package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.BlockingSchedule
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockingScheduleDao {

    @Query("SELECT * FROM blocking_schedules WHERE userId = :userId ORDER BY createdAt DESC")
    fun getByUserId(userId: String): Flow<List<BlockingSchedule>>

    @Query("SELECT * FROM blocking_schedules WHERE userId = :userId AND isActive = 1")
    suspend fun getActiveByUserId(userId: String): List<BlockingSchedule>

    @Query("SELECT * FROM blocking_schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BlockingSchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: BlockingSchedule)

    @Update
    suspend fun update(schedule: BlockingSchedule)

    @Query("UPDATE blocking_schedules SET isActive = :isActive WHERE id = :id")
    suspend fun updateActive(id: String, isActive: Boolean)

    @Delete
    suspend fun delete(schedule: BlockingSchedule)

    @Query("DELETE FROM blocking_schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM blocking_schedules WHERE userId = :userId")
    fun getCountByUser(userId: String): Flow<Int>
}
