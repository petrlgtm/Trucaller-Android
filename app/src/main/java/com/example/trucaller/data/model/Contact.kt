package com.example.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val phoneNumber: String,
    val email: String? = null,
    val syncedAt: String,
    val isBackedUp: Boolean
)

data class ContactSyncStatus(
    val lastSynced: String? = null,
    val totalContacts: Int,
    val backedUpCount: Int,
    val isAutoBackupEnabled: Boolean
)
