package com.byron.trucaller.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.byron.trucaller.R

/**
 * Hosts the remote anti-theft siren in a foreground service so it is genuinely
 * hard to silence without a remote STOP_ALARM:
 *
 *  - it keeps running when the app is swiped out of Recents (a bare background
 *    MediaPlayer would be reclaimed),
 *  - [START_STICKY] + a persisted "active" flag re-arm the siren if the OS kills
 *    and restarts the service,
 *  - the foreground notification has no stop affordance (see [buildNotification]).
 *
 * The actual audio/vibration is produced by [AlarmSoundManager]; this service
 * just keeps it alive in the foreground. Started from the background FCM handler —
 * allowed because the app holds SYSTEM_ALERT_WINDOW (FGS background-start exemption).
 */
class AlarmForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setActive(this, false)
            AlarmSoundManager.stopAlarm()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // A null intent means the system restarted us after a kill (START_STICKY).
        // Only re-arm if we are still meant to be sounding.
        if (intent == null && !isActive(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        setActive(this, true)
        startInForeground()
        AlarmSoundManager.triggerAlarm(applicationContext) // durationMs = 0 → until stopped
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    /** Non-dismissible, no stop action — silenceable only by a remote STOP_ALARM. */
    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannelManager.SECURITY_ALERTS_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Alarm Triggered")
            .setContentText("This alarm can only be stopped remotely by the device owner.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

    override fun onDestroy() {
        super.onDestroy()
        // If we are being destroyed for good (stopped), make sure audio is released.
        if (!isActive(this)) AlarmSoundManager.stopAlarm()
    }

    companion object {
        private const val TAG = "AlarmFgsService"
        private const val NOTIF_ID = 9001
        const val ACTION_STOP = "com.byron.trucaller.action.STOP_ALARM_FGS"

        private const val PREFS = "alarm_fgs_state"
        private const val KEY_ACTIVE = "active"

        /** Starts the siren in the foreground. Throws if the OS blocks the FGS start. */
        fun start(context: Context) {
            val ctx = context.applicationContext
            ctx.startForegroundService(Intent(ctx, AlarmForegroundService::class.java))
        }

        /** Silences the siren and tears the service down. Safe whether or not it is running. */
        fun stop(context: Context) {
            val ctx = context.applicationContext
            setActive(ctx, false)
            AlarmSoundManager.stopAlarm()
            runCatching {
                ctx.startService(
                    Intent(ctx, AlarmForegroundService::class.java).apply { action = ACTION_STOP }
                )
            }
        }

        private fun prefs(c: Context) =
            c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun setActive(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_ACTIVE, v).apply()
        fun isActive(c: Context): Boolean = prefs(c).getBoolean(KEY_ACTIVE, false)
    }
}
