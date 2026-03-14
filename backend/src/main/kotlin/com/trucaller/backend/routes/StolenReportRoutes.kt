package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.trucaller.backend.auth.requireAdmin
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import com.trucaller.backend.auth.userId
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

// ── Request DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class StolenReportRequest(
    val deviceId: String,
    val description: String,
    val pinVerified: Boolean,
    val lastKnownIp: String? = null,
    val lastKnownLatitude: Double? = null,
    val lastKnownLongitude: Double? = null,
    val lastKnownCity: String? = null
)

@Serializable
data class StatusUpdateRequest(
    val status: String
)

// ── Routes ───────────────────────────────────────────────────────────────────

/**
 * Registers stolen-report routes:
 *
 * - `POST   /api/stolen/report`            — file a new stolen report (authenticated)
 * - `GET    /api/stolen/reports`            — list current user's reports (authenticated)
 * - `PUT    /api/stolen/reports/{id}/status` — update a report's status (authenticated)
 * - `GET    /api/admin/stolen-reports`      — list ALL reports (admin only)
 */
fun Route.stolenReportRoutes() {

    // ── Authenticated user endpoints ─────────────────────────────────────
    authenticate("auth-jwt") {

        // POST /api/stolen/report
        post("/api/stolen/report") {
            val request = try {
                call.receive<StolenReportRequest>()
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Invalid request body"
                    )
                )
                return@post
            }

            val userId = call.userId()

            val reportId = ObjectId().toString()
            val now = Instant.now().toString()

            // Build optional lastKnownLocation sub-document
            val locationDoc: Document? =
                if (request.lastKnownLatitude != null &&
                    request.lastKnownLongitude != null &&
                    request.lastKnownCity != null
                ) {
                    Document()
                        .append("latitude", request.lastKnownLatitude)
                        .append("longitude", request.lastKnownLongitude)
                        .append("city", request.lastKnownCity)
                } else null

            val reportDoc = Document()
                .append("_id", reportId)
                .append("deviceId", request.deviceId)
                .append("reportedBy", userId)
                .append("reportedAt", now)
                .append("status", "PENDING")
                .append("description", request.description)
                .append("pinVerified", request.pinVerified)
                .append("lastKnownIp", request.lastKnownIp)
                .append("lastKnownLocation", locationDoc)

            Collections.stolenReports.insertOne(reportDoc)

            // Mark the device as STOLEN
            Collections.devices.updateOne(
                Filters.eq("deviceId", request.deviceId),
                Document("\$set", Document("status", "STOLEN"))
            )

            call.respond(
                HttpStatusCode.Created,
                ApiResponse<Nothing>(
                    success = true,
                    message = "Stolen report filed successfully"
                )
            )
        }

        // GET /api/stolen/reports
        get("/api/stolen/reports") {
            val userId = call.userId()

            val reports = Collections.stolenReports
                .find(Filters.eq("reportedBy", userId))
                .toList()

            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = reports.map { it.toJson() },
                    message = "Stolen reports retrieved"
                )
            )
        }

        // PUT /api/stolen/reports/{id}/status
        put("/api/stolen/reports/{id}/status") {
            val reportId = call.parameters["id"]
            if (reportId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Missing report id"
                    )
                )
                return@put
            }

            val request = try {
                call.receive<StatusUpdateRequest>()
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Invalid request body"
                    )
                )
                return@put
            }

            Collections.stolenReports.updateOne(
                Filters.eq("_id", reportId),
                Document("\$set", Document("status", request.status))
            )

            call.respond(
                HttpStatusCode.OK,
                ApiResponse<Nothing>(
                    success = true,
                    message = "Report status updated to ${request.status}"
                )
            )
        }

        // GET /api/admin/stolen-reports  (admin only)
        get("/api/admin/stolen-reports") {
            try {
                call.requireAdmin()
            } catch (e: IllegalAccessException) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse<Nothing>(
                        success = false,
                        error = e.message ?: "Admin access required"
                    )
                )
                return@get
            }

            val reports = Collections.stolenReports
                .find()
                .toList()

            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = reports.map { it.toJson() },
                    message = "All stolen reports retrieved"
                )
            )
        }
    }
}
