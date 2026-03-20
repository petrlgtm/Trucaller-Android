package com.trucaller.backend.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.time.Instant
import java.util.Date

/**
 * Registers all authentication routes under `/api/auth/` on the Ktor [Routing] receiver.
 */
fun Routing.authRoutes() {
    route("/api/auth") {
        sendOtpRoute()
        verifyOtpRoute()
        registerRoute()
        loginRoute()
        resetPasswordRoute()
        deleteAccountRoute()
    }
}

// ── OTP helpers ─────────────────────────────────────────────────────────

private const val OTP_LENGTH = 6
private const val OTP_TTL_MINUTES = 10L
private const val MAX_OTP_ATTEMPTS = 5
private const val MAX_OTP_REQUESTS_PER_HOUR = 5

private fun generateOtp(): String {
    return (100000..999999).random().toString()
}

/**
 * Stores an OTP in MongoDB with TTL and attempt tracking.
 */
private suspend fun storeOtp(phoneNumber: String, code: String, purpose: String) {
    // Delete any existing OTP for this phone + purpose
    Collections.otpCodes.deleteMany(
        Filters.and(
            Filters.eq("phoneNumber", phoneNumber),
            Filters.eq("purpose", purpose)
        )
    )

    val now = Instant.now()
    val expiresAt = Date.from(now.plusSeconds(OTP_TTL_MINUTES * 60))

    val otpDoc = Document()
        .append("phoneNumber", phoneNumber)
        .append("code", code)
        .append("purpose", purpose)
        .append("attempts", 0)
        .append("createdAt", now.toString())
        .append("expiresAt", expiresAt)

    Collections.otpCodes.insertOne(otpDoc)
}

/**
 * Validates an OTP from MongoDB. Returns true if valid, false otherwise.
 * Increments attempt counter and deletes OTP on success or max attempts.
 */
private suspend fun validateOtp(phoneNumber: String, code: String, purpose: String): OtpValidationResult {
    val filter = Filters.and(
        Filters.eq("phoneNumber", phoneNumber),
        Filters.eq("purpose", purpose)
    )

    val otpDoc = Collections.otpCodes.find(filter).firstOrNull()
        ?: return OtpValidationResult.NOT_FOUND

    val attempts = otpDoc.getInteger("attempts", 0)
    if (attempts >= MAX_OTP_ATTEMPTS) {
        Collections.otpCodes.deleteOne(filter)
        return OtpValidationResult.MAX_ATTEMPTS
    }

    val expiresAt = otpDoc.getDate("expiresAt")
    if (expiresAt != null && expiresAt.before(Date())) {
        Collections.otpCodes.deleteOne(filter)
        return OtpValidationResult.EXPIRED
    }

    val storedCode = otpDoc.getString("code")
    if (storedCode != code) {
        // Increment attempts
        Collections.otpCodes.updateOne(filter, Updates.inc("attempts", 1))
        return OtpValidationResult.INVALID
    }

    // Valid OTP — delete it (one-time use)
    Collections.otpCodes.deleteOne(filter)
    return OtpValidationResult.VALID
}

private enum class OtpValidationResult {
    VALID, INVALID, EXPIRED, NOT_FOUND, MAX_ATTEMPTS
}

/**
 * Finds the purpose of an existing OTP for the given phone number.
 * Checks "registration" first, then "password_reset".
 * Returns null if no OTP exists for either purpose.
 */
private suspend fun findOtpPurpose(phoneNumber: String): String? {
    for (purpose in listOf("registration", "password_reset")) {
        val doc = Collections.otpCodes.find(
            Filters.and(
                Filters.eq("phoneNumber", phoneNumber),
                Filters.eq("purpose", purpose)
            )
        ).firstOrNull()
        if (doc != null) return purpose
    }
    return null
}

/**
 * Rate-limits OTP requests: max [MAX_OTP_REQUESTS_PER_HOUR] per phone per hour.
 * Uses a separate `otpRateLimits` collection to track requests independently
 * of the `otpCodes` collection (whose documents are deleted on each new request).
 */
private suspend fun isRateLimited(phoneNumber: String): Boolean {
    val oneHourAgo = Date.from(Instant.now().minusSeconds(3600))
    val recentCount = Collections.otpRateLimits.countDocuments(
        Filters.and(
            Filters.eq("phoneNumber", phoneNumber),
            Filters.gte("requestedAt", oneHourAgo)
        )
    )
    return recentCount >= MAX_OTP_REQUESTS_PER_HOUR
}

