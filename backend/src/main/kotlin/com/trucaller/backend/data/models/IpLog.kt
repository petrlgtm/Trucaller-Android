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
    val timestamp: String,         // ISO 8601 (kept for backward compat)
    val startTime: String = timestamp,  // When device first appeared at this location
    val lastSeen: String = timestamp,   // Last time device was seen at this location
    val nearbyDevices: List<NearbyDevice> = emptyList()
)

@Serializable
data class NearbyDevice(
    val name: String,
    val macAddress: String,
    val rssi: Int,
    val type: String
)
