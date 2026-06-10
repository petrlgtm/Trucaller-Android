package com.trucaller.backend.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.AdminLoginRequest
import com.trucaller.backend.data.models.AdminRole
import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.data.models.TokenResponse
import com.trucaller.backend.service.RedisCache
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant

// ── Authorised admin phone numbers (E.164) ───────────────────────────────────

private val AUTHORISED_ADMIN_PHONES = setOf(
    "+256787959715",
    "+256751159472"
)

/**
 * Normalises a Ugandan phone number to E.164 (+256XXXXXXXXX).
 * Accepts: 07XXXXXXXX, 256XXXXXXXXX, +256XXXXXXXXX, or 9-digit bare numbers.
 */
fun normalizeUgandaPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        raw.startsWith("+") && digits.length == 12 -> "+$digits"
        digits.startsWith("256") && digits.length == 12 -> "+$digits"
        digits.startsWith("0") && digits.length == 10 -> "+256${digits.drop(1)}"
        digits.length == 9 -> "+256$digits"
        else -> "+$digits"
    }
}

// ── Role-checking utility functions ─────────────────────────────────────────

/**
 * Extracts the admin role from the JWT principal's "role" claim.
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
 */
fun ApplicationCall.requireAdmin() {
    getAdminRole() ?: throw IllegalAccessException("Admin access required")
}

/**
 * Ensures the caller holds the SUPER_ADMIN role specifically.
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
 * - `POST /api/admin/login` — authenticates by phone number + password.
 *   Only the two authorised phone numbers (+256787959715, +256751159472)
 *   can log in. Returns a JWT [TokenResponse].
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
                    ApiResponse<Nothing>(success = false, error = "Invalid request body")
                )
                return@post
            }

            if (request.phoneNumber.isBlank() || request.password.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse<Nothing>(success = false, error = "Phone number and password are required")
                )
                return@post
            }

            val normalizedPhone = normalizeUgandaPhone(request.phoneNumber)
            val ip = call.clientIp()

            // Brute-force protection: same thresholds as regular user login
            if (RedisCache.isLoginLocked(normalizedPhone)) {
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ApiResponse<Nothing>(success = false, error = "Too many failed login attempts. Try again in 15 minutes.")
                )
                return@post
            }

            // Whitelist enforcement — reject before any DB query
            if (normalizedPhone !in AUTHORISED_ADMIN_PHONES) {
                RedisCache.recordFailedLogin(normalizedPhone)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<Nothing>(success = false, error = "Invalid credentials")
                )
                return@post
            }

            // Look up admin by phone number
            val doc = Collections.adminUsers
                .find(Filters.eq("phoneNumber", normalizedPhone))
                .firstOrNull()

            if (doc == null) {
                RedisCache.recordFailedLogin(normalizedPhone)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<Nothing>(success = false, error = "Invalid credentials")
                )
                return@post
            }

            // Verify password with BCrypt
            val storedHash = doc.getString("passwordHash")
            val verified = BCrypt.verifyer()
                .verify(request.password.toCharArray(), storedHash)
                .verified

            if (!verified) {
                RedisCache.recordFailedLogin(normalizedPhone)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse<Nothing>(success = false, error = "Invalid credentials")
                )
                return@post
            }

            RedisCache.clearLoginAttempts(normalizedPhone)

            val userId = doc.getString("_id")
            val role = doc.getString("role") ?: "SUPER_ADMIN"
            val adminName = doc.getString("name") ?: ""

            // Generate JWT
            val token = JwtConfig.makeToken(userId = userId, role = role)

            // Update lastLogin timestamp
            Collections.adminUsers.updateOne(
                Filters.eq("_id", userId),
                Updates.set("lastLogin", Instant.now().toString())
            )

            val expiresIn = JwtConfig.expiresInSeconds()

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
                    message = "Welcome, $adminName"
                )
            )
        }
    }
}
