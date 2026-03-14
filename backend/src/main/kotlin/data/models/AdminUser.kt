package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
enum class AdminRole {
    SUPER_ADMIN, MODERATOR
}

@Serializable
data class AdminUser(
    @BsonId
    val id: String = ObjectId().toString(),
    val name: String,
    val email: String,
    val passwordHash: String,
    val role: AdminRole,
    val lastLogin: String? = null, // ISO 8601
    val createdAt: String          // ISO 8601
)
