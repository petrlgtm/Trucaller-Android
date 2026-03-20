package com.byron.trucaller.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SmsCategory {
    PERSONAL, TRANSACTIONAL, PROMOTIONAL, SPAM
}

enum class SmsType {
    INBOX, SENT, DRAFT
}

/**
 * Represents an SMS message read from the device's SMS content provider.
 * Not stored in Room — used as an in-memory data class.
 */
@Immutable
data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: SmsType,
    val read: Boolean,
    val contactName: String? = null,
    val spamScore: Int = 0,
    val category: SmsCategory = SmsCategory.PERSONAL
)

/**
 * Groups SMS messages into conversations by phone number.
 */
@Immutable
data class SmsConversation(
    val address: String,
    val contactName: String? = null,
    val lastMessage: String,
    val lastDate: Long,
    val messageCount: Int,
    val unreadCount: Int,
    val spamScore: Int = 0,
    val category: SmsCategory = SmsCategory.PERSONAL,
    val isBlocked: Boolean = false
)

/**
 * Room entity for tracking SMS spam reports submitted by the user.
 */
@Entity(tableName = "sms_spam_reports")
data class SmsSpamReport(
    @PrimaryKey val id: String,
    val userId: String,
    val senderNumber: String,
    val messageBody: String,
    val reason: String = "",
    val reportedAt: String,
    val syncedToServer: Boolean = false
)
