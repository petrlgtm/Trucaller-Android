package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Role within a family group.
 */
enum class FamilyRole {
    /** Full control — can manage members, roles, and group settings. */
    OWNER,
    /** Can invite/remove members but cannot delete the group. */
    ADMIN,
    /** Standard member — can view devices and send alerts. */
    MEMBER
}

/**
 * Room entity representing a member within a family group.
 *
 * Links a [FamilyGroup] to a user, storing their display name, phone number,
 * role, and the timestamp they joined.
 */
@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey val id: String,
    val groupId: String,
    val userId: String,
    val displayName: String,
    val phoneNumber: String,
    val role: String = "MEMBER",
    val joinedAt: String
)
