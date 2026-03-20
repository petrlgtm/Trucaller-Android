package com.byron.trucaller.service

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * Centralized notification channel manager.
 *
 * Creates and registers all notification channels and channel groups on app
 * startup. Individual services should reference the channel-ID constants
 * exposed here rather than creating channels ad-hoc.
 */
object NotificationChannelManager {

    // ── Channel IDs ─────────────────────────────────────────────────────
    const val CALLER_ID_CHANNEL = "caller_id_channel"
    const val SMS_SPAM_CHANNEL = "sms_spam_channel"
    const val CALL_SCREENING_CHANNEL = "call_screening_channel"
    const val SECURITY_ALERTS_CHANNEL = "security_alerts_channel"
    const val SYNC_CHANNEL = "sync_channel"
    const val GENERAL_CHANNEL = "general_channel"

    // ── Group IDs ───────────────────────────────────────────────────────
    private const val GROUP_PROTECTION = "group_protection"
    private const val GROUP_COMMUNICATION = "group_communication"
    private const val GROUP_SYSTEM = "group_system"

    /**
     * Registers all notification channel groups and channels with the system.
     *
     * Safe to call multiple times — the system ignores channels that already
     * exist and only applies importance / behaviour changes made by the user
     * in Settings.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val soundAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        // ── Groups ──────────────────────────────────────────────────────
        manager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(GROUP_PROTECTION, "Protection"),
                NotificationChannelGroup(GROUP_COMMUNICATION, "Communication"),
                NotificationChannelGroup(GROUP_SYSTEM, "System")
            )
        )

        // ── Channels ────────────────────────────────────────────────────

        // 1. Caller ID alerts — Protection group
        val callerIdChannel = NotificationChannel(
            CALLER_ID_CHANNEL,
            "Caller ID Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Identifies incoming callers and displays caller information"
            group = GROUP_PROTECTION
            setSound(defaultSoundUri, soundAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 100, 250)
        }

        // 2. SMS spam alerts — Protection group (migrated from SmsNotificationHelper)
        val smsSpamChannel = NotificationChannel(
            SMS_SPAM_CHANNEL,
            "SMS Spam Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for incoming spam SMS messages"
            group = GROUP_PROTECTION
            setSound(defaultSoundUri, soundAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300)
        }

        // 3. Call screening — Protection group
        val callScreeningChannel = NotificationChannel(
            CALL_SCREENING_CHANNEL,
            "Blocked Call Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications about calls that were blocked or screened"
            group = GROUP_PROTECTION
            setSound(defaultSoundUri, soundAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 100, 200)
        }

        // 4. Security alerts — Communication group
        val securityAlertsChannel = NotificationChannel(
            SECURITY_ALERTS_CHANNEL,
            "Security Alerts",
            NotificationManager.IMPORTANCE_MAX
        ).apply {
            description = "Stolen device alerts, alarm triggers, and critical security events"
            group = GROUP_COMMUNICATION
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            enableLights(true)
            setBypassDnd(true)
        }

        // 5. Sync status — System group
        val syncChannel = NotificationChannel(
            SYNC_CHANNEL,
            "Sync & Backup",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Contact sync and backup status updates"
            group = GROUP_SYSTEM
            setSound(null, null)
            enableVibration(false)
        }

        // 6. General — System group
        val generalChannel = NotificationChannel(
            GENERAL_CHANNEL,
            "General",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General app notifications"
            group = GROUP_SYSTEM
            setSound(defaultSoundUri, soundAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 150)
        }

        manager.createNotificationChannels(
            listOf(
                callerIdChannel,
                smsSpamChannel,
                callScreeningChannel,
                securityAlertsChannel,
                syncChannel,
                generalChannel
            )
        )
    }
}
