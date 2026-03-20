package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.CallRecording
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordingDao {

    @Query("SELECT * FROM call_recordings WHERE userId = :userId ORDER BY startTime DESC")
    fun getByUserId(userId: String): Flow<List<CallRecording>>

    @Query("SELECT * FROM call_recordings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CallRecording?

    @Query("SELECT * FROM call_recordings WHERE userId = :userId AND phoneNumber = :phoneNumber ORDER BY startTime DESC")
    fun getByPhoneNumber(userId: String, phoneNumber: String): Flow<List<CallRecording>>

    @Query("SELECT * FROM call_recordings WHERE userId = :userId AND isStarred = 1 ORDER BY startTime DESC")
    fun getStarred(userId: String): Flow<List<CallRecording>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: CallRecording)

    @Update
    suspend fun update(recording: CallRecording)

    @Query("UPDATE call_recordings SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarred(id: String, isStarred: Boolean)

    @Query("UPDATE call_recordings SET duration = :duration, fileSize = :fileSize WHERE id = :id")
    suspend fun updateDurationAndSize(id: String, duration: Long, fileSize: Long)

    @Delete
    suspend fun delete(recording: CallRecording)

    @Query("DELETE FROM call_recordings WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM call_recordings WHERE userId = :userId")
    fun getCountByUser(userId: String): Flow<Int>
}