/**
 * Records an OTP request in the rate-limit tracking collection.
 */
private suspend fun recordOtpRequest(phoneNumber: String) {
    val now = Date.from(Instant.now())
    val doc = Document()
        .append("phoneNumber", phoneNumber)
        .append("requestedAt", now)
    Collections.otpRateLimits.insertOne(doc)
}

// ── POST /api/auth/send-otp ─────────────────────────────────────────────

private fun Route.sendOtpRoute() {
    post("/send-otp") {
        val request = try {
            call.receive<SendOtpRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body. Required: phoneNumber, purpose")
            )
            return@post
        }

        // Validate phone format
        if (!request.phoneNumber.matches(Regex("^\\+[1-9]\\d{6,14}$"))) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid phone number format. Use E.164 format.")
            )
            return@post
        }

        // Validate purpose
        if (request.purpose !in listOf("registration", "password_reset")) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid purpose. Must be 'registration' or 'password_reset'.")
            )
            return@post
        }

        // Rate limiting
        if (isRateLimited(request.phoneNumber)) {
            call.respond(
                HttpStatusCode.TooManyRequests,
                ApiResponse<Unit>(success = false, error = "Too many OTP requests. Please try again later.")
            )
            return@post
        }

        // For registration: check if user already exists
        if (request.purpose == "registration") {
            val existing = Collections.users
                .find(Filters.eq("phoneNumber", request.phoneNumber))
                .firstOrNull()
            if (existing != null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse<Unit>(success = false, error = "A user with this phone number already exists.")
                )
                return@post
            }
        }

        // For password reset: check if user exists
        if (request.purpose == "password_reset") {
            val existing = Collections.users
                .find(Filters.eq("phoneNumber", request.phoneNumber))
                .firstOrNull()
            if (existing == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, error = "No account found with this phone number.")
                )
                return@post
            }
        }

        // Record rate-limit entry and generate OTP
        recordOtpRequest(request.phoneNumber)
        val otp = generateOtp()
        storeOtp(request.phoneNumber, otp, request.purpose)

        // In production, send OTP via SMS gateway here.
        // For development, the OTP is returned in the response.
        call.respond(
            HttpStatusCode.OK,
            ApiResponse(
                success = true,
                data = mapOf("otpSent" to true, "expiresInMinutes" to OTP_TTL_MINUTES),
                message = "Verification code sent to ${request.phoneNumber}."
            )
        )
    }
}

// ── POST /api/auth/verify-otp ───────────────────────────────────────────

private fun Route.verifyOtpRoute() {
    post("/verify-otp") {
        val request = try {
            call.receive<OtpVerification>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body. Required: phoneNumber, code")
            )
            return@post
        }

        if (request.code.length != OTP_LENGTH || !request.code.all { it.isDigit() }) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "OTP must be $OTP_LENGTH digits.")
            )
            return@post
        }

        // Determine which purpose has a pending OTP for this phone number,
        // then validate only that one to avoid burning attempts on unrelated OTPs.
        val purpose = findOtpPurpose(request.phoneNumber)

        if (purpose == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiResponse<Unit>(success = false, error = "Invalid or expired OTP.")
            )
            return@post
        }

        val result = validateOtp(request.phoneNumber, request.code, purpose)
        if (result == OtpValidationResult.VALID) {
            call.respond(
                HttpStatusCode.OK,
                ApiResponse<Unit>(success = true, message = "OTP verified successfully.")
            )
            return@post
        }

        val errorMessage = when (result) {
            OtpValidationResult.EXPIRED -> "OTP has expired. Please request a new one."
            OtpValidationResult.MAX_ATTEMPTS -> "Too many failed attempts. Please request a new OTP."
            OtpValidationResult.INVALID -> "Invalid OTP code."
            else -> "Invalid or expired OTP."
        }

        call.respond(
            HttpStatusCode.Unauthorized,
            ApiResponse<Unit>(success = false, error = errorMessage)
        )
    }
}

// ── POST /api/auth/register ─────────────────────────────────────────────

private fun Route.registerRoute() {
    post("/register") {
        val request = try {
            call.receive<RegisterRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body")
            )
            return@post
        }

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

        // Validate password length
        if (request.password.length < 6) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Password must be at least 6 characters.")
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

        // Hash the password with bcrypt (plaintext password from client over HTTPS)
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
            .append("trustScore", 0)
            .append("trustLevel", "NEW")

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
        val request = try {
            call.receive<LoginRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body")
            )
            return@post
        }

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

// ── POST /api/auth/reset-password ────────────────────────────────────

