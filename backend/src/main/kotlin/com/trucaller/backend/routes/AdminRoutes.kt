package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.trucaller.backend.auth.requireAdmin
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.bson.Document

// ── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class DashboardStats(
    val userCount: Long,
    val deviceCount: Long,
    val reportCount: Long,
    val alarmCount: Long,
    val callerIdCount: Long,
    val smsSpamReportCount: Long
)

@Serializable
data class CallerIdUpdateRequest(
    val spamScore: Int? = null,
    val category: String? = null,
    val name: String? = null
)

@Serializable
data class UserWithDevices(
    val user: String,       // JSON string of user document
    val devices: List<String> // JSON strings of device documents
)

// ── Routes ───────────────────────────────────────────────────────────────────

/**
 * Registers admin dashboard routes under `/api/admin`.
 *
 * All endpoints require JWT authentication and admin role.
 *
 * - `GET    /api/admin/dashboard`        -- aggregate stats
 * - `GET    /api/admin/users`            -- paginated user list
 * - `GET    /api/admin/users/{userId}`   -- single user + their devices
 * - `GET    /api/admin/devices`          -- paginated device list
 * - `GET    /api/admin/caller-ids`       -- paginated caller ID list
 * - `PUT    /api/admin/caller-ids/{id}`  -- update a caller ID entry
 * - `DELETE /api/admin/caller-ids/{id}`  -- delete a caller ID entry
 */
fun Route.adminRoutes() {

    authenticate("auth-jwt") {

        route("/api/admin") {

            // ── GET /api/admin/dashboard ─────────────────────────────────
            get("/dashboard") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@get
                }

                val stats = DashboardStats(
                    userCount = Collections.users.countDocuments(),
                    deviceCount = Collections.devices.countDocuments(),
                    reportCount = Collections.stolenReports.countDocuments(),
                    alarmCount = Collections.alarmLogs.countDocuments(),
                    callerIdCount = Collections.callerIds.countDocuments(),
                    smsSpamReportCount = Collections.smsSpamReports.countDocuments()
                )

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = stats,
                        message = "Dashboard stats retrieved"
                    )
                )
            }

            // ── GET /api/admin/users ─────────────────────────────────────
            get("/users") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@get
                }

                val skip = call.request.queryParameters["skip"]?.toIntOrNull() ?: 0
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val users = Collections.users
                    .find()
                    .skip(skip)
                    .limit(limit)
                    .toList()

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = users.map { it.toJson() },
                        message = "Retrieved ${users.size} user(s)"
                    )
                )
            }

            // ── GET /api/admin/users/{userId} ────────────────────────────
            get("/users/{userId}") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@get
                }

                val userId = call.parameters["userId"]
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Missing userId")
                    )
                    return@get
                }

                val userDoc = Collections.users
                    .find(Filters.eq("_id", userId))
                    .toList()
                    .firstOrNull()

                if (userDoc == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Nothing>(success = false, error = "User not found")
                    )
                    return@get
                }

                val devices = Collections.devices
                    .find(Filters.eq("userId", userId))
                    .toList()

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = UserWithDevices(
                            user = userDoc.toJson(),
                            devices = devices.map { it.toJson() }
                        ),
                        message = "User and devices retrieved"
                    )
                )
            }

            // ── GET /api/admin/devices ───────────────────────────────────
            get("/devices") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@get
                }

                val skip = call.request.queryParameters["skip"]?.toIntOrNull() ?: 0
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val devices = Collections.devices
                    .find()
                    .skip(skip)
                    .limit(limit)
                    .toList()

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = devices.map { it.toJson() },
                        message = "Retrieved ${devices.size} device(s)"
                    )
                )
            }

            // ── GET /api/admin/caller-ids ────────────────────────────────
            get("/caller-ids") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@get
                }

                val skip = call.request.queryParameters["skip"]?.toIntOrNull() ?: 0
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val callerIds = Collections.callerIds
                    .find()
                    .skip(skip)
                    .limit(limit)
                    .toList()

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = callerIds.map { it.toJson() },
                        message = "Retrieved ${callerIds.size} caller ID entry/entries"
                    )
                )
            }

            // ── PUT /api/admin/caller-ids/{id} ──────────────────────────
            put("/caller-ids/{id}") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@put
                }

                val entryId = call.parameters["id"]
                if (entryId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Missing caller ID entry id")
                    )
                    return@put
                }

                val request = try {
                    call.receive<CallerIdUpdateRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Invalid request body")
                    )
                    return@put
                }

                // Build update document from non-null fields
                val updates = mutableListOf<org.bson.conversions.Bson>()
                request.spamScore?.let { updates.add(Updates.set("spamScore", it)) }
                request.category?.let { updates.add(Updates.set("category", it)) }
                request.name?.let { updates.add(Updates.set("name", it)) }

                if (updates.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "No fields to update")
                    )
                    return@put
                }

                val result = Collections.callerIds.updateOne(
                    Filters.eq("_id", entryId),
                    Updates.combine(updates)
                )

                if (result.matchedCount == 0L) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Nothing>(success = false, error = "Caller ID entry not found")
                    )
                    return@put
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Nothing>(
                        success = true,
                        message = "Caller ID entry updated"
                    )
                )
            }

            // ── GET /api/admin/sms-spam-reports ────────────────────────
            get("/sms-spam-reports") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@get
                }

                val skip = call.request.queryParameters["skip"]?.toIntOrNull() ?: 0
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                val reports = Collections.smsSpamReports
                    .find()
                    .skip(skip)
                    .limit(limit)
                    .toList()

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = reports.map { it.toJson() },
                        message = "Retrieved ${reports.size} SMS spam report(s)"
                    )
                )
            }

            // ── DELETE /api/admin/caller-ids/{id} ────────────────────────
            delete("/caller-ids/{id}") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@delete
                }

                val entryId = call.parameters["id"]
                if (entryId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Missing caller ID entry id")
                    )
                    return@delete
                }

                val result = Collections.callerIds.deleteOne(
                    Filters.eq("_id", entryId)
                )

                if (result.deletedCount == 0L) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Nothing>(success = false, error = "Caller ID entry not found")
                    )
                    return@delete
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Nothing>(
                        success = true,
                        message = "Caller ID entry deleted"
                    )
                )
            }
        }
    }
}
