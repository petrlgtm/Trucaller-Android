package com.byron.trucaller.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SecurityNotificationHelper.ACTION_STOP_ALARM) {
            AlarmSoundManager.stopAlarm()
            SecurityNotificationHelper.dismissAlarmNotification(context)
        }
    }
}
