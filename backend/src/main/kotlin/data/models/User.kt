package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

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
    val avatarUrl: String? = null
)
