package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
enum class DeviceStatus {
    ACTIVE, STOLEN, INACTIVE, FLAGGED
}

@Serializable
data class Device(
    @BsonId
    val id: String = ObjectId().toString(),
    val userId: String,
    val model: String,
    val manufacturer: String,
    val osVersion: String,
    val deviceId: String,
    val status: DeviceStatus,
    val lastIp: String,
    val lastSeen: String,          // ISO 8601
    val registeredAt: String       // ISO 8601
)
