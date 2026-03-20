package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.UpdateOptions
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

// ── Request / Response DTOs ─────────────────────────────────────────────

@Serializable
data class DeviceRegisterRequest(
    val model: String,
    val manufacturer: String,
    val osVersion: String,
    val deviceId: String,
    val fcmToken: String? = null,
    val lastIp: String? = null,
    val isp: String? = null,
    val city: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val networkType: String? = null
)

@Serializable
data class DeviceResponse(
    val id: String,
    val userId: String,
    val model: String,
    val manufacturer: String,
    val osVersion: String,
    val deviceId: String,
    val status: String,
    val lastIp: String,
    val lastSeen: String,
    val registeredAt: String,
    val fcmToken: String? = null
)

@Serializable
data class FcmTokenUpdateRequest(
    val deviceId: String,
    val fcmToken: String
)

@Serializable
data class IpLogResponse(
    val id: String,
    val deviceId: String,
    val ipAddress: String,
    val isp: String,
    val city: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val networkType: String,
    val timestamp: String
)

// ── Routes ──────────────────────────────────────────────────────────────

/**
 * Registers all device-management routes under `/api/devices` on the Ktor [Route] receiver.
 * All endpoints require JWT authentication.
 */
fun Route.deviceRoutes() {
    authenticate("auth-jwt") {
        route("/api/devices") {
            registerDevice()
            updateFcmToken()
            getDevicesByUser()
            getIpLogsByDevice()
        }
    }
}

// ── POST /api/devices/register ──────────────────────────────────────────

private fun Route.registerDevice() {
    post("/register") {
        val userId = call.userId()

        val request = try {
            call.receive<DeviceRegisterRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body")
            )
            return@post
        }

        val headerIp = call.request.header("X-Forwarded-For")
            ?.split(",")?.firstOrNull()?.trim()
            ?: call.request.local.remoteHost

        // Use client-provided IP if available, fall back to X-Forwarded-For header
        val deviceIp = request.lastIp ?: headerIp

        val now = Instant.now().toString()

        // Upsert device by (userId, deviceId)
        Collections.devices.updateOne(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("deviceId", request.deviceId)
            ),
            Document("\$set", Document()
                .append("model", request.model)
                .append("manufacturer", request.manufacturer)
                .append("osVersion", request.osVersion)
                .append("deviceId", request.deviceId)
                .append("userId", userId)
                .append("status", "ACTIVE")
                .append("lastIp", deviceIp)
                .append("lastSeen", now)
                .append("fcmToken", request.fcmToken)
            ).append("\$setOnInsert", Document("registeredAt", now)),
            UpdateOptions().upsert(true)
        )

        // Create IpLog entry using client-provided geolocation when available
        val ipLogDoc = Document()
            .append("_id", ObjectId().toString())
            .append("deviceId", request.deviceId)
            .append("ipAddress", deviceIp)
            .append("isp", request.isp ?: "unknown")
            .append("city", request.city ?: "unknown")
            .append("country", request.country ?: "unknown")
            .append("latitude", request.latitude ?: 0.0)
            .append("longitude", request.longitude ?: 0.0)
            .append("networkType", request.networkType ?: "unknown")
            .append("timestamp", now)

        Collections.ipLogs.insertOne(ipLogDoc)

        // Retrieve the upserted/updated device to return
        val deviceDoc = Collections.devices
            .find(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("deviceId", request.deviceId)
                )
            )
            .toList()
            .firstOrNull()

        if (deviceDoc == null) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiResponse<Unit>(
                    success = false,
                    error = "Failed to retrieve device after registration."
                )
            )
            return@post
        }

        val device = deviceDoc.toDeviceResponse()

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = device,
                message = "Device registered successfully."
            )
        )
    }
}

// ── PUT /api/devices/fcm-token ──────────────────────────────────────────

