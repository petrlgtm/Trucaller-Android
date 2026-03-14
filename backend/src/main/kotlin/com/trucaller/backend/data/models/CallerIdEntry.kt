package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
enum class SpamCategory {
    SAFE, SUSPECTED_SPAM, SPAM, FRAUD
}

@Serializable
data class CallerIdEntry(
    @BsonId
    val id: String = ObjectId().toString(),
    val phoneNumber: String,       // E.164 format: "+256XXXXXXXXX"
    val name: String,
    val spamScore: Int,            // 0-100
    val reportCount: Int,
    val category: SpamCategory,
    val lastUpdated: String        // ISO 8601
)
