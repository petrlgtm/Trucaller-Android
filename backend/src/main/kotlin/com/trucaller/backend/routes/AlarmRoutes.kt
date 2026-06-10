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

private val VALID_ALARM_TYPES = setOf("REMOTE_ALARM", "LOCATION_REQUEST", "LOCK_DEVICE")

/**
 * Registers alarm routes:
 *
 * - `POST /api/alarms/trigger`              — trigger an alarm on a device (authenticated)
 * - `GET  /api/alarms/logs/{deviceId}`      — get alarm logs for a device (authenticated)
 * - `PUT  /api/alarms/logs/{logId}/result`  — report execution result from target device
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
                    ApiResponse<Nothing>(success = false, error = "Invalid request body")
                )
                return@post
            }

            // Validate alarm type
            if (request.type !in VALID_ALARM_TYPES) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Invalid type. Must be one of: ${VALID_ALARM_TYPES.joinToString()}"
                    )
                )
                return@post
            }

            val userId = call.userId()
            val role = call.userRole()

            // Device must exist
            val deviceDoc = Collections.devices
                .find(Filters.eq("deviceId", request.deviceId))
                .firstOrNull()

            if (deviceDoc == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Nothing>(success = false, error = "Device not found")
                )
                return@post
            }

            // Ownership check — only device owner or admin may trigger
            if (deviceDoc.getString("userId") != userId && role != "SUPER_ADMIN" && role != "MODERATOR") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse<Nothing>(success = false, error = "Not authorized for this device")
                )
                return@post
            }

            // Look up user name
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

            // ── Send FCM push to the target device ──────────────────────
            val fcmToken = deviceDoc.getString("fcmToken")

            if (fcmToken.isNullOrBlank()) {
                Collections.alarmLogs.updateOne(
                    Filters.eq("_id", logId),
                    Document("\$set", Document("result", "FAILED"))
                )
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(
                        success = true,
                        data = mapOf("logId" to logId),
                        message = "Alarm logged but push failed: no FCM token for device"
                    )
                )
                return@post
            }

            // Include logId in FCM payload so the device can report execution result back
            val pushSent = FcmService.sendPush(
                fcmToken,
                mapOf("action" to request.type, "logId" to logId)
            )

            if (!pushSent) {
                Collections.alarmLogs.updateOne(
                    Filters.eq("_id", logId),
                    Document("\$set", Document("result", "FAILED"))
                )
            }

            call.respond(
                HttpStatusCode.Created,
                ApiResponse(
                    success = true,
                    data = mapOf("logId" to logId),
                    message = if (pushSent) "Alarm triggered successfully"
                               else "Alarm logged but push delivery failed"
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
            val currentUserId = call.userId()
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

            val validResults = listOf("SUCCESS", "FAILED")
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

            // Ownership check: caller must own the device the alarm log belongs to
            val alarmLog = Collections.alarmLogs.find(Filters.eq("_id", logId)).firstOrNull()
            if (alarmLog != null) {
                val alarmDeviceId = alarmLog.getString("deviceId")
                if (alarmDeviceId != null) {
                    val alarmDevice = Collections.devices.find(Filters.eq("deviceId", alarmDeviceId)).firstOrNull()
                    if (alarmDevice != null && alarmDevice.getString("userId") != currentUserId) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ApiResponse<Nothing>(success = false, error = "Not authorized to update this alarm log")
                        )
                        return@put
                    }
                }
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

    }
}
