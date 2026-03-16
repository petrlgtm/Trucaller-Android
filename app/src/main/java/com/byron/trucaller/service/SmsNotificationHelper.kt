package com.byron.trucaller.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.byron.trucaller.R

/**
 * Helper to display notifications for spam/suspected-spam SMS messages.
 */
object SmsNotificationHelper {

    private const val CHANNEL_ID_SPAM = "sms_spam_channel"
    private const val CHANNEL_NAME = "SMS Spam Alerts"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID_SPAM) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID_SPAM,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for incoming spam SMS messages"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun showSpamSmsNotification(
        context: Context,
        sender: String,
        senderNumber: String,
        messagePreview: String,
        spamScore: Int
    ) {
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SPAM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Spam SMS Blocked")
            .setContentText("From $sender ($senderNumber) - Spam Score: $spamScore%")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$messagePreview\n\nThis sender has a spam score of $spamScore%. The message has been moved to your spam folder.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(senderNumber.hashCode(), notification)
    }

    fun showSuspectedSpamNotification(
        context: Context,
        sender: String,
        senderNumber: String,
        messagePreview: String
    ) {
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SPAM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Suspected Spam SMS")
            .setContentText("From $sender ($senderNumber)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$messagePreview\n\nThis sender is suspected of sending spam. Tap to review.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(senderNumber.hashCode(), notification)
    }
}
