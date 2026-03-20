package com.byron.trucaller.util

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun formatPhoneNumber(phone: String): String {
    if (!phone.startsWith("+256")) return phone
    val digits = phone.substring(4)
    if (digits.length < 9) return phone
    return "+256 ${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6)}"
}

fun maskPhoneNumber(phone: String): String {
    if (!phone.startsWith("+256")) return phone
    val digits = phone.substring(4)
    if (digits.length < 9) return phone
    return "+256 ${digits[0]}XX XXX ${digits.substring(6)}"
}

fun formatRelativeTime(dateStr: String): String {
    return try {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        var date: Date? = null
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                date = sdf.parse(dateStr)
                if (date != null) break
            } catch (_: Exception) {}
        }
        if (date == null) return dateStr

        val now = Date()
        val diffMs = (now.time - date.time).coerceAtLeast(0)
        val diffMins = diffMs / 60000
        val diffHours = diffMs / 3600000
        val diffDays = diffMs / 86400000

        when {
            diffMins < 1 -> "Just now"
            diffMins < 60 -> "${diffMins}m ago"
            diffHours < 24 -> "${diffHours}h ago"
            diffDays < 7 -> "${diffDays}d ago"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
        }
    } catch (_: Exception) {
        dateStr
    }
}

fun getInitials(name: String): String {
    return name.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .take(2)
        .joinToString("")
}

fun getSpamScoreColor(score: Int): Color {
    return when {
        score <= 10 -> Color(0xFF43A047)
        score <= 30 -> Color(0xFF7CB342)
        score <= 50 -> Color(0xFFFB8C00)
        score <= 70 -> Color(0xFFF4511E)
        else -> Color(0xFFE53935)
    }
}
