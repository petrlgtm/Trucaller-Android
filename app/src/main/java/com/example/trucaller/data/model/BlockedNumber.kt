package com.example.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_numbers")
data class BlockedNumber(
    @PrimaryKey val id: String,
    val userId: String,
    val phoneNumber: String,
    val name: String,
    val reason: String = "",
    val blockedAt: String
)
