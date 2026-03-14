package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
data class IpLog(
    @BsonId
    val id: String = ObjectId().toString(),
    val deviceId: String,
    val ipAddress: String,
    val isp: String,
    val city: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val networkType: String,       // "wifi", "mobile"
    val timestamp: String          // ISO 8601
)
