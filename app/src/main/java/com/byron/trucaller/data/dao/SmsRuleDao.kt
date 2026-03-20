package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.SmsRule
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsRuleDao {

    @Query("SELECT * FROM sms_rules WHERE userId = :userId ORDER BY priority DESC, createdAt DESC")
    fun getByUserId(userId: String): Flow<List<SmsRule>>

    @Query("SELECT * FROM sms_rules WHERE userId = :userId AND isActive = 1 ORDER BY priority DESC")
    suspend fun getActiveByUserId(userId: String): List<SmsRule>

    @Query("SELECT * FROM sms_rules WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SmsRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SmsRule)

    @Update
    suspend fun update(rule: SmsRule)

    @Query("UPDATE sms_rules SET isActive = :isActive WHERE id = :id")
    suspend fun updateActive(id: String, isActive: Boolean)

    @Delete
    suspend fun delete(rule: SmsRule)

    @Query("DELETE FROM sms_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM sms_rules WHERE userId = :userId")
    fun getCountByUser(userId: String): Flow<Int>
}
