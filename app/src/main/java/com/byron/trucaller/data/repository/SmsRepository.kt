package com.byron.trucaller.data.repository

import android.content.ContentResolver
import com.byron.trucaller.data.dao.SmsSpamDao
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsConversation
import com.byron.trucaller.data.model.SmsMessage
import com.byron.trucaller.data.model.SmsSpamReport
import com.byron.trucaller.data.model.SmsType
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.util.SmsReader
import kotlinx.coroutines.flow.Flow

class SmsRepository(
    private val smsSpamDao: SmsSpamDao,
    private val callerIdRepository: CallerIdRepository,
    private val blockedNumberRepository: BlockedNumberRepository
) {
    /**
     * Read all SMS messages and enrich with spam data.
     */
    suspend fun getAllMessages(
        contentResolver: ContentResolver,
        userId: String,
        limit: Int = 500
    ): List<SmsMessage> {
        val rawMessages = SmsReader.readAllMessages(contentResolver, limit)
        return enrichMessages(rawMessages, userId)
    }

    /**
     * Read messages for a specific conversation and enrich.
     */
    suspend fun getConversation(
        contentResolver: ContentResolver,
        address: String,
        userId: String,
        limit: Int = 100
    ): List<SmsMessage> {
        val rawMessages = SmsReader.readConversation(contentResolver, address, limit)
        return enrichMessages(rawMessages, userId)
    }

    /**
     * Build conversation list from all messages.
     */
    suspend fun getConversations(
        contentResolver: ContentResolver,
        userId: String,
        limit: Int = 500
    ): List<SmsConversation> {
        val messages = getAllMessages(contentResolver, userId, limit)
        return buildConversations(messages, userId)
    }

    /**
     * Get only spam conversations.
     */
    suspend fun getSpamConversations(
        contentResolver: ContentResolver,
        userId: String
    ): List<SmsConversation> {
        return getConversations(contentResolver, userId).filter {
            it.category == SmsCategory.SPAM || it.category == SmsCategory.PROMOTIONAL
        }
    }

    /**
     * Report an SMS as spam.
     */
    suspend fun reportSpam(report: SmsSpamReport) {
        smsSpamDao.insert(report)
    }

    /**
     * Get spam reports for a user.
     */
    fun getSpamReports(userId: String): Flow<List<SmsSpamReport>> =
        smsSpamDao.getByUserId(userId)

    fun getSpamReportCount(userId: String): Flow<Int> =
        smsSpamDao.getCountByUser(userId)

    suspend fun isReportedAsSpam(userId: String, number: String): Boolean =
        smsSpamDao.isReported(userId, number)

    suspend fun getUnsyncedReports(): List<SmsSpamReport> =
        smsSpamDao.getUnsynced()

    suspend fun markReportSynced(id: String) =
        smsSpamDao.markSynced(id)

    // ── Private helpers ────────────────────────────────────────────────

    private suspend fun enrichMessages(
        messages: List<SmsMessage>,
        userId: String
    ): List<SmsMessage> {
        // Cache lookups to avoid repeated DB queries
        val cache = mutableMapOf<String, Pair<String?, SmsCategory>>()

        return messages.map { msg ->
            val cached = cache[msg.address]
            if (cached != null) {
                msg.copy(
                    contactName = cached.first,
                    category = cached.second,
                    spamScore = if (cached.second == SmsCategory.SPAM) 80 else 0
                )
            } else {
                val result = lookupSender(msg.address, userId)
                cache[msg.address] = result
                msg.copy(
                    contactName = result.first,
                    category = result.second,
                    spamScore = if (result.second == SmsCategory.SPAM) 80 else 0
                )
            }
        }
    }

    private suspend fun lookupSender(
        address: String,
        userId: String
    ): Pair<String?, SmsCategory> {
        // Check if blocked
        if (blockedNumberRepository.isBlocked(userId, address)) {
            return Pair(null, SmsCategory.SPAM)
        }

        // Check caller ID database
        val lookup = callerIdRepository.lookupNumber(address)
        val entry = lookup.callerIdEntry

        if (entry != null) {
            val category = when (entry.category) {
                SpamCategory.SPAM, SpamCategory.FRAUD -> SmsCategory.SPAM
                SpamCategory.SUSPECTED_SPAM -> SmsCategory.PROMOTIONAL
                SpamCategory.SAFE -> categorizeByContent(entry.name)
            }
            return Pair(entry.name, category)
        }

        // No entry found — categorize as personal
        return Pair(null, SmsCategory.PERSONAL)
    }

    /**
     * Simple heuristic: if the name looks like a business/service, categorize as transactional.
     */
    private fun categorizeByContent(name: String): SmsCategory {
        val businessKeywords = listOf(
            "bank", "mtn", "airtel", "hospital", "delivery", "jumia",
            "umeme", "nwsc", "ura", "safeboda", "uber", "bolt"
        )
        return if (businessKeywords.any { name.lowercase().contains(it) }) {
            SmsCategory.TRANSACTIONAL
        } else {
            SmsCategory.PERSONAL
        }
    }

    private suspend fun buildConversations(
        messages: List<SmsMessage>,
        userId: String
    ): List<SmsConversation> {
        val grouped = messages.groupBy { it.address }
        val conversations = grouped.map { (address, msgs) ->
            val latest = msgs.maxByOrNull { it.date } ?: msgs.first()
            val isBlocked = blockedNumberRepository.isBlocked(userId, address)

            SmsConversation(
                address = address,
                contactName = latest.contactName,
                lastMessage = latest.body,
                lastDate = latest.date,
                messageCount = msgs.size,
                unreadCount = msgs.count { !it.read && it.type == SmsType.INBOX },
                spamScore = latest.spamScore,
                category = latest.category,
                isBlocked = isBlocked
            )
        }

        return conversations.sortedByDescending { it.lastDate }
    }
}
