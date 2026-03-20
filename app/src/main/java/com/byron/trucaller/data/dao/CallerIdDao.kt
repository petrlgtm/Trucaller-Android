package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.SpamCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface CallerIdDao {
    @Query("SELECT * FROM caller_id_entries LIMIT 500")
    fun getAll(): Flow<List<CallerIdEntry>>

    @Query("SELECT * FROM caller_id_entries LIMIT 500")
    suspend fun getAllOnce(): List<CallerIdEntry>

    @Query("SELECT * FROM caller_id_entries WHERE phoneNumber LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' LIMIT 500")
    fun search(query: String): Flow<List<CallerIdEntry>>

    @Query("SELECT * FROM caller_id_entries WHERE phoneNumber = :phone")
    suspend fun getByPhone(phone: String): CallerIdEntry?

    @Query("SELECT * FROM caller_id_entries WHERE id = :id")
    suspend fun getById(id: String): CallerIdEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CallerIdEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CallerIdEntry>)

    @Update
    suspend fun update(entry: CallerIdEntry)

    @Delete
    suspend fun delete(entry: CallerIdEntry)

    @Query("DELETE FROM caller_id_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE caller_id_entries SET category = :category, lastUpdated = :lastUpdated WHERE id IN (:ids)")
    suspend fun updateCategoryByIds(ids: List<String>, category: SpamCategory, lastUpdated: String)

    @Query("SELECT * FROM caller_id_entries WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CallerIdEntry>

    @Query("SELECT COUNT(*) FROM caller_id_entries")
    fun countFlow(): Flow<Int>
}
