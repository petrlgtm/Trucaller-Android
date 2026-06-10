package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.trucaller.backend.auth.getAdminRole
import com.trucaller.backend.auth.userId
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.data.models.LookupResponse
import com.trucaller.backend.data.models.SpamCategory
import com.trucaller.backend.service.CallerIdService
import com.trucaller.backend.service.PhoneNormalizer
import com.trucaller.backend.service.RedisCache
import com.trucaller.backend.service.SearchLogger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

@Serializable
data class SpamReportRequest(
    val phoneNumber: String,
    val reason: String? = null
)

@Serializable
data class SpamVerifyRequest(
    val phoneNumber: String,
    val vote: String  // "confirm" or "dispute"
)

@Serializable
data class VerificationStatus(
    val phoneNumber: String,
    val confirmCount: Int,
    val disputeCount: Int,
    val communityVerified: Boolean,
    val userVote: String?
)

private val cacheJson = Json { ignoreUnknownKeys = true }

fun Route.callerIdRoutes() {

    route("/api/caller-id") {

        authenticate("auth-jwt") {

            // ── GET /api/caller-id/lookup/{phoneNumber} ─────────────────
            get("/lookup/{phoneNumber}") {
                val rawPhone = call.parameters["phoneNumber"]
                if (rawPhone.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<LookupResponse>(success = false, error = "Phone number is required.")
                    )
                    return@get
                }

                val phone = PhoneNormalizer.normalize(rawPhone)
                val userId = call.userId()
                val startMs = System.currentTimeMillis()

                // ── Redis cache ─────────────────────────────────────────
                val cached = RedisCache.get(phone)
                if (cached != null) {
                    runCatching { cacheJson.decodeFromString<LookupResponse>(cached) }
                        .onSuccess { response ->
                            val found = response.source != "not_found"
                            SearchLogger.log(phone, userId, cacheHit = true, lookupMs = System.currentTimeMillis() - startMs, found = found)
                            val status = if (found) HttpStatusCode.OK else HttpStatusCode.NotFound
                            call.respond(status, ApiResponse(success = found, data = response))
                            return@get
                        }
                    // Decode failure → fall through to MongoDB
                }

                // ── Tier 1: callerIds (uses bestName ranking) ───────────
                val callerIdDoc = Collections.callerIds
                    .find(Filters.eq("phoneNumber", phone))
                    .firstOrNull()

                if (callerIdDoc != null) {
                    val displayName = callerIdDoc.getString("bestName")
                        ?: callerIdDoc.getString("name")
                    val score = callerIdDoc.getInteger("spamScore", 0)
                    val response = LookupResponse(
                        phoneNumber = phone,
                        name = displayName,
                        spamScore = score,
                        reportCount = callerIdDoc.getInteger("reportCount", 0),
                        category = categoryFromScore(score),
                        source = "caller_id_db",
                        confidence = 90
                    )
                    cacheAndLog(phone, response, userId, startMs)
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = response))
                    return@get
                }

                // ── Tier 2: registered user ─────────────────────────────
                val userDoc = Collections.users
                    .find(Filters.eq("phoneNumber", phone))
                    .firstOrNull()

                if (userDoc != null) {
                    val response = LookupResponse(
                        phoneNumber = phone,
                        name = userDoc.getString("fullName"),
                        spamScore = 0,
                        reportCount = 0,
                        category = SpamCategory.SAFE,
                        source = "registered_user",
                        confidence = 80
                    )
                    cacheAndLog(phone, response, userId, startMs)
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = response))
                    return@get
                }

                // ── Tier 3: caller's own contacts ───────────────────────
                val contactDoc = Collections.contacts
                    .find(Filters.and(
                        Filters.eq("userId", userId),
                        Filters.eq("phoneNumber", phone)
                    ))
                    .firstOrNull()

                if (contactDoc != null) {
                    val response = LookupResponse(
                        phoneNumber = phone,
                        name = contactDoc.getString("name"),
                        spamScore = 0,
                        reportCount = 0,
                        category = SpamCategory.SAFE,
                        source = "contacts",
                        confidence = 70
                    )
                    cacheAndLog(phone, response, userId, startMs)
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = response))
                    return@get
                }

                // ── Not found ───────────────────────────────────────────
                val notFound = LookupResponse(
                    phoneNumber = phone,
                    name = null,
                    spamScore = 0,
                    reportCount = 0,
                    category = SpamCategory.SAFE,
                    source = "not_found",
                    confidence = 0
                )
                runCatching { RedisCache.set(phone, cacheJson.encodeToString(notFound), notFound = true) }
                SearchLogger.log(phone, userId, cacheHit = false, lookupMs = System.currentTimeMillis() - startMs, found = false)
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse(success = false, data = notFound, error = "No caller ID found for $phone")
                )
            }

            // ── POST /api/caller-id/upload ────────────────────────────────
            post("/upload") {
                if (call.getAdminRole() == null) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse<Unit>(success = false, error = "Admin access required"))
                    return@post
                }

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
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request body. 'entries' array is required."))
                    return@post
                }

                val now = Instant.now().toString()
                var inserted = 0
                var updated = 0

                for (entry in request.entries) {
                    val phoneNumber = PhoneNormalizer.normalize(entry.phoneNumber)
                    val existing = Collections.callerIds.find(Filters.eq("phoneNumber", phoneNumber)).firstOrNull()

                    if (existing != null) {
                        Collections.callerIds.updateOne(
                            Filters.eq("phoneNumber", phoneNumber),
                            Updates.combine(
                                Updates.set("name", entry.name),
                                Updates.set("lastUpdated", now)
                            )
                        )
                        updated++
                    } else {
                        Collections.callerIds.insertOne(
                            Document()
                                .append("_id", ObjectId().toString())
                                .append("phoneNumber", phoneNumber)
                                .append("name", entry.name)
                                .append("spamScore", entry.spamScore)
                                .append("reportCount", entry.reportCount)
                                .append("category", entry.category)
                                .append("verifiedBusiness", false)
                                .append("claimed", false)
                                .append("lastUpdated", now)
                        )
                        inserted++
                    }
                    runCatching { RedisCache.invalidate(phoneNumber) }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Unit>(success = true, message = "Caller IDs synced: $inserted new, $updated updated.")
                )
            }

            // ── POST /api/caller-id/report ──────────────────────────────
            post("/report") {
                val request = try {
                    call.receive<SpamReportRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request body. 'phoneNumber' is required."))
                    return@post
                }

                val phoneNumber = PhoneNormalizer.normalize(request.phoneNumber)

                if (!PhoneNormalizer.isValid(phoneNumber)) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid phone number format. Use E.164 (e.g. +256XXXXXXXXX)."))
                    return@post
                }

                val now = Instant.now().toString()
                val reporterId = call.userId()

                // Dedup: one report per user per number
                val alreadyReported = Collections.spamReports
                    .find(Filters.and(
                        Filters.eq("userId", reporterId),
                        Filters.eq("phoneNumber", phoneNumber)
                    ))
                    .firstOrNull()
                if (alreadyReported != null) {
                    call.respond(HttpStatusCode.Conflict, ApiResponse<Unit>(success = false, error = "You have already reported this number."))
                    return@post
                }
                Collections.spamReports.insertOne(
                    Document()
                        .append("userId", reporterId)
                        .append("phoneNumber", phoneNumber)
                        .append("reason", request.reason)
                        .append("reportedAt", now)
                )

                val reporterDoc = Collections.users.find(Filters.eq("_id", reporterId)).firstOrNull()
                val trustWeight = trustWeightFromScore(reporterDoc?.getInteger("trustScore", 0) ?: 0)

                val existingDoc = Collections.callerIds.find(Filters.eq("phoneNumber", phoneNumber)).firstOrNull()

                if (existingDoc != null) {
                    val newScore = minOf(existingDoc.getInteger("spamScore", 0) + trustWeight, 100)
                    Collections.callerIds.updateOne(
                        Filters.eq("phoneNumber", phoneNumber),
                        Updates.combine(
                            Updates.inc("reportCount", 1),
                            Updates.set("spamScore", newScore),
                            Updates.set("category", categoryFromScore(newScore).name),
                            Updates.set("lastUpdated", now)
                        )
                    )
                    runCatching { RedisCache.invalidate(phoneNumber) }
                    call.respond(HttpStatusCode.OK, ApiResponse<Unit>(success = true, message = "Spam report recorded. Score updated to $newScore (trust weight: $trustWeight)."))
                } else {
                    val initialScore = trustWeight
                    Collections.callerIds.insertOne(
                        Document()
                            .append("_id", ObjectId().toString())
                            .append("phoneNumber", phoneNumber)
                            .append("name", "Unknown")
                            .append("spamScore", initialScore)
                            .append("reportCount", 1)
                            .append("category", categoryFromScore(initialScore).name)
                            .append("verifiedBusiness", false)
                            .append("claimed", false)
                            .append("lastUpdated", now)
                    )
                    call.respond(HttpStatusCode.OK, ApiResponse<Unit>(success = true, message = "Spam report recorded. New entry created (trust weight: $trustWeight)."))
                }
            }

            // ── POST /api/caller-id/verify ────────────────────────────────
            post("/verify") {
                val request = try {
                    call.receive<SpamVerifyRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid request body. 'phoneNumber' and 'vote' (confirm/dispute) are required."))
                    return@post
                }

                if (request.vote !in listOf("confirm", "dispute")) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Vote must be 'confirm' or 'dispute'."))
                    return@post
                }

                val phoneNumber = PhoneNormalizer.normalize(request.phoneNumber)
                val voterId = call.userId()
                val now = Instant.now().toString()

                val existingVote = Collections.spamVerifications
                    .find(Filters.and(Filters.eq("phoneNumber", phoneNumber), Filters.eq("userId", voterId)))
                    .firstOrNull()

                if (existingVote != null) {
                    Collections.spamVerifications.updateOne(
                        Filters.and(Filters.eq("phoneNumber", phoneNumber), Filters.eq("userId", voterId)),
                        Updates.combine(Updates.set("vote", request.vote), Updates.set("updatedAt", now))
                    )
                } else {
                    Collections.spamVerifications.insertOne(
                        Document()
                            .append("_id", ObjectId().toString())
                            .append("phoneNumber", phoneNumber)
                            .append("userId", voterId)
                            .append("vote", request.vote)
                            .append("createdAt", now)
                            .append("updatedAt", now)
                    )
                }

                val confirmCount = Collections.spamVerifications.countDocuments(
                    Filters.and(Filters.eq("phoneNumber", phoneNumber), Filters.eq("vote", "confirm"))
                ).toInt()
                val disputeCount = Collections.spamVerifications.countDocuments(
                    Filters.and(Filters.eq("phoneNumber", phoneNumber), Filters.eq("vote", "dispute"))
                ).toInt()

                val communityVerified = confirmCount >= 5 && confirmCount > disputeCount * 2
                if (communityVerified) {
                    Collections.callerIds.updateOne(
                        Filters.eq("phoneNumber", phoneNumber),
                        Updates.combine(Updates.set("communityVerified", true), Updates.set("lastUpdated", now))
                    )
                    runCatching { RedisCache.invalidate(phoneNumber) }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = VerificationStatus(phoneNumber, confirmCount, disputeCount, communityVerified, request.vote),
                        message = "Vote recorded."
                    )
                )
            }

            // ── GET /api/caller-id/verify/{phoneNumber} ──────────────────
            get("/verify/{phoneNumber}") {
                val rawPhone = call.parameters["phoneNumber"]
                if (rawPhone.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<VerificationStatus>(success = false, error = "Phone number is required."))
                    return@get
                }

                val phoneNumber = PhoneNormalizer.normalize(rawPhone)
                val voterId = call.userId()

                val confirmCount = Collections.spamVerifications.countDocuments(
                    Filters.and(Filters.eq("phoneNumber", phoneNumber), Filters.eq("vote", "confirm"))
                ).toInt()
                val disputeCount = Collections.spamVerifications.countDocuments(
                    Filters.and(Filters.eq("phoneNumber", phoneNumber), Filters.eq("vote", "dispute"))
                ).toInt()

                val userVote = Collections.spamVerifications
                    .find(Filters.and(Filters.eq("phoneNumber", phoneNumber), Filters.eq("userId", voterId)))
                    .firstOrNull()
                    ?.getString("vote")

                val communityVerified = Collections.callerIds
                    .find(Filters.eq("phoneNumber", phoneNumber))
                    .firstOrNull()
                    ?.getBoolean("communityVerified", false) ?: false

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = VerificationStatus(phoneNumber, confirmCount, disputeCount, communityVerified, userVote)
                    )
                )
            }

            // ── GET /api/caller-id/spam-sync ──────────────────────────────
            get("/spam-sync") {
                try {
                    val since = call.request.queryParameters["since"] ?: "1970-01-01T00:00:00Z"
                    val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
                    val cursor = call.request.queryParameters["cursor"]

                    val baseFilter = Filters.gt("lastUpdated", since)
                    val filter = if (cursor != null) {
                        Filters.and(baseFilter, Filters.gt("_id", cursor))
                    } else {
                        baseFilter
                    }

                    val entries = mutableListOf<Map<String, Any?>>()
                    var lastId: String? = null

                    Collections.callerIds
                        .find(filter)
                        .sort(Document("_id", 1))
                        .limit(limit)
                        .collect { doc ->
                            val rawId = doc["_id"]
                            lastId = when (rawId) {
                                is String -> rawId
                                is ObjectId -> rawId.toHexString()
                                else -> rawId?.toString()
                            }
                            entries.add(mapOf(
                                "phoneNumber" to (doc.getString("phoneNumber") ?: ""),
                                "name" to (doc.getString("bestName") ?: doc.getString("name") ?: ""),
                                "spamScore" to (doc.getInteger("spamScore") ?: 0),
                                "reportCount" to (doc.getInteger("reportCount") ?: 0),
                                "category" to (doc.getString("category") ?: "SAFE"),
                                "lastUpdated" to (doc.getString("lastUpdated") ?: "")
                            ))
                        }

                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            data = mapOf(
                                "entries" to entries,
                                "nextCursor" to lastId,
                                "hasMore" to (entries.size == limit)
                            )
                        )
                    )
                } catch (e: Exception) {
                    call.application.environment.log.error("spam-sync failed", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, error = "Sync temporarily unavailable: ${e.message}")
                    )
                }
            }
        }
    }
}

// ── Private helpers ─────────────────────────────────────────────────────────

private suspend fun cacheAndLog(phone: String, response: LookupResponse, userId: String, startMs: Long) {
    runCatching { RedisCache.set(phone, cacheJson.encodeToString(response)) }
    SearchLogger.log(phone, userId, cacheHit = false, lookupMs = System.currentTimeMillis() - startMs, found = true)
}

private fun categoryFromScore(score: Int): SpamCategory = when {
    score <= 20 -> SpamCategory.SAFE
    score <= 60 -> SpamCategory.SUSPECTED_SPAM
    score <= 80 -> SpamCategory.SPAM
    else -> SpamCategory.FRAUD
}

private fun trustWeightFromScore(trustScore: Int): Int = when {
    trustScore < 20 -> 3
    trustScore < 50 -> 5
    trustScore < 80 -> 8
    else -> 12
}
