package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.trucaller.backend.auth.userId
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.service.CallerIdService
import com.trucaller.backend.service.PhoneNormalizer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

// ── Request / Response DTOs ─────────────────────────────────────────────

@Serializable
data class ContactUploadRequest(
    val contacts: List<ContactItem>
)

@Serializable
data class ContactItem(
    val name: String,
    val phoneNumber: String,
    val email: String? = null
)

@Serializable
data class ContactResponse(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String?,
    val syncedAt: String
)

// ── Routes ──────────────────────────────────────────────────────────────

/**
 * Registers all contact-sync routes under `/api/contacts` on the Ktor [Route] receiver.
 * All endpoints require JWT authentication.
 */
fun Route.contactRoutes() {
    authenticate("auth-jwt") {
        route("/api/contacts") {
            uploadContacts()
            syncContacts()
            getAllContacts()
        }
    }
}

// ── POST /api/contacts/upload ───────────────────────────────────────────

private fun Route.uploadContacts() {
    post("/upload") {
        val userId = call.userId()

        val request = try {
            call.receive<ContactUploadRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body")
            )
            return@post
        }

        if (request.contacts.isEmpty()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(
                    success = false,
                    error = "Contact list must not be empty."
                )
            )
            return@post
        }

        val now = Instant.now().toString()

        val operations = request.contacts.map { contact ->
            UpdateOneModel<Document>(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.eq("phoneNumber", contact.phoneNumber)
                ),
                Document(
                    "\$set", Document()
                        .append("name", contact.name)
                        .append("phoneNumber", contact.phoneNumber)
                        .append("email", contact.email)
                        .append("userId", userId)
                        .append("syncedAt", now)
                        .append("isBackedUp", true)
                ).append("\$setOnInsert", Document("_id", ObjectId().toString())),
                UpdateOptions().upsert(true)
            )
        }

        val result = Collections.contacts.bulkWrite(operations)

        // Contribute names to the aliases collection so the ranking engine
        // can improve caller ID accuracy over time. Done in the background
        // so it doesn't add latency to the upload response.
        val aliasEntries = request.contacts
            .filter { it.name.isNotBlank() && it.phoneNumber.isNotBlank() }
            .map { Triple(PhoneNormalizer.normalize(it.phoneNumber), it.name, userId) }

        if (aliasEntries.isNotEmpty()) {
            val phones = aliasEntries.map { it.first }.distinct()
            call.application.launch {
                runCatching {
                    CallerIdService.contributeAliasBatch(aliasEntries)
                    // Immediately recompute bestName so the callerIds collection is
                    // current for the next lookup — no background job required.
                    phones.forEach { phone ->
                        runCatching { CallerIdService.refreshBestName(phone) }
                    }
                }
            }
        }

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = mapOf(
                    "upsertedCount" to (result.upserts.size + result.modifiedCount).toInt()
                ),
                message = "Contacts uploaded successfully."
            )
        )
    }
}

// ── GET /api/contacts/sync ──────────────────────────────────────────────

private fun Route.syncContacts() {
    get("/sync") {
        val userId = call.userId()

        val since = call.request.queryParameters["since"]

        if (since.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(
                    success = false,
                    error = "Query parameter 'since' is required (ISO 8601 timestamp)."
                )
            )
            return@get
        }

        val docs = Collections.contacts
            .find(
                Filters.and(
                    Filters.eq("userId", userId),
                    Filters.gt("syncedAt", since)
                )
            )
            .toList()

        val contacts = docs.map { doc ->
            ContactResponse(
                id = doc.getString("_id") ?: doc.getObjectId("_id").toString(),
                name = doc.getString("name"),
                phoneNumber = doc.getString("phoneNumber"),
                email = doc.getString("email"),
                syncedAt = doc.getString("syncedAt")
            )
        }

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = contacts,
                message = "Synced ${contacts.size} contact(s) since $since."
            )
        )
    }
}

// ── GET /api/contacts ───────────────────────────────────────────────────

private fun Route.getAllContacts() {
    get {
        val userId = call.userId()

        val docs = Collections.contacts
            .find(Filters.eq("userId", userId))
            .toList()

        val contacts = docs.map { doc ->
            ContactResponse(
                id = doc.getString("_id") ?: doc.getObjectId("_id").toString(),
                name = doc.getString("name"),
                phoneNumber = doc.getString("phoneNumber"),
                email = doc.getString("email"),
                syncedAt = doc.getString("syncedAt")
            )
        }

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = contacts,
                message = "Retrieved ${contacts.size} contact(s)."
            )
        )
    }
}
