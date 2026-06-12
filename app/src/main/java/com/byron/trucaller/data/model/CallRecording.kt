package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CallDirection {
    INCOMING,
    OUTGOING
}

@Entity(tableName = "call_recordings")
data class CallRecording(
    @PrimaryKey val id: String,
    val userId: String,
    val phoneNumber: String,
    val contactName: String?,
    val callDirection: CallDirection,
    val startTime: Long,
    val duration: Long = 0L,
    val filePath: String,
    val fileSize: Long = 0L,
    val isStarred: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** True when [filePath] points to an AES-GCM encrypted file (see RecordingCrypto). */
    val encrypted: Boolean = false
)
