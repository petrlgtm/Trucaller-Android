package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geofences")
data class Geofence(
    @PrimaryKey val id: String,
    val deviceId: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 200,
    val isActive: Boolean = true,
    val createdAt: String,
    val triggeredCount: Int = 0
)
