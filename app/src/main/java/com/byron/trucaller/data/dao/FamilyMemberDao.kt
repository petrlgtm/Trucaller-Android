package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.FamilyMember
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {

    @Query("SELECT * FROM family_members WHERE groupId = :groupId ORDER BY joinedAt ASC")
    fun getByGroupId(groupId: String): Flow<List<FamilyMember>>

    @Query("SELECT * FROM family_members WHERE userId = :userId")
    fun getByUserId(userId: String): Flow<List<FamilyMember>>

    @Query("SELECT * FROM family_members WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FamilyMember?

    @Query("SELECT * FROM family_members WHERE groupId = :groupId AND userId = :userId LIMIT 1")
    suspend fun getByGroupAndUser(groupId: String, userId: String): FamilyMember?

    @Query("SELECT * FROM family_members WHERE groupId = :groupId AND role = :role")
    fun getByGroupAndRole(groupId: String, role: String): Flow<List<FamilyMember>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: FamilyMember)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<FamilyMember>)

    @Update
    suspend fun update(member: FamilyMember)

    @Query("UPDATE family_members SET role = :role WHERE groupId = :groupId AND userId = :userId")
    suspend fun updateRole(groupId: String, userId: String, role: String)

    @Delete
    suspend fun delete(member: FamilyMember)

    @Query("DELETE FROM family_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun deleteByGroupAndUser(groupId: String, userId: String)

    @Query("DELETE FROM family_members WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: String)

    @Query("SELECT COUNT(*) FROM family_members WHERE groupId = :groupId")
    fun getCountByGroup(groupId: String): Flow<Int>
}
