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

        Log.d(TAG, "Executing remote command: $action (logId=$logId)")

        when (action) {
            "REMOTE_ALARM" -> {
                var success = false
                try {
                    AlarmSoundManager.triggerAlarm(context.applicationContext)
                    SecurityNotificationHelper.showAlarmNotification(context.applicationContext)
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute REMOTE_ALARM", e)
                }
                reportResult(logId, success, action)
            }

            "STOP_ALARM" -> {
                AlarmSoundManager.stopAlarm()
                SecurityNotificationHelper.dismissAlarmNotification(context.applicationContext)
                reportResult(logId, true, action)
            }

            "LOCK_DEVICE" -> {
                var success = false
                try {
                    if (DeviceAdminHelper.isAdminActive(context.applicationContext)) {
                        val dpm = context.applicationContext
                            .getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        dpm.lockNow()
                        success = true
                        SecurityNotificationHelper.showLockNotification(context.applicationContext)
                    }
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
                SecurityNotificationHelper.showLocationRequestNotification(context.applicationContext)
                var success = false
                try {
                    val app = context.applicationContext as? TruCallerApplication
                    val userId = app?.container?.userPreferences?.loggedInUserId?.first()
                    if (app != null && userId != null) {
                        val regService = DeviceRegistrationService(
                            context.applicationContext,
                            app.container.deviceRepository
                        )
                        regService.registerOrUpdateDevice(userId)
                        success = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update location on request", e)
                }
                reportResult(logId, success, action)
            }

            else -> {
                Log.w(TAG, "Unknown remote command: $action")
                return false
            }
        }
        return true
    }

    private suspend fun reportResult(logId: String?, success: Boolean, action: String) {
        if (logId == null) return
        try {
            val result = if (success) "SUCCESS" else "FAILED"
            ApiClient.updateAlarmLogResult(logId, result, "Device executed $action")
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
