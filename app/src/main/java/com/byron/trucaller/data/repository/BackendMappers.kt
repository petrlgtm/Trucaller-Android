package com.byron.trucaller.data.repository

import com.byron.trucaller.data.model.BlockedNumber
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.data.model.SpamCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Converts backend JSON maps (deserialized by Gson) into Room entities.
 * Handles Gson's Double-for-int quirk via `as? Number` → `.toInt()`.
 */
object BackendMappers {

    private val dateFormat get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    fun mapToContact(map: Map<String, Any>, userId: String): Contact {
        val now = dateFormat.format(Date())
        return Contact(
            id = (map["_id"] as? String) ?: "cnt-${userId}-${System.nanoTime()}",
            userId = userId,
            name = (map["name"] as? String) ?: "",
            phoneNumber = (map["phoneNumber"] as? String) ?: "",
            email = map["email"] as? String,
            syncedAt = (map["syncedAt"] as? String) ?: now,
            isBackedUp = true
        )
    }

    fun mapToBlockedNumber(map: Map<String, Any>): BlockedNumber {
        val now = dateFormat.format(Date())
        return BlockedNumber(
            id = (map["_id"] as? String) ?: "blk-${System.nanoTime()}",
            userId = (map["userId"] as? String) ?: "",
            phoneNumber = (map["phoneNumber"] as? String) ?: "",
            name = (map["name"] as? String) ?: "Unknown",
            reason = (map["reason"] as? String) ?: "",
            blockedAt = (map["blockedAt"] as? String) ?: now
        )
    }

    fun mapToCallerIdEntry(map: Map<String, Any>): CallerIdEntry {
        val now = dateFormat.format(Date())
        val phone = (map["phoneNumber"] as? String)
            ?: (map["senderNumber"] as? String)
            ?: ""
        return CallerIdEntry(
            id = (map["_id"] as? String) ?: "cid-${System.nanoTime()}",
            phoneNumber = phone,
            name = (map["name"] as? String) ?: "Unknown",
            spamScore = (map["spamScore"] as? Number)?.toInt() ?: 0,
            reportCount = (map["reportCount"] as? Number)?.toInt() ?: 0,
            category = parseSpamCategory(map["category"] as? String),
            lastUpdated = (map["lastUpdated"] as? String) ?: now
        )
    }

    fun mapLookupToCallerIdEntry(map: Map<String, Any>): CallerIdEntry? {
        val source = map["source"] as? String
        if (source == "not_found") return null
        val now = dateFormat.format(Date())
        return CallerIdEntry(
            id = (map["_id"] as? String) ?: "remote-${System.nanoTime()}",
            phoneNumber = (map["phoneNumber"] as? String) ?: "",
            name = (map["name"] as? String) ?: "Unknown",
            spamScore = (map["spamScore"] as? Number)?.toInt() ?: 0,
            reportCount = (map["reportCount"] as? Number)?.toInt() ?: 0,
            category = parseSpamCategory(map["category"] as? String),
            lastUpdated = (map["lastUpdated"] as? String) ?: now
        )
    }

    private fun parseSpamCategory(value: String?): SpamCategory {
        return try {
            value?.let { SpamCategory.valueOf(it) } ?: SpamCategory.SAFE
        } catch (_: IllegalArgumentException) {
            SpamCategory.SAFE
        }
    }
}
