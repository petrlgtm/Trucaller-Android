package com.trucaller.backend.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.trucaller.backend.auth.requireAdmin
import com.trucaller.backend.auth.userId
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.AdminPasswordUpdateRequest
import com.trucaller.backend.data.models.AdminProfileUpdateRequest
import com.trucaller.backend.data.models.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.bson.Document
import org.bson.types.ObjectId

private fun Document.toJsonElement(): JsonElement = Json.parseToJsonElement(toJson())
private fun Document.toJsonElementStripped(vararg fields: String): JsonElement {
    val copy = Document(this)
    fields.forEach { copy.remove(it) }
    return Json.parseToJsonElement(copy.toJson())
}

// ── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class DashboardStats(
    val userCount: Long,
    val deviceCount: Long,
    val reportCount: Long,
    val alarmCount: Long,
    val callerIdCount: Long,
    val smsSpamReportCount: Long,
    val pendingReports: Long = 0,
    val activeDevices: Long = 0,
    val verifiedReports: Long = 0,
    val resolvedReports: Long = 0
)

@Serializable
data class CallerIdCreateRequest(
    val id: String? = null,
    val phoneNumber: String,
    val name: String,
    val spamScore: Int = 0,
    val reportCount: Int = 0,
    val category: String = "SAFE",
    val lastUpdated: String? = null
)

@Serializable
data class CallerIdUpdateRequest(
    val spamScore: Int? = null,
    val category: String? = null,
    val name: String? = null
)

@Serializable
data class AdminStatusUpdateRequest(
    val status: String
)

@Serializable
data class TrustUpdateRequest(
    val trustScore: Int? = null,
    val trustLevel: String? = null
)

@Serializable
data class UserWithDevices(
    val user: JsonElement,
    val devices: List<JsonElement>
)

@Serializable
data class BulkDeleteRequest(
    val userIds: List<String>
)

@Serializable
data class LoginAuditEntry(
    val phone: String,
    val ip: String,
    val success: Boolean,
    val reason: String,
    val timestamp: String
)

