package com.byron.trucaller.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val phoneNumber: String,
    val email: String? = null,
    val syncedAt: String,
    val isBackedUp: Boolean,
    val isFavourite: Boolean = false,
    val favouriteSegment: String? = null,
    val note: String? = null,
    val photoUri: String? = null
)

data class ContactSyncStatus(
    val lastSynced: String? = null,
    val totalContacts: Int,
    val backedUpCount: Int,
    val isAutoBackupEnabled: Boolean
)
