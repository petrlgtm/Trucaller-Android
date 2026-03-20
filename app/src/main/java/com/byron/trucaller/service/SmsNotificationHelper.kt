package com.byron.trucaller.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.byron.trucaller.R

/**
 * Helper to display notifications for spam/suspected-spam SMS messages.
 *
 * Channel creation is handled centrally by [NotificationChannelManager] at
 * application startup — this helper only builds and posts notifications.
 */
object SmsNotificationHelper {

    fun showSpamSmsNotification(
        context: Context,
        sender: String,
        senderNumber: String,
        messagePreview: String,
        spamScore: Int
    ) {
        val notification = NotificationCompat.Builder(context, NotificationChannelManager.SMS_SPAM_CHANNEL)
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
        val notification = NotificationCompat.Builder(context, NotificationChannelManager.SMS_SPAM_CHANNEL)
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
