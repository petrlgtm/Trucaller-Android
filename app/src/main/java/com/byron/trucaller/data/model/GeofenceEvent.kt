package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class GeofenceTransitionType {
    ENTER, EXIT, DWELL
}

@Entity(tableName = "geofence_events")
data class GeofenceEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val geofenceId: String,
    val deviceId: String,
    val transitionType: GeofenceTransitionType,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val synced: Boolean = false
)
