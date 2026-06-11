package com.byron.trucaller.service

import android.util.Log
import com.byron.trucaller.TruCallerApplication
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
            "REMOTE_ALARM", "STOP_ALARM", "LOCK_DEVICE", "WIPE_DATA", "LOCATION_REQUEST" -> {
                // Shared executor dedups by logId so a command also delivered
                // via the polling fallback never runs twice.
                CoroutineScope(Dispatchers.IO).launch {
                    RemoteCommandExecutor.execute(applicationContext, action, logId)
                }
            }
            "FAMILY_ALERT" -> {
                val alertType = data["alertType"] ?: "DEVICE_UPDATE"
                val groupName = data["groupName"] ?: "Family Group"
                val memberName = data["memberName"] ?: "A family member"
                val deviceName = data["deviceName"]

                Log.d(TAG, "FAMILY_ALERT: type=$alertType, group=$groupName, member=$memberName")

                when (alertType) {
                    "STOLEN_REPORT" -> {
                        val device = deviceName ?: "a device"
                        SecurityNotificationHelper.showFamilyAlertNotification(
                            applicationContext,
                            title = "Stolen Device Alert - $groupName",
                            message = "$memberName reported $device as stolen"
                        )
                    }
                    "DEVICE_LOCATION" -> {
                        SecurityNotificationHelper.showFamilyAlertNotification(
                            applicationContext,
                            title = "Location Update - $groupName",
                            message = "$memberName's device location was updated"
                        )
                    }
                    "MEMBER_JOINED" -> {
                        SecurityNotificationHelper.showFamilyAlertNotification(
                            applicationContext,
                            title = "New Member - $groupName",
                            message = "$memberName joined the group"
                        )
                    }
                    "MEMBER_LEFT" -> {
                        SecurityNotificationHelper.showFamilyAlertNotification(
                            applicationContext,
                            title = "Member Left - $groupName",
                            message = "$memberName left the group"
                        )
                    }
                    else -> {
                        SecurityNotificationHelper.showFamilyAlertNotification(
                            applicationContext,
                            title = "Family Alert - $groupName",
                            message = "Update from $memberName"
                        )
                    }
                }
                reportActionResult(logId, true, action)
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
