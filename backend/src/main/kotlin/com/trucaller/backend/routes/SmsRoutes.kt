package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.trucaller.backend.auth.userId
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.data.models.SpamCategory
import com.trucaller.backend.service.PhoneNormalizer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

// ── Request / Response DTOs ──────────────────────────────────────────────

@Serializable
data class SmsSpamReportRequest(
    val senderNumber: String,
    val messageBody: String? = null,
    val reason: String? = null
)

@Serializable
data class BulkSmsSpamReportRequest(
    val reports: List<SmsSpamReportRequest>
)

@Serializable
data class SmsSpamReportResponse(
    val id: String,
    val userId: String,
    val senderNumber: String,
    val messageBody: String?,
    val reason: String?,
    val reportedAt: String
)

@Serializable
data class SmsSpamCheckResponse(
    val phoneNumber: String,
    val isSpam: Boolean,
    val spamScore: Int,
    val category: SpamCategory,
    val reportCount: Int,
    val name: String?
)

/**
 * SMS spam management routes under `/api/sms`.
 *
 * - `POST /api/sms/report`         — report an SMS sender as spam
 * - `POST /api/sms/report/bulk`    — bulk report multiple SMS senders
 * - `GET  /api/sms/check/{number}` — check if a number is known SMS spam
 * - `GET  /api/sms/reports`        — get user's own spam reports
 * - `GET  /api/sms/spam-numbers`   — get all known spam numbers (paginated)
 */
