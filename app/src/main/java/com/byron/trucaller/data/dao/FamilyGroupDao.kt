package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.FamilyGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyGroupDao {

    @Query("SELECT * FROM family_groups ORDER BY createdAt DESC")
    fun getAll(): Flow<List<FamilyGroup>>

    @Query("SELECT * FROM family_groups WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FamilyGroup?

    @Query("SELECT * FROM family_groups WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    fun getByOwner(ownerId: String): Flow<List<FamilyGroup>>

    @Query("SELECT * FROM family_groups WHERE inviteCode = :inviteCode LIMIT 1")
    suspend fun getByInviteCode(inviteCode: String): FamilyGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: FamilyGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<FamilyGroup>)

    @Update
    suspend fun update(group: FamilyGroup)

    @Query("UPDATE family_groups SET memberCount = :count WHERE id = :groupId")
    suspend fun updateMemberCount(groupId: String, count: Int)

    @Delete
    suspend fun delete(group: FamilyGroup)

    @Query("DELETE FROM family_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM family_groups")
    fun getCount(): Flow<Int>
}
