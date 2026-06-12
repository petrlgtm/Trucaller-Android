package com.byron.trucaller.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.byron.trucaller.TruCallerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Listens for [Intent.ACTION_BOOT_COMPLETED] to re-register active geofences
 * with the system, since registered geofences are cleared on device reboot.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedRx"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Re-assert the anti-theft lock if the device was locked before reboot,
        // so a thief can't escape it by power-cycling the phone.
        if (LockManager.isLocked(context)) {
            Log.d(TAG, "Boot completed while locked — re-showing lock screen")
            runCatching { LockManager.showLockScreen(context) }
        }

        Log.d(TAG, "Boot completed — re-registering geofences")

        val app = context.applicationContext as? TruCallerApplication ?: return
        val geofenceRepository = app.container.geofenceRepository
        val deviceRepository = app.container.deviceRepository
        val userPreferences = app.container.userPreferences
        val geofencingService = GeofencingService(context)

        val pendingResult = goAsync()

        scope.launch {
            try {
                val userId = userPreferences.loggedInUserId.firstOrNull()
                if (userId.isNullOrEmpty()) {
                    Log.d(TAG, "No logged-in user — skipping geofence re-registration")
                    return@launch
                }

                val device = deviceRepository.getActiveDeviceByUser(userId)
                if (device == null) {
                    Log.d(TAG, "No active device — skipping geofence re-registration")
                    return@launch
                }

                val activeGeofences = geofenceRepository
                    .getActiveGeofencesByDevice(device.id)
                    .firstOrNull() ?: emptyList()

                if (activeGeofences.isEmpty()) {
                    Log.d(TAG, "No active geofences to re-register")
                    return@launch
                }

                val count = geofencingService.reRegisterGeofences(activeGeofences)
                Log.d(TAG, "Re-registered $count/${activeGeofences.size} geofences after boot")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-register geofences after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