fun Route.smsRoutes() {

    route("/api/sms") {

        authenticate("auth-jwt") {

            // ── POST /api/sms/report ─────────────────────────────────────
            post("/report") {
                val userId = call.userId()
                val request = try {
                    call.receive<SmsSpamReportRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, error = "Invalid request. 'senderNumber' is required.")
                    )
                    return@post
                }

                val phoneNumber = PhoneNormalizer.normalize(request.senderNumber)
                val now = Instant.now().toString()

                // Dedup: one report per user per sender number
                val alreadyReported = Collections.smsSpamReports
                    .find(Filters.and(
                        Filters.eq("userId", userId),
                        Filters.eq("senderNumber", phoneNumber)
                    ))
                    .firstOrNull()
                if (alreadyReported != null) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiResponse<Unit>(success = false, error = "You have already reported this number.")
                    )
                    return@post
                }

                // Save the spam report
                val reportDoc = Document()
                    .append("_id", ObjectId().toString())
                    .append("userId", userId)
                    .append("senderNumber", phoneNumber)
                    .append("messageBody", request.messageBody)
                    .append("reason", request.reason ?: "SMS spam")
                    .append("reportedAt", now)

                Collections.smsSpamReports.insertOne(reportDoc)

                // Update the caller ID entry spam score
                updateCallerIdSpamScore(phoneNumber, now)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Unit>(success = true, message = "SMS spam report recorded.")
                )
            }

            // ── POST /api/sms/report/bulk ────────────────────────────────
            post("/report/bulk") {
                val userId = call.userId()
                val request = try {
                    call.receive<BulkSmsSpamReportRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, error = "Invalid request body.")
                    )
                    return@post
                }

                if (request.reports.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, error = "At least one report is required.")
                    )
                    return@post
                }

                val now = Instant.now().toString()
                var count = 0

                for (report in request.reports) {
                    val phoneNumber = PhoneNormalizer.normalize(report.senderNumber)

                    val reportDoc = Document()
                        .append("_id", ObjectId().toString())
                        .append("userId", userId)
                        .append("senderNumber", phoneNumber)
                        .append("messageBody", report.messageBody)
                        .append("reason", report.reason ?: "SMS spam")
                        .append("reportedAt", now)

                    Collections.smsSpamReports.insertOne(reportDoc)
                    updateCallerIdSpamScore(phoneNumber, now)
                    count++
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Unit>(success = true, message = "$count SMS spam reports recorded.")
                )
            }

            // ── GET /api/sms/check/{number} ──────────────────────────────
            get("/check/{number}") {
                val rawNumber = call.parameters["number"]
                if (rawNumber.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<SmsSpamCheckResponse>(success = false, error = "Phone number is required.")
                    )
                    return@get
                }

                val phoneNumber = PhoneNormalizer.normalize(rawNumber)

                // Check caller ID database
                val callerIdDoc = Collections.callerIds
                    .find(Filters.eq("phoneNumber", phoneNumber))
                    .firstOrNull()

                if (callerIdDoc != null) {
                    val score = callerIdDoc.getInteger("spamScore", 0)
                    val response = SmsSpamCheckResponse(
                        phoneNumber = phoneNumber,
                        isSpam = score >= 60,
                        spamScore = score,
                        category = categoryFromScore(score),
                        reportCount = callerIdDoc.getInteger("reportCount", 0),
                        name = callerIdDoc.getString("name")
                    )
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = response))
                    return@get
                }

                // Count SMS spam reports for this number
                val reportCount = Collections.smsSpamReports
                    .countDocuments(Filters.eq("senderNumber", phoneNumber))

                val response = SmsSpamCheckResponse(
                    phoneNumber = phoneNumber,
                    isSpam = reportCount >= 3,
                    spamScore = minOf(reportCount.toInt() * 10, 100),
                    category = categoryFromScore(minOf(reportCount.toInt() * 10, 100)),
                    reportCount = reportCount.toInt(),
                    name = null
                )
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = response))
            }

            // ── GET /api/sms/reports ─────────────────────────────────────
            get("/reports") {
                val userId = call.userId()
                val skip = call.request.queryParameters["skip"]?.toIntOrNull() ?: 0
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val reports = Collections.smsSpamReports
                    .find(Filters.eq("userId", userId))
                    .sort(Sorts.descending("reportedAt"))
                    .skip(skip)
                    .limit(limit)
                    .toList()
                    .map { doc ->
                        SmsSpamReportResponse(
                            id = doc.getString("_id"),
                            userId = doc.getString("userId"),
                            senderNumber = doc.getString("senderNumber"),
                            messageBody = doc.getString("messageBody"),
                            reason = doc.getString("reason"),
                            reportedAt = doc.getString("reportedAt")
                        )
                    }

                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = reports))
            }

            // ── GET /api/sms/spam-numbers ────────────────────────────────
            get("/spam-numbers") {
                val skip = call.request.queryParameters["skip"]?.toIntOrNull() ?: 0
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

                val spamEntries = Collections.callerIds
                    .find(Filters.gte("spamScore", 30))
                    .sort(Sorts.descending("spamScore"))
                    .skip(skip)
                    .limit(limit)
                    .toList()
                    .map { doc ->
                        SmsSpamCheckResponse(
                            phoneNumber = doc.getString("phoneNumber"),
                            isSpam = doc.getInteger("spamScore", 0) >= 60,
                            spamScore = doc.getInteger("spamScore", 0),
                            category = categoryFromScore(doc.getInteger("spamScore", 0)),
                            reportCount = doc.getInteger("reportCount", 0),
                            name = doc.getString("name")
                        )
                    }

                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = spamEntries))
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

private suspend fun updateCallerIdSpamScore(phoneNumber: String, now: String) {
    val existingDoc = Collections.callerIds
        .find(Filters.eq("phoneNumber", phoneNumber))
        .firstOrNull()

    if (existingDoc != null) {
        val currentScore = existingDoc.getInteger("spamScore", 0)
        val newScore = minOf(currentScore + 5, 100)
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
    } else {
        val newDoc = Document()
            .append("_id", ObjectId().toString())
            .append("phoneNumber", phoneNumber)
            .append("name", "SMS Spam Sender")
            .append("spamScore", 5)
            .append("reportCount", 1)
            .append("category", categoryFromScore(5).name)
            .append("lastUpdated", now)

        Collections.callerIds.insertOne(newDoc)
    }
}

private fun categoryFromScore(score: Int): SpamCategory = when {
    score <= 20 -> SpamCategory.SAFE
    score <= 60 -> SpamCategory.SUSPECTED_SPAM
    score <= 80 -> SpamCategory.SPAM
    else -> SpamCategory.FRAUD
}
