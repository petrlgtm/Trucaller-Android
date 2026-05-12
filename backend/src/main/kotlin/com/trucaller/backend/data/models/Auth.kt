package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable

// ── Request DTOs ──

@Serializable
data class LoginRequest(
    val phoneNumber: String,       // E.164 format: "+256XXXXXXXXX"
    val password: String
)

@Serializable
data class RegisterRequest(
    val fullName: String,
    val phoneNumber: String,       // E.164 format: "+256XXXXXXXXX"
    val password: String
)

@Serializable
data class SendOtpRequest(
    val phoneNumber: String,       // E.164 format: "+256XXXXXXXXX"
    val purpose: String = "registration" // "registration" or "password_reset"
)

@Serializable
data class OtpVerification(
    val phoneNumber: String,       // E.164 format: "+256XXXXXXXXX"
    val code: String
)

@Serializable
data class AdminLoginRequest(
    val phoneNumber: String,   // local (07XXXXXXXX) or E.164 (+256XXXXXXXXX)
    val password: String
)

@Serializable
data class AdminProfileUpdateRequest(
    val name: String,
    val email: String
)

@Serializable
data class AdminPasswordUpdateRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class ResetPasswordRequest(
    val phoneNumber: String,        // E.164 format: "+256XXXXXXXXX"
    val code: String,
    val newPassword: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class LogoutRequest(
    val refreshToken: String? = null
)

// ── Response DTOs ──

@Serializable
data class TokenResponse(
    val token: String,
    val refreshToken: String? = null,
    val expiresIn: Long,           // seconds until token expiry
    val userId: String
)

@Serializable
data class LookupResponse(
    val phoneNumber: String,
    val name: String?,
    val spamScore: Int,
    val reportCount: Int,
    val category: SpamCategory,
    val source: String,
    val confidence: Int
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)
