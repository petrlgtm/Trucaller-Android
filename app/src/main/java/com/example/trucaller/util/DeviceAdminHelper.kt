package com.example.trucaller.util

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.trucaller.service.AntiUninstallReceiver

object DeviceAdminHelper {
    fun getComponentName(context: Context): ComponentName {
        return ComponentName(context, AntiUninstallReceiver::class.java)
    }

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(getComponentName(context))
    }

    fun requestAdminPermission(activity: Activity, requestCode: Int = 1001) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(activity))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "TruCaller needs device admin permission to protect your device from unauthorized uninstallation. This is part of the anti-theft security feature."
            )
        }
        activity.startActivityForResult(intent, requestCode)
    }

    /**
     * Programmatically removes Device Admin. This is the only proper way to disable
     * anti-uninstall protection — requires password verification before calling.
     */
    fun removeAdmin(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.removeActiveAdmin(getComponentName(context))
    }
}
