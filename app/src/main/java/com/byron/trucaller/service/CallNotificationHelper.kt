package com.byron.trucaller.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.byron.trucaller.MainActivity
import com.byron.trucaller.R
import com.byron.trucaller.data.model.SpamCategory

/**
 * Helper for call-related notifications: caller ID results when overlay
 * permission is denied and blocked-call feedback from call screening.
 *
 * Channel creation is handled centrally by [NotificationChannelManager] at
 * application startup — this helper only builds and posts notifications.
 */
object CallNotificationHelper {

    private const val GROUP_BLOCKED_CALLS = "com.byron.trucaller.BLOCKED_CALLS"
    private const val SUMMARY_NOTIFICATION_ID = 900_000
    private const val CALLER_ID_BASE_ID = 800_000

    // ── Caller ID notification (overlay permission denied fallback) ──────

    /**
     * Shows a caller ID notification when overlay permission is not granted.
     *
     * Tapping the notification opens the CallerIdScreen with the phone number
     * pre-filled for lookup.
     */
    fun showCallerIdNotification(
        context: Context,
        callerName: String,
        number: String,
        spamScore: Int,
        category: SpamCategory
    ) {
        val categoryLabel = when (category) {
            SpamCategory.SAFE -> "Safe"
            SpamCategory.SUSPECTED_SPAM -> "Suspected Spam"
            SpamCategory.SPAM -> "Spam"
            SpamCategory.FRAUD -> "Fraud"
        }

        val title = callerName.ifBlank { "Unknown Caller" }
        val scoreText = if (spamScore >= 0) " | Score: $spamScore/100" else ""
        val contentText = "$number - $categoryLabel$scoreText"

        val viewDetailsIntent = createCallerIdPendingIntent(context, number, CALLER_ID_BASE_ID + number.hashCode())

        val notification = NotificationCompat.Builder(context, NotificationChannelManager.CALLER_ID_CHANNEL)
            .setSmallIcon(R.drawable.ic_caller_id)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n\nTap to view full caller details.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(viewDetailsIntent)
            .addAction(
                R.drawable.ic_caller_id,
                "View Details",
                viewDetailsIntent
            )
            .build()

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(CALLER_ID_BASE_ID + number.hashCode(), notification)
    }

    // ── Blocked call notification ────────────────────────────────────────

    /**
     * Shows a notification when a call has been blocked by call screening.
     *
     * Individual blocked-call notifications are grouped so that multiple
     * blocked calls collapse into a single summary in the notification shade.
     *
     * Actions:
     * - "View Details" opens CallerIdScreen with the number
     * - "Block" is informational (the call is already blocked; tapping opens
     *   CallerIdScreen where the user can manage the block list)
     */
    fun showBlockedCallNotification(
        context: Context,
        callerName: String?,
        number: String,
        spamScore: Int
    ) {
        val displayName = callerName?.takeIf { it.isNotBlank() } ?: "Unknown"
        val scoreText = if (spamScore >= 0) " - Score: $spamScore" else ""
        val title = "Blocked call from $displayName"
        val contentText = "$number$scoreText"

        val notificationId = number.hashCode()

        val viewDetailsIntent = createCallerIdPendingIntent(context, number, notificationId)

        // Individual blocked call notification
        val notification = NotificationCompat.Builder(context, NotificationChannelManager.CALL_SCREENING_CHANNEL)
            .setSmallIcon(R.drawable.ic_call_blocked)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Blocked call from $displayName\n$number$scoreText\n\nThis call was automatically screened and rejected.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(viewDetailsIntent)
            .setGroup(GROUP_BLOCKED_CALLS)
            .addAction(
                R.drawable.ic_caller_id,
                "View Details",
                viewDetailsIntent
            )
            .build()

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(notificationId, notification)

        // Post / update the summary notification for the group.
        // On API 24+ the system auto-bundles, but an explicit summary gives
        // us control over the collapsed text.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            postGroupSummary(context, manager)
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Creates a [PendingIntent] that opens [MainActivity] and deep-links to
     * the `caller_id_lookup/{number}` route.
     */
    private fun createCallerIdPendingIntent(
        context: Context,
        number: String,
        requestCode: Int
    ): PendingIntent {
        val encodedNumber = Uri.encode(number)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("trucaller://caller_id_lookup/$encodedNumber")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    /**
     * Posts (or updates) the group summary notification using [InboxStyle]
     * so that multiple blocked calls are shown in a compact list.
     */
    private fun postGroupSummary(context: Context, manager: NotificationManager) {
        val summaryNotification = NotificationCompat.Builder(context, NotificationChannelManager.CALL_SCREENING_CHANNEL)
            .setSmallIcon(R.drawable.ic_call_blocked)
            .setContentTitle("Blocked calls")
            .setContentText("Multiple calls were blocked")
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText("Blocked calls")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setGroup(GROUP_BLOCKED_CALLS)
            .setGroupSummary(true)
            .build()

        manager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
    }
}
