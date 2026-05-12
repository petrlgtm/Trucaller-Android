package com.trucaller.backend.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.trucaller.backend.data.Collections
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

object CallerIdService {

    private val logger = LoggerFactory.getLogger(CallerIdService::class.java)

    /**
     * Contributes a crowdsourced name alias for a single phone number (e.g. from
     * user registration). Increments weight if the (phone, label) pair exists,
     * inserts otherwise, then immediately refreshes bestName in callerIds.
     */
    suspend fun contributeAlias(phone: String, label: String, sourceUserId: String) {
        if (label.isBlank() || phone.isBlank()) return
        val now = Instant.now().toString()

        val existing = Collections.aliases
            .find(Filters.and(Filters.eq("phone", phone), Filters.eq("label", label)))
            .firstOrNull()

        if (existing != null) {
            Collections.aliases.updateOne(
                Filters.and(Filters.eq("phone", phone), Filters.eq("label", label)),
                Updates.combine(Updates.inc("weight", 1), Updates.set("updatedAt", now))
            )
        } else {
            Collections.aliases.insertOne(
                Document()
                    .append("_id", ObjectId().toString())
                    .append("phone", phone)
                    .append("label", label)
                    .append("weight", 1)
                    .append("sourceUserId", sourceUserId)
                    .append("claimed", false)
                    .append("updatedAt", now)
            )
        }

        refreshBestName(phone)
    }

    /**
     * Bulk-upserts aliases for a batch of contacts (e.g. from a contact sync upload).
     * Uses MongoDB bulkWrite for efficiency. Does NOT refresh bestName immediately —
     * the hourly background job handles that pass.
     */
    suspend fun contributeAliasBatch(entries: List<Triple<String, String, String>>) {
        val now = Instant.now().toString()

        val ops = entries
            .filter { (phone, label, _) -> phone.isNotBlank() && label.isNotBlank() }
            .map { (phone, label, userId) ->
                UpdateOneModel<Document>(
                    Filters.and(Filters.eq("phone", phone), Filters.eq("label", label)),
                    Document("\$inc", Document("weight", 1))
                        .append("\$set", Document("updatedAt", now))
                        .append(
                            "\$setOnInsert", Document()
                                .append("_id", ObjectId().toString())
                                .append("sourceUserId", userId)
                                .append("claimed", false)
                        ),
                    UpdateOptions().upsert(true)
                )
            }

        if (ops.isEmpty()) return
        runCatching { Collections.aliases.bulkWrite(ops) }
            .onFailure { logger.error("Alias bulkWrite failed: ${it.message}") }
    }

    /**
     * Scores all known aliases for a phone number and returns the top label.
     *
     * score = (weight × 2) + recencyBonus + claimedBonus
     *   recencyBonus: +5 if updated ≤30 days ago, +2 if ≤90 days
     *   claimedBonus: +15 if the alias is marked as claimed by the number owner
     */
    suspend fun computeBestName(phone: String): String? {
        val aliases = Collections.aliases.find(Filters.eq("phone", phone)).toList()
        if (aliases.isEmpty()) return null

        return aliases
            .mapNotNull { doc ->
                val label = doc.getString("label") ?: return@mapNotNull null
                val weight = doc.getInteger("weight", 1)
                val claimed = doc.getBoolean("claimed", false) ?: false
                val score = weight * 2 + recencyBonus(doc.getString("updatedAt")) + if (claimed) 15 else 0
                label to score
            }
            .maxByOrNull { it.second }
            ?.first
    }

    /**
     * Recomputes bestName for [phone] and writes it to the callerIds document.
     * Creates a minimal callerIds entry if none exists yet.
     */
    suspend fun refreshBestName(phone: String) {
        val bestName = computeBestName(phone) ?: return
        val now = Instant.now().toString()

        val existing = Collections.callerIds.find(Filters.eq("phoneNumber", phone)).firstOrNull()
        if (existing != null) {
            Collections.callerIds.updateOne(
                Filters.eq("phoneNumber", phone),
                Updates.combine(Updates.set("bestName", bestName), Updates.set("lastUpdated", now))
            )
        } else {
            Collections.callerIds.insertOne(
                Document()
                    .append("_id", ObjectId().toString())
                    .append("phoneNumber", phone)
                    .append("name", bestName)
                    .append("bestName", bestName)
                    .append("spamScore", 0)
                    .append("reportCount", 0)
                    .append("category", "SAFE")
                    .append("verifiedBusiness", false)
                    .append("claimed", false)
                    .append("lastUpdated", now)
            )
        }

        RedisCache.invalidate(phone)
    }

    private fun recencyBonus(updatedAt: String?): Int {
        if (updatedAt.isNullOrBlank()) return 0
        return try {
            val days = Duration.between(Instant.parse(updatedAt), Instant.now()).toDays()
            when {
                days <= 30 -> 5
                days <= 90 -> 2
                else -> 0
            }
        } catch (e: Exception) { 0 }
    }
}
