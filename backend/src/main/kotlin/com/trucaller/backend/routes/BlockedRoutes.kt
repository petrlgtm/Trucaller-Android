package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.trucaller.backend.auth.userId
import com.trucaller.backend.data.Collections
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
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

@Serializable
data class BlockNumberRequest(
    val phoneNumber: String,
    val name: String? = null,
    val reason: String? = null
)

@Serializable
data class BlockedNumberResponse(
    val id: String,
    val userId: String,
    val phoneNumber: String,
    val name: String,
    val reason: String,
    val blockedAt: String
)

/**
 * Blocked number management routes under `/api/blocked`.
 *
 * - `POST   /api/blocked/add`       — block a phone number
 * - `GET    /api/blocked`           — list user's blocked numbers
 * - `DELETE /api/blocked/{id}`      — unblock a number
 * - `GET    /api/blocked/check/{phoneNumber}` — check if a number is blocked
 */
fun Route.blockedRoutes() {

    route("/api/blocked") {

        authenticate("auth-jwt") {

            // ── POST /api/blocked/add ────────────────────────────────
            post("/add") {
                val userId = call.userId()
                val request = try {
                    call.receive<BlockNumberRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, error = "Invalid request. 'phoneNumber' is required.")
                    )
                    return@post
                }

                // Check if already blocked
                val existing = Collections.blockedNumbers
                    .find(
                        Filters.and(
                            Filters.eq("userId", userId),
                            Filters.eq("phoneNumber", request.phoneNumber)
                        )
                    )
                    .firstOrNull()

                if (existing != null) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiResponse<Unit>(success = false, error = "Number already blocked.")
                    )
                    return@post
                }

                val now = Instant.now().toString()
                val doc = Document()
                    .append("_id", ObjectId().toString())
                    .append("userId", userId)
                    .append("phoneNumber", request.phoneNumber)
                    .append("name", request.name ?: "Unknown")
                    .append("reason", request.reason ?: "Blocked by user")
                    .append("blockedAt", now)

                Collections.blockedNumbers.insertOne(doc)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Unit>(success = true, message = "Number blocked successfully.")
                )
            }

            // ── GET /api/blocked ─────────────────────────────────────
            get {
                val userId = call.userId()

                val docs = Collections.blockedNumbers
                    .find(Filters.eq("userId", userId))
                    .toList()

                val blocked = docs.map { doc ->
                    BlockedNumberResponse(
                        id = doc.getString("_id"),
                        userId = doc.getString("userId"),
                        phoneNumber = doc.getString("phoneNumber"),
                        name = doc.getString("name") ?: "Unknown",
                        reason = doc.getString("reason") ?: "",
                        blockedAt = doc.getString("blockedAt") ?: ""
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(success = true, data = blocked)
                )
            }

            // ── DELETE /api/blocked/{id} ──────────────────────────────
            delete("/{id}") {
                val userId = call.userId()
                val blockId = call.parameters["id"]

                if (blockId.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, error = "Missing block ID.")
                    )
                    return@delete
                }

                // Verify ownership
                val result = Collections.blockedNumbers.deleteOne(
                    Filters.and(
                        Filters.eq("_id", blockId),
                        Filters.eq("userId", userId)
                    )
                )

                if (result.deletedCount == 0L) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(success = false, error = "Blocked number not found.")
                    )
                    return@delete
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse<Unit>(success = true, message = "Number unblocked.")
                )
            }

            // ── GET /api/blocked/check/{phoneNumber} ─────────────────
            get("/check/{phoneNumber}") {
                val userId = call.userId()
                val phoneNumber = call.parameters["phoneNumber"]

                if (phoneNumber.isNullOrBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Boolean>(success = false, error = "Phone number required.")
                    )
                    return@get
                }

                val exists = Collections.blockedNumbers
                    .find(
                        Filters.and(
                            Filters.eq("userId", userId),
                            Filters.eq("phoneNumber", phoneNumber)
                        )
                    )
                    .firstOrNull() != null

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(success = true, data = exists)
                )
            }
        }
    }
}
