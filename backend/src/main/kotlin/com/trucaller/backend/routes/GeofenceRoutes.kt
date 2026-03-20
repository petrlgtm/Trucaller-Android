package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
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

// ── Request DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class CreateGeofenceRequest(
    val deviceId: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 200
)

@Serializable
data class UpdateGeofenceRequest(
    val label: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Int? = null,
    val isActive: Boolean? = null
)

@Serializable
data class GeofenceEventRequest(
    val geofenceId: String,
    val deviceId: String,
    val transitionType: String,
    val latitude: Double,
    val longitude: Double
)

// ── Routes ───────────────────────────────────────────────────────────────────

/**
 * Registers geofence routes:
 *
 * - `POST   /api/geofences`                      — create a geofence (authenticated)
 * - `GET    /api/geofences/{deviceId}`            — list geofences for a device (authenticated)
 * - `GET    /api/geofences/detail/{id}`           — get a single geofence (authenticated)
 * - `PUT    /api/geofences/{id}`                  — update a geofence (authenticated)
 * - `DELETE /api/geofences/{id}`                  — delete a geofence (authenticated)
 * - `POST   /api/geofence-events`                 — record a geofence event (authenticated)
 * - `GET    /api/geofence-events/{deviceId}`      — list events for a device (authenticated)
 * - `GET    /api/geofence-events/fence/{geofenceId}` — list events for a geofence (authenticated)
 */
