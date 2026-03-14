package com.trucaller.backend.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.mongodb.client.model.Filters
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.time.Instant

/**
 * Registers all authentication routes under `/api/auth/` on the Ktor [Routing] receiver.
 */
fun Routing.authRoutes() {
    route("/api/auth") {
        registerRoute()
        loginRoute()
        verifyOtpRoute()
        resetPasswordRoute()
    }
}

// ── POST /api/auth/register ─────────────────────────────────────────────

private fun Route.registerRoute() {
    post("/register") {
        val request = call.receive<RegisterRequest>()

        // Validate E.164 phone number format (+256...)
        if (!request.phoneNumber.matches(Regex("^\\+[1-9]\\d{6,14}$"))) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(
                    success = false,
                    error = "Invalid phone number format. Use E.164 format (e.g. +256XXXXXXXXX)."
                )
            )
            return@post
        }

        // Check if user already exists
        val existing = Collections.users
            .find(Filters.eq("phoneNumber", request.phoneNumber))
            .firstOrNull()

        if (existing != null) {
            call.respond(
                HttpStatusCode.Conflict,
                ApiResponse<Unit>(
                    success = false,
                    error = "A user with this phone number already exists."
                )
            )
            return@post
        }

        // Hash the password (client sends SHA-256 hash, server bcrypt's it)
        val passwordHash = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())

        val userId = org.bson.types.ObjectId().toString()
        val now = Instant.now().toString()

        val userDoc = Document()
            .append("_id", userId)
            .append("fullName", request.fullName)
            .append("phoneNumber", request.phoneNumber)
            .append("passwordHash", passwordHash)
            .append("createdAt", now)
            .append("isActive", true)

        Collections.users.insertOne(userDoc)

        val token = JwtConfig.makeToken(userId, role = "user")

        call.respond(
            HttpStatusCode.Created,
            ApiResponse(
                success = true,
                data = TokenResponse(
                    token = token,
                    expiresIn = JwtConfig.expiresInSeconds(),
                    userId = userId
                ),
                message = "Registration successful."
            )
        )
    }
}

// ── POST /api/auth/login ────────────────────────────────────────────────

private fun Route.loginRoute() {
    post("/login") {
        val request = call.receive<LoginRequest>()

        val userDoc = Collections.users
            .find(Filters.eq("phoneNumber", request.phoneNumber))
            .firstOrNull()

        if (userDoc == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiResponse<Unit>(success = false, error = "Invalid credentials.")
            )
            return@post
        }

        val storedHash = userDoc.getString("passwordHash")
        val verified = BCrypt.verifyer()
            .verify(request.password.toCharArray(), storedHash)
            .verified

        if (!verified) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiResponse<Unit>(success = false, error = "Invalid credentials.")
            )
            return@post
        }

        val userId = userDoc.getString("_id")
        val token = JwtConfig.makeToken(userId, role = "user")

        // Update lastLogin timestamp
        Collections.users.updateOne(
            Filters.eq("_id", userId),
            Document("\$set", Document("lastLogin", Instant.now().toString()))
        )

        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = TokenResponse(
                    token = token,
                    expiresIn = JwtConfig.expiresInSeconds(),
                    userId = userId
                ),
                message = "Login successful."
            )
        )
    }
}

// ── POST /api/auth/verify-otp (stub) ────────────────────────────────────

private fun Route.verifyOtpRoute() {
    post("/verify-otp") {
        val request = call.receive<OtpVerification>()

        // Stub: accept hard-coded OTP for development
        if (request.code == "123456") {
            call.respond(
                HttpStatusCode.OK,
                ApiResponse<Unit>(
                    success = true,
                    message = "OTP verified successfully."
                )
            )
        } else {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiResponse<Unit>(
                    success = false,
                    error = "Invalid or expired OTP."
                )
            )
        }
    }
}

// ── POST /api/auth/reset-password (stub) ────────────────────────────────

private fun Route.resetPasswordRoute() {
    post("/reset-password") {
        // Stub: password reset requires OTP verification first.
        // Full implementation will accept a new password + OTP token.
        call.respond(
            HttpStatusCode.OK,
            ApiResponse<Unit>(
                success = true,
                message = "Password reset endpoint. Please verify OTP before resetting your password."
            )
        )
    }
}
