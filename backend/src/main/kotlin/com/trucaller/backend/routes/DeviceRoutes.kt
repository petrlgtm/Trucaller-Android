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
import com.trucaller.backend.service.IpGeolocationService
import com.trucaller.backend.data.models.NearbyDevice
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
    val timestamp: String,
    val startTime: String = timestamp,
    val lastSeen: String = timestamp,
    val nearbyDevices: List<NearbyDevice> = emptyList()
)

@Serializable
data class ForensicsResponse(
    val deviceId: String,
    val ipLogs: List<IpLogResponse>,
    val uniqueIsps: List<String>,
    val uniqueCities: List<String>,
    val uniqueCountries: List<String>,
    val totalLogs: Int
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
            recoverDevice()
            getDevicesByUser()
            getIpLogsByDevice()
            getDeviceForensics()
        }
    }
}

@Serializable
data class DeviceRecoverRequest(
    val status: String
)

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

        // Resolve geolocation: prefer client-provided data, fall back to IP lookup
        val geo = if (request.isp != null && request.city != null && request.country != null) {
            null // client already provided full geo data
        } else {
            IpGeolocationService.resolve(deviceIp)
        }

        val resolvedCity = request.city ?: geo?.city ?: "unknown"
        val resolvedCountry = request.country ?: geo?.country ?: "unknown"
        val resolvedIsp = request.isp ?: geo?.isp ?: "unknown"
        val resolvedLat = request.latitude ?: geo?.lat ?: 0.0
        val resolvedLon = request.longitude ?: geo?.lon ?: 0.0
        val resolvedNetwork = request.networkType ?: "unknown"

        // Check if the latest log for this device is at the same location
        val latestLog = Collections.ipLogs
            .find(Filters.eq("deviceId", request.deviceId))
            .sort(Sorts.descending("startTime"))
            .limit(1)
            .firstOrNull()

        val sameLocation = latestLog != null &&
            (latestLog.getString("city") ?: "") == resolvedCity &&
            (latestLog.getString("country") ?: "") == resolvedCountry

        if (sameLocation && latestLog != null) {
            // Same location — just update lastSeen timestamp
            val logId = latestLog.getString("_id") ?: latestLog.getObjectId("_id").toString()
            Collections.ipLogs.updateOne(
                Filters.eq("_id", logId),
                Document("\$set", Document()
                    .append("lastSeen", now)
                    .append("ipAddress", deviceIp)
                    .append("networkType", resolvedNetwork)
                )
            )
        } else {
            // New location — create a new log entry
            val ipLogDoc = Document()
                .append("_id", ObjectId().toString())
                .append("deviceId", request.deviceId)
                .append("ipAddress", deviceIp)
                .append("isp", resolvedIsp)
                .append("city", resolvedCity)
                .append("country", resolvedCountry)
                .append("latitude", resolvedLat)
                .append("longitude", resolvedLon)
                .append("networkType", resolvedNetwork)
                .append("timestamp", now)
                .append("startTime", now)
                .append("lastSeen", now)

            Collections.ipLogs.insertOne(ipLogDoc)
        }

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

// ── PUT /api/devices/{deviceId}/recover ──────────────────────────────────

private fun Route.recoverDevice() {
    put("/{deviceId}/recover") {
        val userId = call.userId()
        val deviceId = call.parameters["deviceId"]

        if (deviceId.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Missing deviceId")
            )
            return@put
        }

        // Verify the device belongs to the requesting user
        val deviceDoc = Collections.devices
            .find(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("deviceId", deviceId)
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

        // Update device status to ACTIVE
        Collections.devices.updateOne(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("deviceId", deviceId)
            ),
            Document("\$set", Document("status", "ACTIVE"))
        )

        // Resolve all pending stolen reports for this device
        Collections.stolenReports.updateMany(
            Filters.and(
                Filters.eq("deviceId", deviceId),
                Filters.ne("status", "RESOLVED")
            ),
            Document("\$set", Document("status", "RESOLVED"))
        )

        call.respond(
            HttpStatusCode.OK,
            ApiResponse<Nothing>(
                success = true,
                message = "Device recovered and stolen reports resolved"
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

        if (deviceDoc == null || deviceDoc.getString("userId") != currentUserId) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "Access denied")
            )
            return@get
        }

        val docs = Collections.ipLogs
            .find(Filters.eq("deviceId", deviceId))
            .sort(Sorts.descending("startTime"))
            .toList()

        val logs = docs.map { doc ->
            val ts = doc.getString("timestamp") ?: doc.getString("startTime") ?: ""
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
                timestamp = ts,
                startTime = doc.getString("startTime") ?: ts,
                lastSeen = doc.getString("lastSeen") ?: ts
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

// ── GET /api/devices/{deviceId}/forensics ────────────────────────────

private fun Route.getDeviceForensics() {
    get("/{deviceId}/forensics") {
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

        if (deviceDoc == null || deviceDoc.getString("userId") != currentUserId) {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiResponse<Unit>(success = false, error = "Access denied")
            )
            return@get
        }

        // Fetch all IP logs for this device, sorted newest-first
        val docs = Collections.ipLogs
            .find(Filters.eq("deviceId", deviceId))
            .sort(Sorts.descending("startTime"))
            .toList()

        val logs = docs.map { doc ->
            val ts = doc.getString("timestamp") ?: doc.getString("startTime") ?: ""
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
                timestamp = ts,
                startTime = doc.getString("startTime") ?: ts,
                lastSeen = doc.getString("lastSeen") ?: ts
            )
        }

        val forensics = ForensicsResponse(
            deviceId = deviceId,
            ipLogs = logs,
            uniqueIsps = logs.map { it.isp }.distinct().filter { it != "unknown" },
            uniqueCities = logs.map { it.city }.distinct().filter { it != "unknown" },
            uniqueCountries = logs.map { it.country }.distinct().filter { it != "unknown" },
            totalLogs = logs.size
        )

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = forensics,
                message = "Device forensics retrieved."
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
