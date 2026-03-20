package com.byron.trucaller.service

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.util.DeviceAdminHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TruCallerMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        val app = application as? TruCallerApplication ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = app.container.userPreferences.loggedInUserId.first()
                if (userId != null) {
                    val device = app.container.deviceRepository.getFirstDeviceByUser(userId)
                    device?.let {
                        // Update Room locally
                        app.container.deviceRepository.updateFcmToken(it.id, token)

                        // Sync to backend
                        val result = ApiClient.updateFcmToken(it.deviceId, token)
                        if (result.success) {
                            app.container.userPreferences.setFcmTokenNeedsSync(false)
                            Log.d(TAG, "FCM token synced to backend")
                        } else {
                            app.container.userPreferences.setFcmTokenNeedsSync(true)
                            Log.w(TAG, "FCM token backend sync failed, flagged for retry: ${result.error}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token", e)
                try {
                    app.container.userPreferences.setFcmTokenNeedsSync(true)
                } catch (_: Exception) { }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val action = data["action"] ?: return
        val logId = data["logId"]

        Log.d(TAG, "Received FCM action: $action, logId: $logId")

        when (action) {
            "REMOTE_ALARM" -> {
                var success = false
                try {
                    AlarmSoundManager.triggerAlarm(applicationContext)
                    SecurityNotificationHelper.showAlarmNotification(applicationContext)
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute REMOTE_ALARM", e)
                }
                reportActionResult(logId, success, action)
            }
            "LOCK_DEVICE" -> {
                var success = false
                try {
                    if (DeviceAdminHelper.isAdminActive(applicationContext)) {
                        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        dpm.lockNow()
                        success = true
                    }
                    SecurityNotificationHelper.showLockNotification(applicationContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute LOCK_DEVICE", e)
                }
                reportActionResult(logId, success, action)
            }
            "LOCATION_REQUEST" -> {
                SecurityNotificationHelper.showLocationRequestNotification(applicationContext)
                val app = application as? TruCallerApplication ?: return
                CoroutineScope(Dispatchers.IO).launch {
                    var success = false
                    try {
                        val userId = app.container.userPreferences.loggedInUserId.first()
                        if (userId != null) {
                            val regService = DeviceRegistrationService(applicationContext, app.container.deviceRepository)
                            regService.registerOrUpdateDevice(userId)
                            success = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update location on request", e)
                    }
                    reportActionResult(logId, success, action)
                }
            }
        }
    }

    private fun reportActionResult(logId: String?, success: Boolean, action: String) {
        if (logId == null) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = if (success) "SUCCESS" else "FAILED"
                val notes = "Device executed $action"
                ApiClient.updateAlarmLogResult(logId, result, notes)
                Log.d(TAG, "Reported $result for logId=$logId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to report action result for logId=$logId", e)
            }
        }
    }

    companion object {
        private const val TAG = "TruCallerFCM"
    }
}
