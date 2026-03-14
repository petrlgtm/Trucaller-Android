package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
data class BlockedNumber(
    @BsonId
    val id: String = ObjectId().toString(),
    val userId: String,
    val phoneNumber: String,       // E.164 format: "+256XXXXXXXXX"
    val name: String,
    val reason: String = "",
    val blockedAt: String          // ISO 8601
)
