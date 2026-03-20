package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_aliases")
data class ContactAlias(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val name: String,
    val source: String,  // "user", "caller_id", "contact_book", "whatsapp", etc.
    val addedAt: String
)
