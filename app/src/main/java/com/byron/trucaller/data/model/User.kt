package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TrustLevel {
    NEW,        // 0-19
    BASIC,      // 20-49
    TRUSTED,    // 50-79
    VERIFIED,   // 80-99
    AUTHORITY   // 100
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val fullName: String,
    val phoneNumber: String,
    val email: String? = null,
    val passwordHash: String,
    val createdAt: String,
    val lastLogin: String? = null,
    val isActive: Boolean = true,
    val avatarUrl: String? = null,
    val securityPin: String? = null,
    val trustScore: Int = 0,
    val trustLevel: TrustLevel = TrustLevel.NEW
)

data class AuthState(
    val user: User? = null,
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val token: String? = null,
    val pendingPhone: String? = null,
    val pendingFullName: String? = null,
    val pendingPasswordHash: String? = null,
    val pendingPassword: String? = null,
    val generatedOtp: String? = null
)

data class LoginRequest(
    val phoneNumber: String,
    val password: String
)

data class RegisterRequest(
    val fullName: String,
    val phoneNumber: String,
    val password: String
)

data class OtpVerification(
    val phoneNumber: String,
    val code: String,
    val expiresAt: Long
)
