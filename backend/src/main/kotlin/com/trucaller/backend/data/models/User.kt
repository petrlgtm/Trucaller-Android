package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
enum class TrustLevel {
    NEW,        // 0–19:  freshly registered, minimal history
    BASIC,      // 20–49: some verified activity
    TRUSTED,    // 50–79: consistent positive behaviour
    VERIFIED,   // 80–99: long-standing, highly trusted
    AUTHORITY   // 100:   admin-granted authority status
}

@Serializable
data class User(
    @BsonId
    val id: String = ObjectId().toString(),
    val fullName: String,
    val phoneNumber: String,       // E.164 format: "+256XXXXXXXXX"
    val email: String? = null,
    val passwordHash: String,
    val createdAt: String,         // ISO 8601
    val lastLogin: String? = null, // ISO 8601
    val isActive: Boolean = true,
    val avatarUrl: String? = null,
    val trustScore: Int = 0,       // 0-100
    val trustLevel: TrustLevel = TrustLevel.NEW
)
