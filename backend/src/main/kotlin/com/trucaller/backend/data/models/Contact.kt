package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
data class Contact(
    @BsonId
    val id: String = ObjectId().toString(),
    val userId: String,
    val name: String,
    val phoneNumber: String,       // E.164 format: "+256XXXXXXXXX"
    val email: String? = null,
    val syncedAt: String,          // ISO 8601
    val isBackedUp: Boolean
)
