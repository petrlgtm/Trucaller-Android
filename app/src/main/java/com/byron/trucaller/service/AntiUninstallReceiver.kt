package com.byron.trucaller.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.AlarmLog
import com.byron.trucaller.data.model.AlarmResult
import com.byron.trucaller.data.model.AlarmType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AntiUninstallReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "TruCaller device protection enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "TruCaller device protection disabled", Toast.LENGTH_SHORT).show()
        // Log the disable event
        logProtectionEvent(context, "Device admin protection was DISABLED")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Log the attempt
        logProtectionEvent(context, "Unauthorized attempt to disable device admin protection")

        return "WARNING: Device protection can only be disabled from within TruCaller app " +
                "using your account password. Disabling from here is not recommended and " +
                "will leave your device unprotected against theft. " +
                "Please open TruCaller \u2192 Profile \u2192 Device Protection to manage this setting securely."
    }

    private fun logProtectionEvent(context: Context, notes: String) {
        try {
            val app = context.applicationContext as? TruCallerApplication ?: return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                    val userId = app.container.userPreferences.loggedInUserId.first()
                    val device = if (userId != null) app.container.deviceRepository.getFirstDeviceByUser(userId) else null

                    val log = AlarmLog(
                        id = "alm-protect-${System.currentTimeMillis()}",
                        deviceId = device?.id ?: "unknown",
                        triggeredBy = "system",
                        triggeredByName = "Device Admin",
                        triggeredByRole = "system",
                        triggeredAt = now,
                        type = AlarmType.LOCK_DEVICE,
                        result = AlarmResult.FAILED,
                        notes = notes
                    )
                    app.container.alarmRepository.insertLog(log)
                } catch (e: Exception) {
                    Log.e("AntiUninstall", "Failed to log protection event", e)
                }
            }
        } catch (e: Exception) {
            Log.e("AntiUninstall", "Failed to access app container", e)
        }
    }
}
