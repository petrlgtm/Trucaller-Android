package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val securityPin: String? = null
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
