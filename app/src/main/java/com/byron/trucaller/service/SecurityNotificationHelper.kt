package com.byron.trucaller.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.byron.trucaller.R

/**
 * Helper to display notifications for security events (alarm, lock, location,
 * stolen-device) and silent sync / backup status updates.
 *
 * Channel creation is handled centrally by [NotificationChannelManager] at
 * application startup — this helper only builds and posts notifications.
 */
object SecurityNotificationHelper {

    // ── Notification IDs (unique per type) ───────────────────────────────
    private const val NOTIFICATION_ID_ALARM = 9001
    private const val NOTIFICATION_ID_LOCK = 9002
    private const val NOTIFICATION_ID_LOCATION = 9003
    private const val NOTIFICATION_ID_STOLEN = 9004
    private const val NOTIFICATION_ID_SYNC = 9005
    private const val NOTIFICATION_ID_BACKUP = 9006
    private const val NOTIFICATION_ID_FAMILY_ALERT = 9007

    /**
     * Shows a persistent, non-dismissible notification while a remote alarm is
     * sounding.
     *
     * Deliberately has **no "Stop" action and no tap intent**: an anti-theft
     * alarm must not be silenceable by whoever is holding the phone. It can only
     * be stopped by a remote STOP_ALARM command from the owner/admin (handled in
     * [RemoteCommandExecutor]), which calls [dismissAlarmNotification]. The
     * notification is [setOngoing] so it also cannot be swiped away.
     */
    fun showAlarmNotification(context: Context) {
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannelManager.SECURITY_ALERTS_CHANNEL
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Alarm Triggered")
            .setContentText("This alarm can only be stopped remotely by the device owner.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)        // persistent — cannot be swiped away
            .setAutoCancel(false)    // tapping it must not dismiss it
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_ALARM, notification)
    }

    /**
     * Dismisses the alarm notification (e.g. after the user taps "Stop").
     */
    fun dismissAlarmNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID_ALARM)
    }

    /**
     * Shows a high-priority notification indicating the device was locked
     * remotely.
     */
    fun showLockNotification(context: Context) {
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannelManager.SECURITY_ALERTS_CHANNEL
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Device Locked")
            .setContentText("Device locked remotely")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_LOCK, notification)
    }

    /**
     * Shows a high-priority notification indicating a location request was
     * received and is being processed.
     */
    fun showLocationRequestNotification(context: Context) {
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannelManager.SECURITY_ALERTS_CHANNEL
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Location Request")
            .setContentText("Location request received")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_LOCATION, notification)
    }

    /**
     * Shows a high-priority notification when another device reports this
     * device (or an associated device) as stolen.
     */
    fun showStolenDeviceNotification(context: Context, deviceName: String) {
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannelManager.SECURITY_ALERTS_CHANNEL
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Stolen Device Alert")
            .setContentText("$deviceName has been reported stolen")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "$deviceName has been reported stolen. " +
                                "Security measures have been activated. " +
                                "Open the app for more details."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_STOLEN, notification)
    }

    /**
     * Shows a silent (low-priority, no sound/vibration) notification after
     * contact sync completes.
     */
    fun showSyncNotification(context: Context, count: Int) {
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannelManager.SYNC_CHANNEL
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Contacts Synced")
            .setContentText("$count contacts synced")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_SYNC, notification)
    }

    /**
     * Shows a high-priority notification for family group alerts (e.g. stolen
     * device, member joined/left, location update).
     */
    fun showFamilyAlertNotification(context: Context, title: String, message: String) {
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannelManager.SECURITY_ALERTS_CHANNEL
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_FAMILY_ALERT, notification)
    }

    /**
     * Shows a silent (low-priority, no sound/vibration) notification after
     * a Google Drive backup completes.
     */
    fun showBackupNotification(context: Context) {
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannelManager.SYNC_CHANNEL
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Backup Complete")
            .setContentText("Backup complete")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_BACKUP, notification)
    }
}
