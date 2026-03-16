package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DeviceStatus {
    ACTIVE, STOLEN, INACTIVE, FLAGGED
}

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey val id: String,
    val userId: String,
    val model: String,
    val manufacturer: String,
    val osVersion: String,
    val deviceId: String,
    val status: DeviceStatus,
    val lastIp: String,
    val lastSeen: String,
    val registeredAt: String,
    val fcmToken: String? = null
)

@Entity(tableName = "ip_logs")
data class IpLog(
    @PrimaryKey val id: String,
    val deviceId: String,
    val ipAddress: String,
    val isp: String,
    val city: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val networkType: String,
    val timestamp: String
)
