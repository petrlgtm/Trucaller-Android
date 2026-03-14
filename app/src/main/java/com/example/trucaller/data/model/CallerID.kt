package com.example.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SpamCategory {
    SAFE, SUSPECTED_SPAM, SPAM, FRAUD
}

@Entity(tableName = "caller_id_entries")
data class CallerIdEntry(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val name: String,
    val spamScore: Int,
    val reportCount: Int,
    val category: SpamCategory,
    val lastUpdated: String
)

data class CallerLookupResult(
    val entry: CallerIdEntry? = null,
    val source: String,
    val confidence: Int
)