private fun Route.resetPasswordRoute() {
    post("/reset-password") {
        val request = try {
            call.receive<ResetPasswordRequest>()
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid request body. Required: phoneNumber, code, newPassword")
            )
            return@post
        }

        // Validate phone format
        if (!request.phoneNumber.matches(Regex("^\\+[1-9]\\d{6,14}$"))) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Invalid phone number format.")
            )
            return@post
        }

        // Validate new password
        if (request.newPassword.length < 6) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Unit>(success = false, error = "Password must be at least 6 characters.")
            )
            return@post
        }

        // Verify OTP from MongoDB
        val otpResult = validateOtp(request.phoneNumber, request.code, "password_reset")
        when (otpResult) {
            OtpValidationResult.VALID -> { /* continue */ }
            OtpValidationResult.EXPIRED -> {
                call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(success = false, error = "OTP has expired. Please request a new one."))
                return@post
            }
            OtpValidationResult.MAX_ATTEMPTS -> {
                call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(success = false, error = "Too many failed attempts. Please request a new OTP."))
                return@post
            }
            else -> {
                call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(success = false, error = "Invalid or expired OTP."))
                return@post
            }
        }

        // Find user by phone
        val userDoc = Collections.users
            .find(Filters.eq("phoneNumber", request.phoneNumber))
            .firstOrNull()

        if (userDoc == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse<Unit>(success = false, error = "No account found with this phone number.")
            )
            return@post
        }

        // Hash the new password and update
        val newPasswordHash = BCrypt.withDefaults().hashToString(12, request.newPassword.toCharArray())
        val userId = userDoc.getString("_id")

        Collections.users.updateOne(
            Filters.eq("_id", userId),
            Document("\$set", Document("passwordHash", newPasswordHash))
        )

        call.respond(
            HttpStatusCode.OK,
            ApiResponse<Unit>(
                success = true,
                message = "Password reset successfully."
            )
        )
    }
}

// ── DELETE /api/auth/delete-account ──────────────────────────────────

private fun Route.deleteAccountRoute() {
    authenticate("auth-jwt") {
        delete("/delete-account") {
            val userId = call.userId()

            // Verify the user exists
            val userDoc = Collections.users
                .find(Filters.eq("_id", userId))
                .firstOrNull()

            if (userDoc == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse<Unit>(success = false, error = "Account not found.")
                )
                return@delete
            }

            // Delete all user data across collections
            val userFilter = Filters.eq("userId", userId)

            // Delete devices and their IP logs
            val devicesCursor = Collections.devices.find(userFilter)
            val deviceIds = mutableListOf<String>()
            devicesCursor.collect { doc ->
                val deviceId = doc.getString("_id")
                if (deviceId != null) deviceIds.add(deviceId)
            }
            for (deviceId in deviceIds) {
                Collections.ipLogs.deleteMany(Filters.eq("deviceId", deviceId))
                Collections.alarmLogs.deleteMany(Filters.eq("deviceId", deviceId))
                Collections.geofenceEvents.deleteMany(Filters.eq("deviceId", deviceId))
                Collections.geofences.deleteMany(Filters.eq("deviceId", deviceId))
            }
            Collections.devices.deleteMany(userFilter)

            // Delete contacts
            Collections.contacts.deleteMany(userFilter)

            // Delete blocked numbers
            Collections.blockedNumbers.deleteMany(userFilter)

            // Delete stolen reports
            Collections.stolenReports.deleteMany(userFilter)

            // Delete SMS spam reports
            Collections.smsSpamReports.deleteMany(userFilter)

            // Delete spam verifications
            Collections.spamVerifications.deleteMany(userFilter)

            // Delete family memberships
            Collections.familyMembers.deleteMany(userFilter)

            // Delete family groups owned by this user
            Collections.familyGroups.deleteMany(Filters.eq("ownerId", userId))

            // Delete OTP codes and rate limits for this user's phone
            val phoneNumber = userDoc.getString("phoneNumber")
            if (phoneNumber != null) {
                Collections.otpCodes.deleteMany(Filters.eq("phoneNumber", phoneNumber))
                Collections.otpRateLimits.deleteMany(Filters.eq("phoneNumber", phoneNumber))
            }

            // Finally, delete the user document
            Collections.users.deleteOne(Filters.eq("_id", userId))

            call.respond(
                HttpStatusCode.OK,
                ApiResponse<Unit>(
                    success = true,
                    message = "Account and all associated data have been permanently deleted."
                )
            )
        }
    }
}
