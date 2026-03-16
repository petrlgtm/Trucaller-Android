package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AlarmType {
    REMOTE_ALARM, LOCATION_REQUEST, LOCK_DEVICE
}

enum class AlarmResult {
    SUCCESS, FAILED, PENDING
}

@Entity(tableName = "alarm_logs")
data class AlarmLog(
    @PrimaryKey val id: String,
    val deviceId: String,
    val triggeredBy: String,
    val triggeredByName: String,
    val triggeredByRole: String,
    val triggeredAt: String,
    val type: AlarmType,
    val result: AlarmResult,
    val notes: String? = null
)
