package com.byron.trucaller.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.byron.trucaller.R
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.data.model.GeofenceEvent
import com.byron.trucaller.data.model.GeofenceTransitionType
import com.byron.trucaller.data.repository.DeviceRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Receives geofence transition broadcasts from the system.
 *
 * On every transition the receiver:
 * 1. Persists a [GeofenceEvent] to the Room database.
 * 2. Attempts to sync the event to the backend via [ApiClient.recordGeofenceEvent].
 * 3. Shows a notification to the user.
 * 4. If the transition is ENTER **and** the device status is STOLEN,
 *    triggers a remote alarm via [AlarmSoundManager].
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastRx"
        private const val NOTIFICATION_ID_GEOFENCE = 9100
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }
        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Geofence error: $errorMessage")
            return
        }

        val transitionType = when (geofencingEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceTransitionType.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> GeofenceTransitionType.EXIT
            Geofence.GEOFENCE_TRANSITION_DWELL -> GeofenceTransitionType.DWELL
            else -> {
                Log.w(TAG, "Unknown transition type: ${geofencingEvent.geofenceTransition}")
                return
            }
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
        val triggeringLocation = geofencingEvent.triggeringLocation
        val latitude = triggeringLocation?.latitude ?: 0.0
        val longitude = triggeringLocation?.longitude ?: 0.0
        val timestamp = Instant.now().toString()

        val app = context.applicationContext as? TruCallerApplication ?: return
        val geofenceRepository = app.container.geofenceRepository
        val deviceRepository = app.container.deviceRepository
        val userPreferences = app.container.userPreferences

        // Use goAsync() so the system does not kill us during the coroutine
        val pendingResult = goAsync()

        scope.launch {
            try {
                // Resolve the device ID from the logged-in user
                val userId = userPreferences.loggedInUserId.firstOrNull() ?: ""
                val device = if (userId.isNotEmpty()) {
                    deviceRepository.getActiveDeviceByUser(userId)
                } else null
                val deviceId = device?.id ?: ""

                for (geofence in triggeringGeofences) {
                    val geofenceId = geofence.requestId

                    // 1. Save event to Room
                    val event = GeofenceEvent(
                        id = UUID.randomUUID().toString(),
                        geofenceId = geofenceId,
                        deviceId = deviceId,
                        transitionType = transitionType,
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = timestamp,
                        synced = false
                    )
                    geofenceRepository.insertEvent(event)
                    geofenceRepository.incrementTriggeredCount(geofenceId)

                    Log.d(TAG, "Geofence transition: $transitionType for $geofenceId")

                    // 2. Sync to backend
                    syncEventToBackend(event, geofenceRepository)

                    // 3. Show notification
                    showGeofenceNotification(context, transitionType, geofenceId)

                    // 4. If ENTER and device is STOLEN, trigger alarm
                    if (transitionType == GeofenceTransitionType.ENTER) {
                        checkStolenAndTriggerAlarm(context, device)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing geofence transition", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    // ── Sync event to backend ────────────────────────────────────────────

    private suspend fun syncEventToBackend(
        event: GeofenceEvent,
        geofenceRepository: com.byron.trucaller.data.repository.GeofenceRepository
    ) {
        try {
            val result = ApiClient.recordGeofenceEvent(
                geofenceId = event.geofenceId,
                deviceId = event.deviceId,
                transitionType = event.transitionType.name,
                latitude = event.latitude,
                longitude = event.longitude
            )
            if (result.success) {
                geofenceRepository.markEventSynced(event.id)
                Log.d(TAG, "Geofence event synced to backend: ${event.id}")
            } else {
                Log.w(TAG, "Backend sync failed: ${result.error}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync geofence event — will retry later", e)
        }
    }

    // ── Check stolen status and trigger alarm ────────────────────────────

    private fun checkStolenAndTriggerAlarm(
        context: Context,
        device: com.byron.trucaller.data.model.Device?
    ) {
        if (device != null && device.status == DeviceStatus.STOLEN) {
            Log.w(TAG, "Device is STOLEN — triggering remote alarm on geofence ENTER")
            AlarmSoundManager.triggerAlarm(context, durationMs = 60_000)
            SecurityNotificationHelper.showAlarmNotification(context)
        }
    }

    // ── Notification ─────────────────────────────────────────────────────

    private fun showGeofenceNotification(
        context: Context,
        transitionType: GeofenceTransitionType,
        geofenceId: String
    ) {
        val transitionLabel = when (transitionType) {
            GeofenceTransitionType.ENTER -> "Entered"
            GeofenceTransitionType.EXIT -> "Exited"
            GeofenceTransitionType.DWELL -> "Dwelling in"
        }

        val contentIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.let { launchIntent ->
                PendingIntent.getActivity(
                    context, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        val notification = NotificationCompat.Builder(
            context,
            NotificationChannelManager.SECURITY_ALERTS_CHANNEL
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Geofence Alert")
            .setContentText("$transitionLabel geofence zone")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "$transitionLabel geofence zone ($geofenceId). " +
                                "Tap to open the app for details."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        // Use geofenceId hashCode to allow multiple simultaneous notifications
        manager.notify(
            NOTIFICATION_ID_GEOFENCE + geofenceId.hashCode().and(0xFF),
            notification
        )
    }
}
