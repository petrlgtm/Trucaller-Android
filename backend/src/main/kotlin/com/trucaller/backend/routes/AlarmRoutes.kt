package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.trucaller.backend.auth.requireAdmin
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import com.trucaller.backend.auth.userId
import com.trucaller.backend.auth.userRole
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.trucaller.backend.service.FcmService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

// ── Request DTO ──────────────────────────────────────────────────────────────

@Serializable
data class AlarmTriggerRequest(
    val deviceId: String,
    val type: String,
    val notes: String? = null
)

@Serializable
data class AlarmLogResultRequest(
    val result: String,
    val notes: String? = null
)

// ── Routes ───────────────────────────────────────────────────────────────────

/**
 * Registers alarm routes:
 *
 * - `POST /api/alarms/trigger`          — trigger an alarm on a device (authenticated)
 * - `GET  /api/alarms/logs/{deviceId}`  — get alarm logs for a device (authenticated)
 * - `GET  /api/admin/alarm-logs`        — get ALL alarm logs (admin only)
 */
fun Route.alarmRoutes() {

    // ── Authenticated endpoints ──────────────────────────────────────────
    authenticate("auth-jwt") {

        // POST /api/alarms/trigger
        post("/api/alarms/trigger") {
            val request = try {
                call.receive<AlarmTriggerRequest>()
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
            val role = call.userRole()

            // Look up user name from users collection
            val userDoc = Collections.users
                .find(Filters.eq("_id", userId))
                .firstOrNull()
            val userName = userDoc?.getString("fullName") ?: "Unknown"

            val logId = ObjectId().toString()
            val now = Instant.now().toString()

            val alarmDoc = Document()
                .append("_id", logId)
                .append("deviceId", request.deviceId)
                .append("triggeredBy", userId)
                .append("triggeredByName", userName)
                .append("triggeredByRole", role)
                .append("triggeredAt", now)
                .append("type", request.type)
                .append("result", "PENDING")
                .append("notes", request.notes)

            Collections.alarmLogs.insertOne(alarmDoc)

            // ── Device ownership check ────────────────────────────────────
            val deviceDoc = Collections.devices
                .find(Filters.eq("deviceId", request.deviceId))
                .firstOrNull()

            if (deviceDoc != null && deviceDoc.getString("userId") != userId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse<Nothing>(success = false, error = "Not authorized for this device")
                )
                return@post
            }

            // ── Send FCM push to the target device ──────────────────────
            val fcmToken = deviceDoc?.getString("fcmToken")

            if (fcmToken.isNullOrBlank()) {
                // No token available — mark alarm as FAILED
                Collections.alarmLogs.updateOne(
                    Filters.eq("_id", logId),
                    Document("\$set", Document("result", "FAILED"))
                )
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse<Nothing>(
                        success = true,
                        message = "Alarm logged but push failed: no FCM token for device"
                    )
                )
                return@post
            }

            val pushSent = FcmService.sendPush(
                fcmToken,
                mapOf("action" to request.type)
            )

            if (!pushSent) {
                // Push delivery failed — mark alarm as FAILED
                Collections.alarmLogs.updateOne(
                    Filters.eq("_id", logId),
                    Document("\$set", Document("result", "FAILED"))
                )
            }
            // If push succeeded, result stays "PENDING" (device will confirm)

            call.respond(
                HttpStatusCode.Created,
                ApiResponse<Nothing>(
                    success = true,
                    message = if (pushSent)
                        "Alarm triggered successfully"
                    else
                        "Alarm logged but push delivery failed"
                )
            )
        }

        // GET /api/alarms/logs/{deviceId}
        get("/api/alarms/logs/{deviceId}") {
            val currentUserId = call.userId()
            val deviceId = call.parameters["deviceId"]
            if (deviceId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Missing deviceId"
                    )
                )
                return@get
            }

            // Ownership check
            val deviceDoc = Collections.devices
                .find(Filters.eq("deviceId", deviceId))
                .firstOrNull()
            if (deviceDoc != null && deviceDoc.getString("userId") != currentUserId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse<Nothing>(success = false, error = "Access denied")
                )
                return@get
            }

            val logs = Collections.alarmLogs
                .find(Filters.eq("deviceId", deviceId))
                .toList()

            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = logs.map { it.toJson() },
                    message = "Alarm logs retrieved"
                )
            )
        }

        // PUT /api/alarms/logs/{logId}/result
        put("/api/alarms/logs/{logId}/result") {
            val logId = call.parameters["logId"]
            if (logId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Missing logId"
                    )
                )
                return@put
            }

            val request = try {
                call.receive<AlarmLogResultRequest>()
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

            val validResults = listOf("SUCCESS", "FAILED", "PARTIAL")
            if (request.result !in validResults) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Invalid result. Must be one of: ${validResults.joinToString()}"
                    )
                )
                return@put
            }

            val updateDoc = Document()
                .append("result", request.result)
                .append("completedAt", Instant.now().toString())
            if (request.notes != null) {
                updateDoc.append("resultNotes", request.notes)
            }

            val updateResult = Collections.alarmLogs.updateOne(
                Filters.eq("_id", logId),
                Document("\$set", updateDoc)
            )

            if (updateResult.matchedCount == 0L) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Alarm log not found"
                    )
                )
                return@put
            }

            call.respond(
                HttpStatusCode.OK,
                ApiResponse<Nothing>(
                    success = true,
                    message = "Alarm log result updated to ${request.result}"
                )
            )
        }

        // GET /api/admin/alarm-logs  (admin only)
        get("/api/admin/alarm-logs") {
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

            val logs = Collections.alarmLogs
                .find()
                .toList()

            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = logs.map { it.toJson() },
                    message = "All alarm logs retrieved"
                )
            )
        }
    }
}
