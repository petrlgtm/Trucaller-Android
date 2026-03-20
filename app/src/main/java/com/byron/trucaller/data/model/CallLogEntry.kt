package com.byron.trucaller.data.model

data class CallLogEntry(
    val id: String,
    val phoneNumber: String,
    val name: String?,
    val callType: CallType,
    val duration: Long,  // seconds
    val timestamp: Long, // millis
    val isSpam: Boolean = false,
    val spamScore: Int = 0
)

enum class CallType {
    INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED
}
