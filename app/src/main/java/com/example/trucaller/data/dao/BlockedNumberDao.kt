package com.example.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.trucaller.data.model.BlockedNumber
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers WHERE userId = :userId ORDER BY blockedAt DESC")
    fun getByUserId(userId: String): Flow<List<BlockedNumber>>

    @Query("SELECT * FROM blocked_numbers WHERE userId = :userId AND phoneNumber = :phone LIMIT 1")
    suspend fun getByUserAndPhone(userId: String, phone: String): BlockedNumber?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blockedNumber: BlockedNumber)

    @Delete
    suspend fun delete(blockedNumber: BlockedNumber)

    @Query("DELETE FROM blocked_numbers WHERE userId = :userId AND phoneNumber = :phone")
    suspend fun unblock(userId: String, phone: String)

    @Query("SELECT COUNT(*) FROM blocked_numbers WHERE userId = :userId")
    fun getCountByUser(userId: String): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE userId = :userId AND phoneNumber = :phone)")
    suspend fun isBlocked(userId: String, phone: String): Boolean
}
