package com.byron.trucaller.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class CallLogEntry(
    val id: String,
    val phoneNumber: String,
    val name: String?,
    val callType: CallType,
    val duration: Long,  // seconds
    val timestamp: Long, // millis
    val isSpam: Boolean = false,
    val spamScore: Int = 0,
    val isBlocked: Boolean = false
)

enum class CallType {
    INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED
}
