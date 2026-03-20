package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.data.models.LookupResponse
import com.trucaller.backend.data.models.SpamCategory
import io.ktor.http.*
import io.ktor.server.application.*
import com.trucaller.backend.auth.userId
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

@Serializable
data class SpamReportRequest(
    val phoneNumber: String,
    val reason: String? = null
)

/**
 * Registers Caller ID routes under `/api/caller-id`.
 *
 * - `GET  /api/caller-id/lookup/{phoneNumber}` — 3-tier caller ID lookup (authenticated)
 * - `POST /api/caller-id/report`               — report a phone number as spam (authenticated)
 */
fun Route.callerIdRoutes() {

    route("/api/caller-id") {

        authenticate("auth-jwt") {

            // ── GET /api/caller-id/lookup/{phoneNumber} ─────────────────
            get("/lookup/{phoneNumber}") {
                val rawPhone = call.parameters["phoneNumber"]
                if (rawPhone.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<LookupResponse>(
                            success = false,
                            error = "Phone number is required."
                        )
                    )
                    return@get
                }

                val phoneNumber = normalizeToE164(rawPhone)

                val userId = call.userId()

                // ── Tier 1: callerIds collection ────────────────────────
                val callerIdDoc = Collections.callerIds
                    .find(Filters.eq("phoneNumber", phoneNumber))
                    .firstOrNull()

                if (callerIdDoc != null) {
                    val response = LookupResponse(
                        phoneNumber = callerIdDoc.getString("phoneNumber"),
                        name = callerIdDoc.getString("name"),
                        spamScore = callerIdDoc.getInteger("spamScore", 0),
                        reportCount = callerIdDoc.getInteger("reportCount", 0),
                        category = categoryFromScore(callerIdDoc.getInteger("spamScore", 0)),
                        source = "caller_id_db",
                        confidence = 90
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(success = true, data = response)
                    )
                    return@get
                }

                // ── Tier 2: users collection (registered user) ─────────
                val userDoc = Collections.users
                    .find(Filters.eq("phoneNumber", phoneNumber))
                    .firstOrNull()

                if (userDoc != null) {
                    val response = LookupResponse(
                        phoneNumber = userDoc.getString("phoneNumber"),
                        name = userDoc.getString("fullName"),
                        spamScore = 0,
                        reportCount = 0,
                        category = SpamCategory.SAFE,
                        source = "registered_user",
                        confidence = 80
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(success = true, data = response)
                    )
                    return@get
                }

                // ── Tier 3: contacts collection (user's contacts) ──────
                val contactDoc = Collections.contacts
                    .find(
                        Filters.and(
                            Filters.eq("userId", userId),
                            Filters.eq("phoneNumber", phoneNumber)
                        )
                    )
                    .firstOrNull()

                if (contactDoc != null) {
                    val response = LookupResponse(
                        phoneNumber = contactDoc.getString("phoneNumber"),
                        name = contactDoc.getString("name"),
                        spamScore = 0,
                        reportCount = 0,
                        category = SpamCategory.SAFE,
                        source = "contacts",
                        confidence = 70
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(success = true, data = response)
                    )
                    return@get
                }

                // ── Not found ──────────────────────────────────────────
                val notFoundResponse = LookupResponse(
                    phoneNumber = phoneNumber,
                    name = null,
                    spamScore = 0,
                    reportCount = 0,
                    category = SpamCategory.SAFE,
                    source = "not_found",
                    confidence = 0
                )
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse(
                        success = false,
                        data = notFoundResponse,
                        error = "No caller ID information found for $phoneNumber"
                    )
                )
            }

            // ── POST /api/caller-id/upload ────────────────────────────────
            post("/upload") {
                @Serializable
                data class CallerIdUpload(
                    val phoneNumber: String,
                    val name: String,
                    val spamScore: Int = 0,
                    val reportCount: Int = 0,
                    val category: String = "SAFE"
                )

                @Serializable
                data class BulkUploadRequest(val entries: List<CallerIdUpload>)

                val request = try {
                    call.receive<BulkUploadRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, error = "Invalid request body. 'entries' array is required.")
                    )
                    return@post
                }

                val now = Instant.now().toString()
                var inserted = 0
                var updated = 0

                for (entry in request.entries) {
                    val phoneNumber = normalizeToE164(entry.phoneNumber)
                    val existing = Collections.callerIds
                        .find(Filters.eq("phoneNumber", phoneNumber))
                        .firstOrNull()

                    if (existing != null) {
                        // Update name if it changed
                        Collections.callerIds.updateOne(
                            Filters.eq("phoneNumber", phoneNumber),
                            Updates.combine(
                                Updates.set("name", entry.name),
                                Updates.set("lastUpdated", now)
                            )
                        )
                        updated++
                    } else {
                        val doc = Document()
                            .append("_id", ObjectId().toString())
                            .append("phoneNumber", phoneNumber)
                            .append("name", entry.name)
                            .append("spamScore", entry.spamScore)
                            .append("reportCount", entry.reportCount)
                            .append("category", entry.category)
                            .append("lastUpdated", now)
                        Collections.callerIds.insertOne(doc)
                        inserted++
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Unit>(
                        success = true,
                        message = "Caller IDs synced: $inserted new, $updated updated."
                    )
                )
            }

            // ── POST /api/caller-id/report ──────────────────────────────
            post("/report") {
                val request = try {
                    call.receive<SpamReportRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Invalid request body. 'phoneNumber' is required."
                        )
                    )
                    return@post
                }

                val phoneNumber = normalizeToE164(request.phoneNumber)

                if (!phoneNumber.matches(Regex("^\\+[1-9]\\d{6,14}$"))) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(
                            success = false,
                            error = "Invalid phone number format. Use E.164 format (e.g. +256XXXXXXXXX)."
                        )
                    )
                    return@post
                }

                val now = Instant.now().toString()

                // Determine reporter's trust weight (higher trust = more impact)
                val reporterId = call.userId()
                val reporterDoc = Collections.users
                    .find(Filters.eq("_id", reporterId))
                    .firstOrNull()
                val reporterTrustScore = reporterDoc?.getInteger("trustScore", 0) ?: 0
                val trustWeight = trustWeightFromScore(reporterTrustScore)

                // Check if an entry already exists
                val existingDoc = Collections.callerIds
                    .find(Filters.eq("phoneNumber", phoneNumber))
                    .firstOrNull()

                if (existingDoc != null) {
                    // Update existing entry: increment reportCount, increase spamScore weighted by trust
                    val currentScore = existingDoc.getInteger("spamScore", 0)
                    val newScore = minOf(currentScore + trustWeight, 100)
                    val newCategory = categoryFromScore(newScore)

                    Collections.callerIds.updateOne(
                        Filters.eq("phoneNumber", phoneNumber),
                        Updates.combine(
                            Updates.inc("reportCount", 1),
                            Updates.set("spamScore", newScore),
                            Updates.set("category", newCategory.name),
                            Updates.set("lastUpdated", now)
                        )
                    )

                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse<Unit>(
                            success = true,
                            message = "Spam report recorded. Score updated to $newScore (trust weight: $trustWeight)."
                        )
                    )
                } else {
                    // Create a new CallerIdEntry, seeded with trust-weighted score
                    val initialScore = trustWeight
                    val newCategory = categoryFromScore(initialScore)
                    val newDoc = Document()
                        .append("_id", ObjectId().toString())
                        .append("phoneNumber", phoneNumber)
                        .append("name", "Unknown")
                        .append("spamScore", initialScore)
                        .append("reportCount", 1)
                        .append("category", newCategory.name)
                        .append("lastUpdated", now)

                    Collections.callerIds.insertOne(newDoc)

                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse<Unit>(
                            success = true,
                            message = "Spam report recorded. New entry created (trust weight: $trustWeight)."
                        )
                    )
                }
            }
        }
    }
}

