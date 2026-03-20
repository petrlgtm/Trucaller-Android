package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.byron.trucaller.data.model.SmsSpamReport
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsSpamDao {
    @Query("SELECT * FROM sms_spam_reports WHERE userId = :userId ORDER BY reportedAt DESC")
    fun getByUserId(userId: String): Flow<List<SmsSpamReport>>

    @Query("SELECT * FROM sms_spam_reports WHERE syncedToServer = 0")
    suspend fun getUnsynced(): List<SmsSpamReport>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: SmsSpamReport)

    @Query("UPDATE sms_spam_reports SET syncedToServer = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM sms_spam_reports WHERE userId = :userId AND senderNumber = :number)")
    suspend fun isReported(userId: String, number: String): Boolean

    @Query("SELECT COUNT(*) FROM sms_spam_reports WHERE userId = :userId")
    fun getCountByUser(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sms_spam_reports")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM sms_spam_reports WHERE id = :id")
    suspend fun delete(id: String)
}
