package com.byron.trucaller.service

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.util.DeviceAdminHelper
import kotlinx.coroutines.flow.first

/**
 * Executes remote security commands (alarm, lock, wipe, locate) on this device.
 *
 * Commands arrive over two transports — FCM data messages and the
 * `/api/alarms/pending/{deviceId}` polling fallback — so execution is
 * deduplicated by logId: a command that already ran is never run twice,
 * even if both transports deliver it.
 */
object RemoteCommandExecutor {

    private const val TAG = "RemoteCommandExecutor"
    private const val DEDUP_PREFS = "remote_command_dedup"
    private const val DEDUP_KEY = "executed_log_ids"
    private const val DEDUP_MAX = 100

    /**
     * Runs [action] and reports the result to the backend against [logId].
     * Returns false without executing when the command was already handled.
     */
    suspend fun execute(context: Context, action: String, logId: String?): Boolean {
        if (logId != null && !markExecuted(context, logId)) {
            Log.d(TAG, "Skipping duplicate command $action (logId=$logId)")
            return false
        }

        // FCM/WorkManager can cold-start this without the UI ever running, leaving
        // ApiClient unauthenticated. Load the persisted token before executing so the
        // result ack below actually authenticates instead of 401-ing and leaving the
        // backend log stuck at PENDING.
        (context.applicationContext as? TruCallerApplication)?.ensureApiAuthLoaded()

        Log.d(TAG, "Executing remote command: $action (logId=$logId)")

        when (action) {
            "REMOTE_ALARM" -> {
                var success = false
                try {
                    // Run the siren in a foreground service so it survives the app
                    // being swiped from Recents and restarts if the OS kills it.
                    AlarmForegroundService.start(context.applicationContext)
                    success = true
                } catch (e: Exception) {
                    Log.w(TAG, "FGS alarm start blocked — falling back to in-process alarm", e)
                    try {
                        AlarmSoundManager.triggerAlarm(context.applicationContext)
                        SecurityNotificationHelper.showAlarmNotification(context.applicationContext)
                        success = true
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to execute REMOTE_ALARM", e2)
                    }
                }
                reportResult(logId, success, action)
            }

            "STOP_ALARM" -> {
                AlarmForegroundService.stop(context.applicationContext)
                SecurityNotificationHelper.dismissAlarmNotification(context.applicationContext)
                reportResult(logId, true, action)
            }

            "LOCK_DEVICE" -> {
                var success = false
                try {
                    // App-level lock: full-screen, unlockable only with the owner's
                    // app password. Works without Device Owner privileges (which the
                    // system keyguard would require) and persists across reboot.
                    LockManager.lock(context.applicationContext)

                    // Also engage the system keyguard when Device Admin is granted,
                    // so the screen is locked the instant the command lands.
                    if (DeviceAdminHelper.isAdminActive(context.applicationContext)) {
                        val dpm = context.applicationContext
                            .getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        dpm.lockNow()
                    }
                    SecurityNotificationHelper.showLockNotification(context.applicationContext)
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute LOCK_DEVICE", e)
                }
                reportResult(logId, success, action)
            }

            "WIPE_DATA" -> {
                try {
                    if (DeviceAdminHelper.isAdminActive(context.applicationContext)) {
                        // Report before wipe — device resets immediately after wipeData()
                        reportResult(logId, true, action)
                        val dpm = context.applicationContext
                            .getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        @Suppress("DEPRECATION")
                        dpm.wipeData(0)
                        return true
                    }
                    reportResult(logId, false, action)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute WIPE_DATA", e)
                    reportResult(logId, false, action)
                }
            }

            "LOCATION_REQUEST" -> {
                // Anti-theft: do NOT notify the person holding the phone. The
                // location is captured silently and reported to the admins/owner
                // via the device record and the alarm-log result note below.
                var success = false
                var locationNote = "Location reported to admin"
                try {
                    val app = context.applicationContext as? TruCallerApplication
                    val userId = app?.container?.userPreferences?.loggedInUserId?.first()
                    if (app != null && userId != null) {
                        // Pushes GPS lat/long to the backend device record (admins
                        // see it in device detail / forensics).
                        val regService = DeviceRegistrationService(
                            context.applicationContext,
                            app.container.deviceRepository
                        )
                        regService.registerOrUpdateDevice(userId)

                        // Also capture the coordinates for the alarm-log note so the
                        // admin sees the exact location alongside the command result.
                        val loc = LocationService(context.applicationContext).getCurrentLocation()
                        locationNote = if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                            "Location: ${loc.latitude},${loc.longitude}" +
                                (if (loc.city != "Unknown") " (${loc.city}, ${loc.country})" else "") +
                                " ±${loc.accuracy.toInt()}m"
                        } else {
                            "Location unavailable (GPS off / no permission)"
                        }
                        success = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update location on request", e)
                    locationNote = "Location capture failed: ${e.message}"
                }
                reportResult(logId, success, action, notesOverride = locationNote)
            }

            else -> {
                Log.w(TAG, "Unknown remote command: $action")
                return false
            }
        }
        return true
    }

    private suspend fun reportResult(logId: String?, success: Boolean, action: String, notesOverride: String? = null) {
        if (logId == null) return
        try {
            val result = if (success) "SUCCESS" else "FAILED"
            ApiClient.updateAlarmLogResult(logId, result, notesOverride ?: "Device executed $action")
            Log.d(TAG, "Reported $result for logId=$logId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report result for logId=$logId", e)
        }
    }

    /**
     * Records [logId] as executed. Returns false when it was already recorded
     * (i.e. the command must be skipped).
     */
    @Synchronized
    private fun markExecuted(context: Context, logId: String): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(DEDUP_PREFS, Context.MODE_PRIVATE)
        // Stored as "id1,id2,..." in insertion order, newest last
        val existing = prefs.getString(DEDUP_KEY, "") ?: ""
        val ids = existing.split(',').filter { it.isNotBlank() }.toMutableList()
        if (logId in ids) return false
        ids.add(logId)
        while (ids.size > DEDUP_MAX) ids.removeAt(0)
        prefs.edit().putString(DEDUP_KEY, ids.joinToString(",")).apply()
        return true
    }
}