private fun Route.updateFcmToken() {
    put("/fcm-token") {
        val userId = call.userId()

        val request = try {
            call.receive<FcmTokenUpdateRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body")
            )
            return@put
        }

        // Verify the device belongs to the requesting user
        val deviceDoc = Collections.devices
            .find(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("deviceId", request.deviceId)
                )
            )
            .firstOrNull()

        if (deviceDoc == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(
                    success = false,
                    error = "Device not found or does not belong to you."
                )
            )
            return@put
        }

        // Update the FCM token
        Collections.devices.updateOne(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("deviceId", request.deviceId)
            ),
            Document("\$set", Document("fcmToken", request.fcmToken))
        )

        // Retrieve the updated device to return
        val updatedDoc = Collections.devices
            .find(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("deviceId", request.deviceId)
                )
            )
            .firstOrNull()

        if (updatedDoc == null) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiResponse<Unit>(
                    success = false,
                    error = "Failed to retrieve device after FCM token update."
                )
            )
            return@put
        }

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = updatedDoc.toDeviceResponse(),
                message = "FCM token updated successfully."
            )
        )
    }
}

// ── GET /api/devices/{userId} ───────────────────────────────────────────

private fun Route.getDevicesByUser() {
    get("/{userId}") {
        val currentUserId = call.userId()
        val requestedUserId = call.parameters["userId"]

        if (requestedUserId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(
                    success = false,
                    error = "Path parameter 'userId' is required."
                )
            )
            return@get
        }

        if (currentUserId != requestedUserId) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(
                    success = false,
                    error = "Access denied"
                )
            )
            return@get
        }

        val userId = requestedUserId

        val docs = Collections.devices
            .find(Filters.eq("userId", userId))
            .toList()

        val devices = docs.map { it.toDeviceResponse() }

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = devices,
                message = "Retrieved ${devices.size} device(s)."
            )
        )
    }
}

// ── GET /api/devices/{deviceId}/ip-logs ─────────────────────────────────

private fun Route.getIpLogsByDevice() {
    get("/{deviceId}/ip-logs") {
        val currentUserId = call.userId()
        val deviceId = call.parameters["deviceId"]

        if (deviceId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(
                    success = false,
                    error = "Path parameter 'deviceId' is required."
                )
            )
            return@get
        }

        // Ownership check: verify the device belongs to the requesting user
        val deviceDoc = Collections.devices
            .find(Filters.eq("deviceId", deviceId))
            .firstOrNull()

        if (deviceDoc != null && deviceDoc.getString("userId") != currentUserId) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "Access denied")
            )
            return@get
        }

        val docs = Collections.ipLogs
            .find(Filters.eq("deviceId", deviceId))
            .sort(Sorts.descending("timestamp"))
            .toList()

        val logs = docs.map { doc ->
            IpLogResponse(
                id = doc.getString("_id") ?: doc.getObjectId("_id").toString(),
                deviceId = doc.getString("deviceId"),
                ipAddress = doc.getString("ipAddress"),
                isp = doc.getString("isp") ?: "unknown",
                city = doc.getString("city") ?: "unknown",
                country = doc.getString("country") ?: "unknown",
                latitude = doc.getDouble("latitude") ?: 0.0,
                longitude = doc.getDouble("longitude") ?: 0.0,
                networkType = doc.getString("networkType") ?: "unknown",
                timestamp = doc.getString("timestamp")
            )
        }

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = logs,
                message = "Retrieved ${logs.size} IP log(s)."
            )
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────

/**
 * Maps a MongoDB [Document] from the `devices` collection to a [DeviceResponse].
 */
private fun Document.toDeviceResponse(): DeviceResponse {
    return DeviceResponse(
        id = getString("_id") ?: getObjectId("_id").toString(),
        userId = getString("userId"),
        model = getString("model"),
        manufacturer = getString("manufacturer"),
        osVersion = getString("osVersion"),
        deviceId = getString("deviceId"),
        status = getString("status") ?: "ACTIVE",
        lastIp = getString("lastIp") ?: "",
        lastSeen = getString("lastSeen") ?: "",
        registeredAt = getString("registeredAt") ?: "",
        fcmToken = getString("fcmToken")
    )
}