@Serializable
data class LoginAuditPage(
    val logs: List<LoginAuditEntry>,
    val count: Int
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
 * - `PUT    /api/admin/devices/{deviceId}/status` -- update device status
 * - `GET    /api/admin/caller-ids`       -- paginated caller ID list
 * - `POST   /api/admin/caller-ids`      -- create a caller ID entry
 * - `PUT    /api/admin/caller-ids/{id}`  -- update a caller ID entry
 * - `DELETE /api/admin/caller-ids/{id}`  -- delete a caller ID entry
 * - `PUT    /api/admin/stolen-reports/{id}/status` -- update stolen report status (no ownership check)
 * - `PUT    /api/admin/profile`           -- update admin name and email
 * - `PUT    /api/admin/password`          -- update admin password (BCrypt verified)
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
                    smsSpamReportCount = Collections.smsSpamReports.countDocuments(),
                    pendingReports = Collections.stolenReports.countDocuments(
                        Filters.eq("status", "PENDING")
                    ),
                    activeDevices = Collections.devices.countDocuments(
                        Filters.eq("status", "ACTIVE")
                    ),
                    verifiedReports = Collections.stolenReports.countDocuments(
                        Filters.eq("status", "VERIFIED")
                    ),
                    resolvedReports = Collections.stolenReports.countDocuments(
                        Filters.eq("status", "RESOLVED")
                    )
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
                val search = call.request.queryParameters["search"]?.trim()
                val sortParam = call.request.queryParameters["sort"]
                val sortOrder = if (sortParam == "createdAt_asc") 1 else -1

                val filter = if (!search.isNullOrBlank()) {
                    Filters.or(
                        Filters.regex("fullName", search, "i"),
                        Filters.regex("phoneNumber", search)
                    )
                } else Filters.empty()

                val users = Collections.users
                    .find(filter)
                    .sort(Document("createdAt", sortOrder))
                    .skip(skip)
                    .limit(limit)
                    .toList()

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = users.map { it.toJsonElementStripped("passwordHash") },
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
                            user = userDoc.toJsonElementStripped("passwordHash"),
                            devices = devices.map { it.toJsonElement() }
                        ),
                        message = "User and devices retrieved"
                    )
                )
            }

            // ── GET /api/admin/stolen-reports ────────────────────────────
            get("/stolen-reports") {
                try { call.requireAdmin() } catch (e: IllegalAccessException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse<Nothing>(success = false, error = "Admin access required"))
                    return@get
                }

                val skip = call.request.queryParameters["skip"]?.toIntOrNull() ?: 0
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val statusFilter = call.request.queryParameters["status"]

                val filter = if (!statusFilter.isNullOrBlank()) Filters.eq("status", statusFilter) else Filters.empty()

                val reports = Collections.stolenReports
                    .find(filter)
                    .sort(Document("reportedAt", -1))
                    .skip(skip)
                    .limit(limit)
                    .toList()

                call.respond(HttpStatusCode.OK, ApiResponse(
                    success = true,
                    data = reports.map { it.toJsonElement() },
                    message = "Retrieved ${reports.size} stolen report(s)"
                ))
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
                val sortParam = call.request.queryParameters["sort"]
                val sortOrder = if (sortParam == "createdAt_asc") 1 else -1

                val devices = Collections.devices
                    .find()
                    .sort(Document("createdAt", sortOrder))
                    .skip(skip)
                    .limit(limit)
                    .toList()

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = devices.map { it.toJsonElement() },
                        message = "Retrieved ${devices.size} device(s)"
                    )
                )
            }

            // ── PUT /api/admin/devices/{deviceId}/status ─────────────────
            put("/devices/{deviceId}/status") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@put
                }

                val deviceId = call.parameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Missing deviceId")
                    )
                    return@put
                }

                val request = try {
                    call.receive<AdminStatusUpdateRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Invalid request body")
                    )
                    return@put
                }

                val validStatuses = listOf("ACTIVE", "STOLEN", "LOCKED", "LOST", "RECOVERED", "DEACTIVATED")
                if (request.status !in validStatuses) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(
                            success = false,
                            error = "Invalid status. Must be one of: ${validStatuses.joinToString()}"
                        )
                    )
                    return@put
                }

                val updateFields = Document("status", request.status)
                    .append("updatedAt", java.time.Instant.now().toString())

                val result = Collections.devices.updateOne(
                    Filters.eq("deviceId", deviceId),
                    Document("\$set", updateFields)
                )

                if (result.matchedCount == 0L) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Nothing>(success = false, error = "Device not found")
                    )
                    return@put
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Nothing>(
                        success = true,
                        message = "Device status updated to ${request.status}"
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
                        data = callerIds.map { it.toJsonElement() },
                        message = "Retrieved ${callerIds.size} caller ID entry/entries"
                    )
                )
            }

            // ── POST /api/admin/caller-ids ─────────────────────────────
            post("/caller-ids") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@post
                }

                val request = try {
                    call.receive<CallerIdCreateRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Invalid request body")
                    )
                    return@post
                }

                if (request.phoneNumber.isBlank() || request.name.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "phoneNumber and name are required")
                    )
                    return@post
                }

                val now = java.time.Instant.now().toString()
                val doc = Document()
                    .append("_id", request.id ?: ObjectId().toString())
                    .append("phoneNumber", request.phoneNumber)
                    .append("name", request.name)
                    .append("spamScore", request.spamScore.coerceIn(0, 100))
                    .append("reportCount", request.reportCount)
                    .append("category", request.category)
                    .append("lastUpdated", request.lastUpdated ?: now)

                Collections.callerIds.insertOne(doc)

                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse<Nothing>(
                        success = true,
                        message = "Caller ID entry created"
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
                        data = reports.map { it.toJsonElement() },
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

            // ── PUT /api/admin/stolen-reports/{id}/status ─────────────
            put("/stolen-reports/{id}/status") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@put
                }

                val reportId = call.parameters["id"]
                if (reportId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Missing report id")
                    )
                    return@put
                }

                val request = try {
                    call.receive<AdminStatusUpdateRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Invalid request body")
                    )
                    return@put
                }

                // Validate status value
                val validStatuses = listOf("PENDING", "VERIFIED", "ESCALATED", "RESOLVED")
                if (request.status !in validStatuses) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(
                            success = false,
                            error = "Invalid status. Must be one of: ${validStatuses.joinToString()}"
                        )
                    )
                    return@put
                }

                // Admin: no ownership check — update any report by ID
                val updateFields = Document("status", request.status)
                    .append("updatedAt", java.time.Instant.now().toString())

                val result = Collections.stolenReports.updateOne(
                    Filters.eq("_id", reportId),
                    Document("\$set", updateFields)
                )

                if (result.matchedCount == 0L) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Nothing>(success = false, error = "Stolen report not found")
                    )
                    return@put
                }

                // When resolving, optionally update device status back to ACTIVE
                if (request.status == "RESOLVED") {
                    val reportDoc = Collections.stolenReports
                        .find(Filters.eq("_id", reportId))
                        .toList()
                        .firstOrNull()
                    val deviceId = reportDoc?.getString("deviceId")
                    if (deviceId != null) {
                        Collections.devices.updateOne(
                            Filters.eq("deviceId", deviceId),
                            Document("\$set", Document("status", "ACTIVE"))
                        )
                    }
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Nothing>(
                        success = true,
                        message = "Stolen report status updated to ${request.status}"
                    )
                )
            }

            // ── PUT /api/admin/users/{userId}/status ─────────────────────
            put("/users/{userId}/status") {
                try { call.requireAdmin() } catch (e: IllegalAccessException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse<Nothing>(success = false, error = "Admin access required"))
                    return@put
                }

                val targetId = call.parameters["userId"]
                if (targetId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Nothing>(success = false, error = "Missing userId"))
                    return@put
                }

                val request = try { call.receive<AdminStatusUpdateRequest>() } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Nothing>(success = false, error = "Invalid request body"))
                    return@put
                }

                val validStatuses = listOf("ACTIVE", "BANNED", "SUSPENDED")
                if (request.status !in validStatuses) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Nothing>(success = false, error = "Invalid status. Must be one of: ${validStatuses.joinToString()}"))
                    return@put
                }

                val result = Collections.users.updateOne(
                    Filters.eq("_id", targetId),
                    Updates.combine(
                        Updates.set("status", request.status),
                        Updates.set("updatedAt", java.time.Instant.now().toString())
                    )
                )

                if (result.matchedCount == 0L) {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Nothing>(success = false, error = "User not found"))
                    return@put
                }

                call.respond(HttpStatusCode.OK, ApiResponse<Nothing>(success = true, message = "User status updated to ${request.status}"))
            }

            // ── DELETE /api/admin/users/{userId} ──────────────────────────
            delete("/users/{userId}") {
                try { call.requireAdmin() } catch (e: IllegalAccessException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse<Nothing>(success = false, error = "Admin access required"))
                    return@delete
                }

                val targetId = call.parameters["userId"]
                if (targetId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Nothing>(success = false, error = "Missing userId"))
                    return@delete
                }

                val userResult = Collections.users.deleteOne(Filters.eq("_id", targetId))
                if (userResult.deletedCount == 0L) {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Nothing>(success = false, error = "User not found"))
                    return@delete
                }

                val devicesDeleted = Collections.devices.deleteMany(Filters.eq("userId", targetId)).deletedCount
                val contactsDeleted = Collections.contacts.deleteMany(Filters.eq("userId", targetId)).deletedCount
                val reportsDeleted = Collections.stolenReports.deleteMany(Filters.eq("userId", targetId)).deletedCount
                runCatching { Collections.blockedNumbers.deleteMany(Filters.eq("userId", targetId)) }

                call.respond(HttpStatusCode.OK, ApiResponse(
                    success = true,
                    data = mapOf(
                        "devicesDeleted" to devicesDeleted,
                        "contactsDeleted" to contactsDeleted,
                        "reportsDeleted" to reportsDeleted
                    ),
                    message = "User and all associated data deleted"
                ))
            }

            // ── POST /api/admin/users/bulk-delete ─────────────────────────
            post("/users/bulk-delete") {
                try { call.requireAdmin() } catch (e: IllegalAccessException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse<Nothing>(success = false, error = "Admin access required"))
                    return@post
                }

                val request = try { call.receive<BulkDeleteRequest>() } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Nothing>(success = false, error = "Invalid request body"))
                    return@post
                }

                if (request.userIds.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Nothing>(success = false, error = "No user IDs provided"))
                    return@post
                }

                val ids = request.userIds.distinct()
                val usersDeleted = Collections.users.deleteMany(Filters.`in`("_id", ids)).deletedCount
                val devicesDeleted = Collections.devices.deleteMany(Filters.`in`("userId", ids)).deletedCount
                val contactsDeleted = Collections.contacts.deleteMany(Filters.`in`("userId", ids)).deletedCount
                val reportsDeleted = Collections.stolenReports.deleteMany(Filters.`in`("userId", ids)).deletedCount
                runCatching { Collections.blockedNumbers.deleteMany(Filters.`in`("userId", ids)) }

                call.respond(HttpStatusCode.OK, ApiResponse(
                    success = true,
                    data = mapOf(
                        "usersDeleted" to usersDeleted,
                        "devicesDeleted" to devicesDeleted,
                        "contactsDeleted" to contactsDeleted,
                        "reportsDeleted" to reportsDeleted
                    ),
                    message = "$usersDeleted user(s) and all associated data deleted"
                ))
            }

            // ── PUT /api/admin/sms-spam-reports/{id}/status ───────────────
            put("/sms-spam-reports/{id}/status") {
                try { call.requireAdmin() } catch (e: IllegalAccessException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse<Nothing>(success = false, error = "Admin access required"))
                    return@put
                }

                val reportId = call.parameters["id"]
                if (reportId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Nothing>(success = false, error = "Missing report id"))
                    return@put
                }

                val request = try { call.receive<AdminStatusUpdateRequest>() } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Nothing>(success = false, error = "Invalid request body"))
                    return@put
                }

                val validStatuses = listOf("PENDING", "REVIEWED", "DISMISSED")
                if (request.status !in validStatuses) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Nothing>(success = false, error = "Invalid status. Must be one of: ${validStatuses.joinToString()}"))
                    return@put
                }

                val result = Collections.smsSpamReports.updateOne(
                    Filters.eq("_id", reportId),
                    Updates.combine(
                        Updates.set("status", request.status),
                        Updates.set("updatedAt", java.time.Instant.now().toString())
                    )
                )

                if (result.matchedCount == 0L) {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Nothing>(success = false, error = "SMS spam report not found"))
                    return@put
                }

                call.respond(HttpStatusCode.OK, ApiResponse<Nothing>(success = true, message = "SMS spam report status updated to ${request.status}"))
            }

            // ── POST /api/admin/sms-spam-reports/{id}/promote ─────────────
            post("/sms-spam-reports/{id}/promote") {
                try { call.requireAdmin() } catch (e: IllegalAccessException) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse<Nothing>(success = false, error = "Admin access required"))
                    return@post
                }

                val reportId = call.parameters["id"]
                if (reportId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Nothing>(success = false, error = "Missing report id"))
                    return@post
                }

                val reportDoc = Collections.smsSpamReports.find(Filters.eq("_id", reportId)).toList().firstOrNull()
                if (reportDoc == null) {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Nothing>(success = false, error = "SMS spam report not found"))
                    return@post
                }

                val senderNumber = reportDoc.getString("senderNumber") ?: reportDoc.getString("phoneNumber") ?: ""
                if (senderNumber.isBlank()) {
                    call.respond(HttpStatusCode.UnprocessableEntity, ApiResponse<Nothing>(success = false, error = "Report has no sender number"))
                    return@post
                }

                val now = java.time.Instant.now().toString()
                val existing = Collections.callerIds.find(Filters.eq("phoneNumber", senderNumber)).toList().firstOrNull()
                if (existing != null) {
                    val newScore = maxOf(existing.getInteger("spamScore", 0), 80)
                    val newCount = existing.getInteger("reportCount", 0) + 1
                    Collections.callerIds.updateOne(
                        Filters.eq("phoneNumber", senderNumber),
                        Updates.combine(
                            Updates.set("spamScore", newScore),
                            Updates.set("reportCount", newCount),
                            Updates.set("category", "SPAM"),
                            Updates.set("lastUpdated", now)
                        )
                    )
                } else {
                    Collections.callerIds.insertOne(Document()
                        .append("_id", ObjectId().toString())
                        .append("phoneNumber", senderNumber)
                        .append("name", senderNumber)
                        .append("spamScore", 80)
                        .append("reportCount", 1)
                        .append("category", "SPAM")
                        .append("lastUpdated", now)
                    )
                }

                Collections.smsSpamReports.updateOne(
                    Filters.eq("_id", reportId),
                    Updates.combine(Updates.set("status", "REVIEWED"), Updates.set("updatedAt", now))
                )

                call.respond(HttpStatusCode.OK, ApiResponse(
                    success = true,
                    data = mapOf("senderNumber" to senderNumber, "promoted" to true),
                    message = "$senderNumber promoted to caller ID spam list"
                ))
            }

            // ── PUT /api/admin/profile ────────────────────────────────────
            put("/profile") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@put
                }

                val adminId = call.userId()

                val request = try {
                    call.receive<AdminProfileUpdateRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Invalid request body")
                    )
                    return@put
                }

                if (request.name.isBlank() || request.email.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Name and email are required")
                    )
                    return@put
                }

                // Check if another admin already uses this email
                val existingAdmin = Collections.adminUsers
                    .find(
                        Filters.and(
                            Filters.eq("email", request.email),
                            Filters.ne("_id", adminId)
                        )
                    )
                    .firstOrNull()
                if (existingAdmin != null) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiResponse<Nothing>(success = false, error = "Another admin already uses this email")
                    )
                    return@put
                }

                val result = Collections.adminUsers.updateOne(
                    Filters.eq("_id", adminId),
                    Updates.combine(
                        Updates.set("name", request.name),
                        Updates.set("email", request.email)
                    )
                )

                if (result.matchedCount == 0L) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Nothing>(success = false, error = "Admin user not found")
                    )
                    return@put
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Nothing>(
                        success = true,
                        message = "Admin profile updated"
                    )
                )
            }

            // ── PUT /api/admin/password ───────────────────────────────────
            put("/password") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@put
                }

                val adminId = call.userId()

                val request = try {
                    call.receive<AdminPasswordUpdateRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Invalid request body")
                    )
                    return@put
                }

                if (request.currentPassword.isBlank() || request.newPassword.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Current and new passwords are required")
                    )
                    return@put
                }

                if (request.newPassword.length < 8) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "New password must be at least 8 characters")
                    )
                    return@put
                }

                // Fetch admin user document
                val adminDoc = Collections.adminUsers
                    .find(Filters.eq("_id", adminId))
                    .firstOrNull()

                if (adminDoc == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Nothing>(success = false, error = "Admin user not found")
                    )
                    return@put
                }

                // Verify current password with BCrypt
                val storedHash = adminDoc.getString("passwordHash")
                val verified = BCrypt.verifyer()
                    .verify(request.currentPassword.toCharArray(), storedHash)
                    .verified

                if (!verified) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiResponse<Nothing>(success = false, error = "Current password is incorrect")
                    )
                    return@put
                }

                // Hash and store new password
                val newHash = BCrypt.withDefaults()
                    .hashToString(12, request.newPassword.toCharArray())

                Collections.adminUsers.updateOne(
                    Filters.eq("_id", adminId),
                    Updates.set("passwordHash", newHash)
                )

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Nothing>(
                        success = true,
                        message = "Password updated successfully"
                    )
                )
            }

            // ── GET /api/admin/users/{userId}/trust ───────────────────────
            get("/users/{userId}/trust") {
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

                val trustScore = userDoc.getInteger("trustScore", 0)
                val trustLevel = userDoc.getString("trustLevel") ?: "NEW"

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = com.trucaller.backend.data.models.TrustResponse(userId = userId, trustScore = trustScore, trustLevel = trustLevel),
                        message = "Trust info retrieved"
                    )
                )
            }

            // ── PUT /api/admin/users/{userId}/trust ───────────────────────
            put("/users/{userId}/trust") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@put
                }

                val userId = call.parameters["userId"]
                if (userId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Missing userId")
                    )
                    return@put
                }

                val request = try {
                    call.receive<TrustUpdateRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "Invalid request body")
                    )
                    return@put
                }

                val updates = mutableListOf<org.bson.conversions.Bson>()
                request.trustScore?.let {
                    val clamped = it.coerceIn(0, 100)
                    updates.add(Updates.set("trustScore", clamped))
                    // Auto-derive trustLevel from score
                    val level = when {
                        clamped >= 100 -> "AUTHORITY"
                        clamped >= 80 -> "VERIFIED"
                        clamped >= 50 -> "TRUSTED"
                        clamped >= 20 -> "BASIC"
                        else -> "NEW"
                    }
                    updates.add(Updates.set("trustLevel", level))
                }
                request.trustLevel?.let {
                    val validLevels = listOf("NEW", "BASIC", "TRUSTED", "VERIFIED", "AUTHORITY")
                    if (it in validLevels) {
                        updates.add(Updates.set("trustLevel", it))
                    }
                }

                if (updates.isEmpty()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Nothing>(success = false, error = "No fields to update")
                    )
                    return@put
                }

                val result = Collections.users.updateOne(
                    Filters.eq("_id", userId),
                    Updates.combine(updates)
                )

                if (result.matchedCount == 0L) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Nothing>(success = false, error = "User not found")
                    )
                    return@put
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Nothing>(
                        success = true,
                        message = "Trust level updated"
                    )
                )
            }

            // ── GET /api/admin/alarm-logs ──────────────────────────────────
            get("/alarm-logs") {
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
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

                val logs = Collections.alarmLogs
                    .find()
                    .sort(org.bson.Document("triggeredAt", -1))
                    .skip(skip)
                    .limit(limit)
                    .toList()

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = logs.map { it.toJsonElement() },
                        message = "Retrieved ${logs.size} alarm log(s)"
                    )
                )
            }

            // ── GET /api/admin/audit/logins ────────────────────────────────
            get("/audit/logins") {
                try {
                    call.requireAdmin()
                } catch (e: IllegalAccessException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Admin access required")
                    )
                    return@get
                }

                val phone = call.request.queryParameters["phone"]
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 500)
                val skip = call.request.queryParameters["skip"]?.toIntOrNull() ?: 0

                val filter = if (phone != null) Filters.eq("phone", phone) else Filters.empty()

                val logs = Collections.loginAuditLog
                    .find(filter)
                    .sort(org.bson.Document("timestamp", -1))
                    .skip(skip)
                    .limit(limit)
                    .toList()
                    .map { doc ->
                        LoginAuditEntry(
                            phone = doc.getString("phone") ?: "",
                            ip = doc.getString("ip") ?: "",
                            success = doc.getBoolean("success", false),
                            reason = doc.getString("reason") ?: "",
                            timestamp = doc["timestamp"]?.toString() ?: ""
                        )
                    }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = LoginAuditPage(logs = logs, count = logs.size)
                    )
                )
            }
        }
    }
}
