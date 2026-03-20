package com.byron.trucaller.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a family group.
 *
 * Members share device location and stolen-device alerts within the group.
 * Each group has an owner, an invite code for joining, and a collection of
 * [FamilyGroupMember] entries (fetched from API, not stored in Room).
 */
@Entity(tableName = "family_groups")
data class FamilyGroup(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val ownerId: String,
    val inviteCode: String,
    val memberCount: Int = 1,
    val createdAt: String,
    val maxMembers: Int = 10
) {
    /** Transient member list populated from API responses, not persisted in Room. */
    @androidx.room.Ignore
    var members: List<FamilyGroupMember> = emptyList()

    companion object {
        fun fromApi(
            id: String,
            name: String,
            ownerId: String,
            inviteCode: String,
            createdAt: String,
            members: List<FamilyGroupMember> = emptyList(),
            maxMembers: Int = 10,
            description: String = ""
        ): FamilyGroup {
            return FamilyGroup(
                id = id,
                name = name,
                description = description,
                ownerId = ownerId,
                inviteCode = inviteCode,
                memberCount = members.size.coerceAtLeast(1),
                createdAt = createdAt,
                maxMembers = maxMembers
            ).also { it.members = members }
        }
    }
}

data class FamilyGroupMember(
    val userId: String,
    val fullName: String,
    val phoneNumber: String,
    val role: FamilyGroupRole = FamilyGroupRole.MEMBER,
    val joinedAt: String,
    val avatarUrl: String? = null
)

enum class FamilyGroupRole {
    ADMIN,
    MEMBER
}