fun Route.geofenceRoutes() {

    // ── Authenticated endpoints ──────────────────────────────────────────
    authenticate("auth-jwt") {

        // POST /api/geofences
        post("/api/geofences") {
            val request = try {
                call.receive<CreateGeofenceRequest>()
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

            // Verify device ownership
            val deviceDoc = Collections.devices
                .find(Filters.eq("deviceId", request.deviceId))
                .firstOrNull()
            if (deviceDoc == null || deviceDoc.getString("userId") != userId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse<Nothing>(success = false, error = "Access denied")
                )
                return@post
            }

            val geofenceId = ObjectId().toString()
            val now = Instant.now().toString()

            val doc = Document()
                .append("_id", geofenceId)
                .append("deviceId", request.deviceId)
                .append("label", request.label)
                .append("latitude", request.latitude)
                .append("longitude", request.longitude)
                .append("radiusMeters", request.radiusMeters)
                .append("isActive", true)
                .append("createdAt", now)
                .append("triggeredCount", 0)

            Collections.geofences.insertOne(doc)

            call.respond(
                HttpStatusCode.Created,
                ApiResponse(
                    success = true,
                    data = doc.toJson(),
                    message = "Geofence created successfully"
                )
            )
        }

        // GET /api/geofences/{deviceId}
        get("/api/geofences/{deviceId}") {
            val userId = call.userId()
            val deviceId = call.parameters["deviceId"]
            if (deviceId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(success = false, error = "Missing deviceId")
                )
                return@get
            }

            // Verify device ownership
            val deviceDoc = Collections.devices
                .find(Filters.eq("deviceId", deviceId))
                .firstOrNull()
            if (deviceDoc == null || deviceDoc.getString("userId") != userId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse<Nothing>(success = false, error = "Access denied")
                )
                return@get
            }

            val geofences = Collections.geofences
                .find(Filters.eq("deviceId", deviceId))
                .toList()

            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = geofences.map { it.toJson() },
                    message = "Geofences retrieved"
                )
            )
        }

        // GET /api/geofences/detail/{id}
        get("/api/geofences/detail/{id}") {
            val userId = call.userId()
            val geofenceId = call.parameters["id"]
            if (geofenceId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(success = false, error = "Missing geofence id")
                )
                return@get
            }

            val doc = Collections.geofences
                .find(Filters.eq("_id", geofenceId))
                .firstOrNull()

            if (doc == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Nothing>(success = false, error = "Geofence not found")
                )
                return@get
            }

            // Verify device ownership
            val deviceDoc = Collections.devices
                .find(Filters.eq("deviceId", doc.getString("deviceId")))
                .firstOrNull()
            if (deviceDoc != null && deviceDoc.getString("userId") != userId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse<Nothing>(success = false, error = "Access denied")
                )
                return@get
            }

            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = doc.toJson(),
                    message = "Geofence retrieved"
                )
            )
        }

        // PUT /api/geofences/{id}
        put("/api/geofences/{id}") {
            val userId = call.userId()
            val geofenceId = call.parameters["id"]
            if (geofenceId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(success = false, error = "Missing geofence id")
                )
                return@put
            }

            val request = try {
                call.receive<UpdateGeofenceRequest>()
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(success = false, error = "Invalid request body")
                )
                return@put
            }

            // Fetch existing geofence
            val existing = Collections.geofences
                .find(Filters.eq("_id", geofenceId))
                .firstOrNull()
            if (existing == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Nothing>(success = false, error = "Geofence not found")
                )
                return@put
            }

            // Verify device ownership
            val deviceDoc = Collections.devices
                .find(Filters.eq("deviceId", existing.getString("deviceId")))
                .firstOrNull()
            if (deviceDoc != null && deviceDoc.getString("userId") != userId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse<Nothing>(success = false, error = "Access denied")
                )
                return@put
            }

            val updateDoc = Document()
            request.label?.let { updateDoc.append("label", it) }
            request.latitude?.let { updateDoc.append("latitude", it) }
            request.longitude?.let { updateDoc.append("longitude", it) }
            request.radiusMeters?.let { updateDoc.append("radiusMeters", it) }
            request.isActive?.let { updateDoc.append("isActive", it) }

            if (updateDoc.isEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(success = false, error = "No fields to update")
                )
                return@put
            }

            Collections.geofences.updateOne(
                Filters.eq("_id", geofenceId),
                Document("\$set", updateDoc)
            )

            call.respond(
                HttpStatusCode.OK,
                ApiResponse<Nothing>(
                    success = true,
                    message = "Geofence updated successfully"
                )
            )
        }

        // DELETE /api/geofences/{id}
        delete("/api/geofences/{id}") {
            val userId = call.userId()
            val geofenceId = call.parameters["id"]
            if (geofenceId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(success = false, error = "Missing geofence id")
                )
                return@delete
            }

            // Fetch existing geofence
            val existing = Collections.geofences
                .find(Filters.eq("_id", geofenceId))
                .firstOrNull()
            if (existing == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Nothing>(success = false, error = "Geofence not found")
                )
                return@delete
            }

            // Verify device ownership
            val deviceDoc = Collections.devices
                .find(Filters.eq("deviceId", existing.getString("deviceId")))
                .firstOrNull()
            if (deviceDoc != null && deviceDoc.getString("userId") != userId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse<Nothing>(success = false, error = "Access denied")
                )
                return@delete
            }

            Collections.geofences.deleteOne(Filters.eq("_id", geofenceId))

            // Also delete associated events
            Collections.geofenceEvents.deleteMany(Filters.eq("geofenceId", geofenceId))

            call.respond(
                HttpStatusCode.OK,
                ApiResponse<Nothing>(
                    success = true,
                    message = "Geofence and associated events deleted"
                )
            )
        }

        // POST /api/geofence-events
        post("/api/geofence-events") {
            val request = try {
                call.receive<GeofenceEventRequest>()
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(success = false, error = "Invalid request body")
                )
                return@post
            }

            val validTransitions = listOf("ENTER", "EXIT", "DWELL")
            if (request.transitionType !in validTransitions) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Invalid transitionType. Must be one of: ${validTransitions.joinToString()}"
                    )
                )
                return@post
            }

            val eventId = ObjectId().toString()
            val now = Instant.now().toString()

            val eventDoc = Document()
                .append("_id", eventId)
                .append("geofenceId", request.geofenceId)
                .append("deviceId", request.deviceId)
                .append("transitionType", request.transitionType)
                .append("latitude", request.latitude)
                .append("longitude", request.longitude)
                .append("timestamp", now)

            Collections.geofenceEvents.insertOne(eventDoc)

            // Increment triggered count on the geofence
            Collections.geofences.updateOne(
                Filters.eq("_id", request.geofenceId),
                Document("\$inc", Document("triggeredCount", 1))
            )

            call.respond(
                HttpStatusCode.Created,
                ApiResponse(
                    success = true,
                    data = eventDoc.toJson(),
                    message = "Geofence event recorded"
                )
            )
        }

        // GET /api/geofence-events/{deviceId}
        get("/api/geofence-events/{deviceId}") {
            val userId = call.userId()
            val deviceId = call.parameters["deviceId"]
            if (deviceId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(success = false, error = "Missing deviceId")
                )
                return@get
            }

            // Verify device ownership
            val deviceDoc = Collections.devices
                .find(Filters.eq("deviceId", deviceId))
                .firstOrNull()
            if (deviceDoc == null || deviceDoc.getString("userId") != userId) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiResponse<Nothing>(success = false, error = "Access denied")
                )
                return@get
            }

            val events = Collections.geofenceEvents
                .find(Filters.eq("deviceId", deviceId))
                .toList()

            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = events.map { it.toJson() },
                    message = "Geofence events retrieved"
                )
            )
        }

        // GET /api/geofence-events/fence/{geofenceId}
        get("/api/geofence-events/fence/{geofenceId}") {
            val userId = call.userId()
            val geofenceId = call.parameters["geofenceId"]
            if (geofenceId == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(success = false, error = "Missing geofenceId")
                )
                return@get
            }

            // Verify geofence ownership via device
            val geofenceDoc = Collections.geofences
                .find(Filters.eq("_id", geofenceId))
                .firstOrNull()
            if (geofenceDoc != null) {
                val deviceDoc = Collections.devices
                    .find(Filters.eq("deviceId", geofenceDoc.getString("deviceId")))
                    .firstOrNull()
                if (deviceDoc != null && deviceDoc.getString("userId") != userId) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiResponse<Nothing>(success = false, error = "Access denied")
                    )
                    return@get
                }
            }

            val events = Collections.geofenceEvents
                .find(Filters.eq("geofenceId", geofenceId))
                .toList()

            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = events.map { it.toJson() },
                    message = "Geofence events retrieved"
                )
            )
        }
    }
}
