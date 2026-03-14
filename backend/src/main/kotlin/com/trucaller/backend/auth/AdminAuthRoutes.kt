package com.trucaller.backend.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.AdminLoginRequest
import com.trucaller.backend.data.models.AdminRole
import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.data.models.TokenResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant

// ── Role-checking utility functions ─────────────────────────────────────────

/**
 * Extracts the admin role from the JWT principal's "role" claim.
 * Returns `null` if the principal is missing or the role claim is not
 * a valid [AdminRole] value.
 */
fun ApplicationCall.getAdminRole(): AdminRole? {
    val principal = principal<JWTPrincipal>() ?: return null
    val roleClaim = principal.payload.getClaim("role")?.asString() ?: return null
    return try {
        AdminRole.valueOf(roleClaim)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Ensures the caller holds any admin role (SUPER_ADMIN or MODERATOR).
 * Throws an [IllegalAccessException] if the JWT does not carry a valid admin role.
 */
fun ApplicationCall.requireAdmin() {
    getAdminRole() ?: throw IllegalAccessException("Admin access required")
}

/**
 * Ensures the caller holds the SUPER_ADMIN role specifically.
 * Throws an [IllegalAccessException] otherwise.
 */
fun ApplicationCall.requireSuperAdmin() {
    val role = getAdminRole()
    if (role != AdminRole.SUPER_ADMIN) {
        throw IllegalAccessException("Super admin access required")
    }
}

// ── Routes ──────────────────────────────────────────────────────────────────

/**
 * Registers admin authentication routes under `/api/admin`.
 *
 * - `POST /api/admin/login` — authenticates an admin user with email + password
 *   and returns a JWT [TokenResponse].
 */
fun Route.adminAuthRoutes() {

    route("/api/admin") {

        // ── Public (no JWT required) ────────────────────────────────────
        post("/login") {
            val request = try {
                call.receive<AdminLoginRequest>()
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

            // Look up admin user by email
            val doc = Collections.adminUsers
                .find(Filters.eq("email", request.email))
                .firstOrNull()

            if (doc == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Invalid credentials"
                    )
                )
                return@post
            }

            // Verify password with BCrypt
            val storedHash = doc.getString("passwordHash")
            val verified = BCrypt.verifyer()
                .verify(request.password.toCharArray(), storedHash)
                .verified

            if (!verified) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<Nothing>(
                        success = false,
                        error = "Invalid credentials"
                    )
                )
                return@post
            }

            val userId = doc.getString("_id")
            val role = doc.getString("role") ?: "MODERATOR"

            // Generate JWT token via JwtConfig (created by Task 2.1)
            val token = JwtConfig.makeToken(userId = userId, role = role)

            // Update lastLogin timestamp
            Collections.adminUsers.updateOne(
                Filters.eq("_id", userId),
                Updates.set("lastLogin", Instant.now().toString())
            )

            val expiresIn = 3600L // 1 hour in seconds

            call.respond(
                HttpStatusCode.OK,
                ApiResponse(
                    success = true,
                    data = TokenResponse(
                        token = token,
                        refreshToken = null,
                        expiresIn = expiresIn,
                        userId = userId
                    ),
                    message = "Login successful"
                )
            )
        }
    }
}
