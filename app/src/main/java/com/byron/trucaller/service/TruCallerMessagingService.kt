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

        Log.d(TAG, "Received FCM action: $action")

        when (action) {
            "REMOTE_ALARM" -> {
                AlarmSoundManager.triggerAlarm(applicationContext)
                SecurityNotificationHelper.showAlarmNotification(applicationContext)
            }
            "LOCK_DEVICE" -> {
                if (DeviceAdminHelper.isAdminActive(applicationContext)) {
                    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    dpm.lockNow()
                }
                SecurityNotificationHelper.showLockNotification(applicationContext)
            }
            "LOCATION_REQUEST" -> {
                SecurityNotificationHelper.showLocationRequestNotification(applicationContext)
                val app = application as? TruCallerApplication ?: return
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val userId = app.container.userPreferences.loggedInUserId.first()
                        if (userId != null) {
                            val regService = DeviceRegistrationService(applicationContext, app.container.deviceRepository)
                            regService.registerOrUpdateDevice(userId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update location on request", e)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "TruCallerFCM"
    }
}
