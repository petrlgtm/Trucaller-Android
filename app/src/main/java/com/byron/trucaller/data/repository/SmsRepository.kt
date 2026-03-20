package com.byron.trucaller.data.repository

import android.content.ContentResolver
import com.byron.trucaller.data.dao.SmsSpamDao
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsConversation
import com.byron.trucaller.data.model.SmsMessage
import com.byron.trucaller.data.model.SmsSpamReport
import com.byron.trucaller.data.model.SmsType
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.util.SmsReader
import android.util.Log
import kotlinx.coroutines.flow.Flow

class SmsRepository(
    private val smsSpamDao: SmsSpamDao,
    private val callerIdRepository: CallerIdRepository,
    private val blockedNumberRepository: BlockedNumberRepository
) {
    companion object {
        private const val TAG = "SmsRepository"
    }
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

    /**
     * Upload all unsynced SMS spam reports to the backend.
     * Each report is uploaded individually; on success it is marked as synced.
     * Failures are silently logged so the sync can be retried later.
     */
    suspend fun syncUnsyncedReports() {
        val unsynced = smsSpamDao.getUnsynced()
        if (unsynced.isEmpty()) return

        for (report in unsynced) {
            try {
                val result = ApiClient.reportSmsSpam(
                    senderNumber = report.senderNumber,
                    messageBody = report.messageBody,
                    reason = report.reason
                )
                if (result.success) {
                    smsSpamDao.markSynced(report.id)
                } else {
                    Log.w(TAG, "Backend rejected SMS spam report ${report.id}: ${result.error}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync SMS spam report ${report.id}", e)
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────

    private suspend fun enrichMessages(
        messages: List<SmsMessage>,
        userId: String
    ): List<SmsMessage> {
        // Cache sender lookups to avoid repeated DB queries
        val senderCache = mutableMapOf<String, Pair<String?, SmsCategory>>()

        return messages.map { msg ->
            val senderResult = senderCache.getOrPut(msg.address) {
                lookupSender(msg.address, userId)
            }
            // Refine category using message body content analysis
            val finalCategory = classifyMessage(msg.address, msg.body, senderResult.second)
            val spamScore = when (finalCategory) {
                SmsCategory.SPAM -> 80
                SmsCategory.PROMOTIONAL -> 40
                SmsCategory.TRANSACTIONAL -> 0
                SmsCategory.PERSONAL -> 0
            }
            msg.copy(
                contactName = senderResult.first,
                category = finalCategory,
                spamScore = spamScore
            )
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

        // Check if user reported this number as spam
        if (smsSpamDao.isReported(userId, address)) {
            return Pair(null, SmsCategory.SPAM)
        }

        // Check caller ID database
        val lookup = callerIdRepository.lookupNumber(address)
        val entry = lookup.callerIdEntry

        if (entry != null) {
            val category = when (entry.category) {
                SpamCategory.SPAM, SpamCategory.FRAUD -> SmsCategory.SPAM
                SpamCategory.SUSPECTED_SPAM -> SmsCategory.PROMOTIONAL
                SpamCategory.SAFE -> SmsCategory.PERSONAL // will be refined by message body
            }
            return Pair(entry.name, category)
        }

        // Non-numeric / shortcode sender → likely business
        if (isShortCodeOrAlpha(address)) {
            return Pair(null, SmsCategory.TRANSACTIONAL)
        }

        return Pair(null, SmsCategory.PERSONAL)
    }

    // ── Message body classifier ──────────────────────────────────────

    /**
     * Classifies a message using sender analysis + body content scoring.
     * Called after lookupSender to refine the category when it's PERSONAL.
     */
    private fun classifyMessage(address: String, body: String, senderCategory: SmsCategory): SmsCategory {
        // If already SPAM from blocked/reported, keep it
        if (senderCategory == SmsCategory.SPAM) return SmsCategory.SPAM

        val isBusinessSender = isShortCodeOrAlpha(address)

        // Short messages (< 10 chars) from shortcode/alpha senders are likely OTP codes
        if (isBusinessSender && body.trim().length < 10) {
            return SmsCategory.TRANSACTIONAL
        }

        val lowerBody = body.lowercase()

        // Score each category based on body keywords
        val spamScore = scoreSpam(lowerBody)
        val promoScore = scorePromotional(lowerBody)
        val transactionalScore = scoreTransactional(lowerBody)

        // Strong spam signals override everything
        if (spamScore >= 3) return SmsCategory.SPAM

        // Shortcode / alphanumeric senders are never personal
        if (isBusinessSender) {
            return when {
                promoScore > transactionalScore -> SmsCategory.PROMOTIONAL
                transactionalScore > 0 -> SmsCategory.TRANSACTIONAL
                spamScore > 0 -> SmsCategory.PROMOTIONAL
                else -> SmsCategory.TRANSACTIONAL
            }
        }

        // Regular phone number senders
        return when {
            spamScore >= 2 -> SmsCategory.SPAM
            promoScore >= 3 -> SmsCategory.PROMOTIONAL
            transactionalScore >= 2 -> SmsCategory.TRANSACTIONAL
            promoScore >= 2 && senderCategory != SmsCategory.PERSONAL -> SmsCategory.PROMOTIONAL
            else -> senderCategory
        }
    }

    private fun scoreSpam(body: String): Int {
        var score = 0
        val spamPatterns = listOf(
            // Prize / lottery scams
            "you have won", "you've won", "congratulations you", "winner",
            "lottery", "claim your prize", "selected as winner",
            "congratulations you have won",
            // Money scams
            "free money", "earn money fast", "make money online",
            "double your money", "investment opportunity",
            // Urgency / phishing
            "act now", "click here immediately", "your account will be",
            "suspended", "verify immediately or",
            "urgent action required", "confirm your identity",
            "click here to claim",
            // Nigerian/advance fee patterns
            "beneficiary", "next of kin", "inheritance",
            "million dollars", "million usd", "diplomat",
            // Mobile money scams (Uganda-specific)
            "send airtime", "wrong transfer", "mistakenly sent",
            "reverse the money", "mobile money pin",
            "confirm withdrawal",
            // Uganda telecom scam patterns
            "free data", "win airtime", "free mb", "free mbs",
            "you have been selected to receive",
            "claim your free airtime", "claim free data",
            "your number has been selected", "dial to claim",
            "send to this number to receive"
        )
        for (p in spamPatterns) {
            if (body.contains(p)) score++
        }
        // Suspicious URL patterns
        if (body.contains(Regex("https?://bit\\.ly|https?://t\\.co|https?://tinyurl|https?://[a-z]{2,5}\\.[a-z]{2}/[a-z0-9]"))) score += 2
        // Excessive caps
        val capsRatio = body.count { it.isUpperCase() }.toFloat() / body.length.coerceAtLeast(1)
        if (capsRatio > 0.6f && body.length > 20) score++
        // Multiple exclamation/question marks
        if (body.contains(Regex("[!]{3,}|[?]{3,}"))) score++
        return score
    }

    private fun scorePromotional(body: String): Int {
        var score = 0
        val promoPatterns = listOf(
            // Offers & deals
            "% off", "percent off", "discount", "sale", "flash sale",
            "limited time", "limited offer", "exclusive offer",
            "special offer", "best deal", "mega deal",
            // Marketing
            "subscribe", "unsubscribe", "opt out", "reply stop",
            "promo code", "coupon", "voucher", "cashback", "cash back",
            "redeem", "reward points", "loyalty",
            // Call to action
            "shop now", "buy now", "order now", "download now",
            "get yours", "don't miss", "hurry",
            // Telco promotions (Uganda-specific)
            "data bundle", "bonus data", "free airtime", "voice bundle",
            "mtn pulse", "airtel money", "daily bundle", "weekly bundle",
            "dial *", "activate now", "unlimited calls"
        )
        for (p in promoPatterns) {
            if (body.contains(p)) score++
        }
        return score
    }

    private fun scoreTransactional(body: String): Int {
        var score = 0
        val transactionalPatterns = listOf(
            // Authentication
            "otp", "verification code", "verify your", "one-time password",
            "one-time pin", "security code", "login code", "2fa",
            // Financial
            "transaction", "payment received", "payment of", "balance is",
            "account balance", "credited", "debited", "receipt",
            "statement", "invoice",
            // Delivery / order
            "delivered", "shipped", "out for delivery", "tracking",
            "order confirmed", "order #", "booking confirmed",
            // Mobile money (Uganda-specific)
            "you have received", "sent to", "mobile money",
            "transaction id", "new balance", "withdraw",
            "float balance", "agent", "approved",
            // M-PESA / Airtel Money / MTN MoMo specific transaction patterns
            "m-pesa", "mpesa", "mtn momo", "mtn mobile money",
            "airtel money", "received ugx", "sent ugx",
            "balance ugx", "ugx", "withdraw from", "deposit to",
            "cash in", "cash out", "transfer to", "transfer from",
            // Uganda banks
            "stanbic", "centenary", "dfcu", "absa", "bank of africa",
            "postbank", "equity bank", "housing finance",
            "standard chartered", "barclays", "orient bank",
            // Uganda utilities & government
            "nwsc", "umeme", "kcca", "ura",
            "national water", "electricity", "yaka",
            "tax payment", "prn", "payment registration number",
            // Alerts
            "alert:", "reminder:", "appointment", "scheduled",
            "password changed", "login from", "new device"
        )
        for (p in transactionalPatterns) {
            if (body.contains(p)) score++
        }
        // OTP pattern: 4-8 digit codes
        if (body.contains(Regex("\\b\\d{4,8}\\b")) &&
            body.contains(Regex("code|otp|pin|verify|confirm", RegexOption.IGNORE_CASE))) {
            score += 2
        }
        // Uganda currency amount pattern: UGX followed by amount (e.g., "UGX 50,000" or "UGX50000")
        if (body.contains(Regex("ugx\\s?[\\d,]+"))) {
            score++
        }
        return score
    }

    /**
     * Checks if the sender address is a shortcode or alphanumeric (non-phone-number) sender.
     * These are almost always businesses.
     *
     * Handles various sender formats:
     * - Pure alphanumeric: "MTN", "AIRTEL", "UMEME", "Stanbic"
     * - Hyphenated alpha: "M-PESA", "M-SENTE"
     * - Mixed alpha-numeric: "MTNMoMo", "AirtelMoney", "256INFO"
     * - Short codes: "3030", "8080", "6060"
     */
    private fun isShortCodeOrAlpha(address: String): Boolean {
        val trimmed = address.trim()

        // Known Uganda alphanumeric senders (case-insensitive check)
        val knownAlphaSenders = listOf(
            "m-pesa", "mpesa", "mtn", "mtnmomo", "mtn momo",
            "airtel", "airtelmoney", "airtel money",
            "stanbic", "centenary", "dfcu", "absa", "postbank",
            "bankofafrica", "bank of africa", "equity", "housingfinance",
            "nwsc", "umeme", "kcca", "ura",
            "safaricom", "africell", "smile", "lycamobile"
        )
        if (knownAlphaSenders.any { trimmed.equals(it, ignoreCase = true) }) return true

        // Strip non-alphanumeric characters (except +) to check composition
        val cleaned = trimmed.replace(Regex("[^a-zA-Z0-9+]"), "")
        // Alphanumeric sender: contains any letter (e.g., "MTN", "M-PESA" → "MPESA", "AirtelMoney")
        if (cleaned.any { it.isLetter() }) return true
        // Short code (3-6 digits)
        val digits = cleaned.replace(Regex("[^\\d]"), "")
        if (digits.length in 3..6) return true
        return false
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
