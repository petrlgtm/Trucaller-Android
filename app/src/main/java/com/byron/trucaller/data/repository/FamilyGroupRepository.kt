package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.FamilyGroupDao
import com.byron.trucaller.data.dao.FamilyMemberDao
import com.byron.trucaller.data.model.FamilyGroup
import com.byron.trucaller.data.model.FamilyMember
import kotlinx.coroutines.flow.Flow

class FamilyGroupRepository(
    private val familyGroupDao: FamilyGroupDao,
    private val familyMemberDao: FamilyMemberDao
) {
    // ── Group CRUD ────────────────────────────────────────────────────────

    fun getAllGroups(): Flow<List<FamilyGroup>> =
        familyGroupDao.getAll()

    fun getGroupsByOwner(ownerId: String): Flow<List<FamilyGroup>> =
        familyGroupDao.getByOwner(ownerId)

    suspend fun getGroupById(id: String): FamilyGroup? =
        familyGroupDao.getById(id)

    suspend fun getGroupByInviteCode(inviteCode: String): FamilyGroup? =
        familyGroupDao.getByInviteCode(inviteCode)

    suspend fun insertGroup(group: FamilyGroup) =
        familyGroupDao.insert(group)

    suspend fun insertGroups(groups: List<FamilyGroup>) =
        familyGroupDao.insertAll(groups)

    suspend fun updateGroup(group: FamilyGroup) =
        familyGroupDao.update(group)

    suspend fun updateMemberCount(groupId: String, count: Int) =
        familyGroupDao.updateMemberCount(groupId, count)

    suspend fun deleteGroup(group: FamilyGroup) =
        familyGroupDao.delete(group)

    suspend fun deleteGroupById(id: String) {
        familyMemberDao.deleteByGroupId(id)
        familyGroupDao.deleteById(id)
    }

    fun getGroupCount(): Flow<Int> =
        familyGroupDao.getCount()

    // ── Member CRUD ───────────────────────────────────────────────────────

    fun getMembersByGroup(groupId: String): Flow<List<FamilyMember>> =
        familyMemberDao.getByGroupId(groupId)

    fun getMembershipsByUser(userId: String): Flow<List<FamilyMember>> =
        familyMemberDao.getByUserId(userId)

    suspend fun getMemberById(id: String): FamilyMember? =
        familyMemberDao.getById(id)

    suspend fun getMemberByGroupAndUser(groupId: String, userId: String): FamilyMember? =
        familyMemberDao.getByGroupAndUser(groupId, userId)

    fun getMembersByRole(groupId: String, role: String): Flow<List<FamilyMember>> =
        familyMemberDao.getByGroupAndRole(groupId, role)

    suspend fun insertMember(member: FamilyMember) =
        familyMemberDao.insert(member)

    suspend fun insertMembers(members: List<FamilyMember>) =
        familyMemberDao.insertAll(members)

    suspend fun updateMember(member: FamilyMember) =
        familyMemberDao.update(member)

    suspend fun updateMemberRole(groupId: String, userId: String, role: String) =
        familyMemberDao.updateRole(groupId, userId, role)

    suspend fun removeMember(member: FamilyMember) =
        familyMemberDao.delete(member)

    suspend fun removeMemberByGroupAndUser(groupId: String, userId: String) =
        familyMemberDao.deleteByGroupAndUser(groupId, userId)

    suspend fun removeAllMembersByGroup(groupId: String) =
        familyMemberDao.deleteByGroupId(groupId)

    fun getMemberCountByGroup(groupId: String): Flow<Int> =
        familyMemberDao.getCountByGroup(groupId)
}
