package com.byron.trucaller.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReportStatus {
    PENDING, VERIFIED, ESCALATED, RESOLVED
}

@Entity(tableName = "stolen_reports")
data class StolenReport(
    @PrimaryKey val id: String,
    val deviceId: String,
    val reportedBy: String,
    val reportedAt: String,
    val status: ReportStatus,
    val description: String,
    val pinVerified: Boolean,
    val lastKnownIp: String? = null,
    @Embedded val lastKnownLocation: LastKnownLocation? = null
)

data class LastKnownLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String
)
