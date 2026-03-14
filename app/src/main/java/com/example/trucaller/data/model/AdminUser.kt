package com.example.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin_users")
data class AdminUser(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    val lastLogin: String,
    val createdAt: String
)
