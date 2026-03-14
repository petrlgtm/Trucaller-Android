package com.trucaller.backend.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
enum class AlarmType {
    REMOTE_ALARM, LOCATION_REQUEST, LOCK_DEVICE
}

@Serializable
enum class AlarmResult {
    SUCCESS, FAILED, PENDING
}

@Serializable
data class AlarmLog(
    @BsonId
    val id: String = ObjectId().toString(),
    val deviceId: String,
    val triggeredBy: String,
    val triggeredByName: String,
    val triggeredByRole: String,
    val triggeredAt: String,       // ISO 8601
    val type: AlarmType,
    val result: AlarmResult,
    val notes: String? = null
)