// ── Helper functions ────────────────────────────────────────────────────────

/**
 * Normalizes a phone number to E.164 format (+256... for Uganda).
 * Handles common formats: 07XXXXXXXX → +25607XXXXXXXX, 25607... → +25607...
 */
private fun normalizeToE164(phone: String): String {
    val digits = phone.replace(Regex("[^+\\d]"), "")

    return when {
        digits.startsWith("+") -> digits
        digits.startsWith("256") -> "+$digits"
        digits.startsWith("0") -> "+256${digits.substring(1)}"
        else -> "+$digits"
    }
}

/**
 * Determines the [SpamCategory] from a numeric spam score (0-100).
 *
 * - 0–20   → SAFE
 * - 21–60  → SUSPECTED_SPAM
 * - 61–80  → SPAM
 * - 81–100 → FRAUD
 */
private fun categoryFromScore(score: Int): SpamCategory = when {
    score <= 20 -> SpamCategory.SAFE
    score <= 60 -> SpamCategory.SUSPECTED_SPAM
    score <= 80 -> SpamCategory.SPAM
    else -> SpamCategory.FRAUD
}

/**
 * Converts a user's trust score (0-100) into a spam-report weight.
 *
 * - 0–19  (NEW)       → +3  (minimal impact)
 * - 20–49 (BASIC)     → +5  (standard impact)
 * - 50–79 (TRUSTED)   → +8  (significant impact)
 * - 80+   (VERIFIED+) → +12 (high impact)
 */
private fun trustWeightFromScore(trustScore: Int): Int = when {
    trustScore < 20 -> 3
    trustScore < 50 -> 5
    trustScore < 80 -> 8
    else -> 12
}
