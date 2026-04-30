package com.byron.trucaller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byron.trucaller.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ── Permission Prompt ──────────────────────────────────────────────────────

/**
 * A full-screen permission prompt with a branded icon, title, description,
 * and a grant-permission button. Used across CallLog, Messages, etc.
 *
 * @param icon The icon to display in the circle.
 * @param title The permission title (e.g., "Call Log Permission Required").
 * @param description Explanation of why the permission is needed.
 * @param buttonLabel Label for the grant button.
 * @param onGrant Callback when the button is pressed.
 */
@Composable
fun PermissionPromptView(
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String = "Grant Permission",
    onGrant: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(40.dp),
                tint = colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            description,
            color = colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(
            onClick = onGrant,
            modifier = Modifier
                .background(colorScheme.primary, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp)
        ) {
            Text(
                buttonLabel,
                color = colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

// ── Date Section Header ────────────────────────────────────────────────────

/**
 * A centered date header with divider lines on either side.
 * Used in CallLog, Messages, and other chronological lists.
 *
 * @param timestamp The timestamp to format into a label.
 */
@Composable
fun SectionDateHeader(timestamp: Long) {
    val colorScheme = MaterialTheme.colorScheme
    val label = formatDateLabel(timestamp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(colorScheme.outlineVariant)
        )
        Text(
            text = label,
            color = colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(colorScheme.outlineVariant)
        )
    }
}

// ── Shared Time/Date Formatters ────────────────────────────────────────────

/**
 * Formats a timestamp into a relative time string:
 * "Now", "5m", "3h", "Yesterday", "Mon", "12/03"
 */
fun formatRelativeTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
        diff < TimeUnit.HOURS.toMillis(24) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
        diff < TimeUnit.DAYS.toMillis(2) -> "Yesterday"
        diff < TimeUnit.DAYS.toMillis(7) -> SimpleDateFormat("EEE", Locale.US).format(Date(timestamp))
        else -> SimpleDateFormat("dd/MM", Locale.US).format(Date(timestamp))
    }
}

/**
 * Formats a timestamp into a date section label:
 * "Today", "Yesterday", or "Monday, Mar 5"
 */
fun formatDateLabel(timestamp: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        isSameCalendarDay(cal, today) -> "Today"
        isSameCalendarDay(cal, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date(timestamp))
    }
}

/**
 * Returns true if two timestamps fall on the same calendar day.
 */
fun isSameTimestampDay(ts1: Long, ts2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
    return isSameCalendarDay(c1, c2)
}

/**
 * Formats a duration in seconds to a human-readable string: "45s", "2m 30s", "1h 15m"
 */
fun formatCallDuration(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

private fun isSameCalendarDay(c1: Calendar, c2: Calendar): Boolean {
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}
