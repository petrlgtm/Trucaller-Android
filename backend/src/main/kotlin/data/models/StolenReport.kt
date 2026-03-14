package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
enum class ReportStatus {
    PENDING, VERIFIED, ESCALATED, RESOLVED
}

@Serializable
data class LastKnownLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String
)

@Serializable
data class StolenReport(
    @BsonId
    val id: String = ObjectId().toString(),
    val deviceId: String,
    val reportedBy: String,
    val reportedAt: String,        // ISO 8601
    val status: ReportStatus,
    val description: String,
    val pinVerified: Boolean,
    val lastKnownIp: String? = null,
    val lastKnownLocation: LastKnownLocation? = null
)
