package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.ReportStatus
import com.byron.trucaller.data.model.StolenReport
import kotlinx.coroutines.flow.Flow

@Dao
interface StolenReportDao {
    @Query("SELECT * FROM stolen_reports ORDER BY reportedAt DESC")
    fun getAll(): Flow<List<StolenReport>>

    @Query("SELECT * FROM stolen_reports WHERE deviceId = :deviceId ORDER BY reportedAt DESC")
    fun getByDeviceId(deviceId: String): Flow<List<StolenReport>>

    @Query("SELECT * FROM stolen_reports WHERE reportedBy = :userId ORDER BY reportedAt DESC")
    fun getByUserId(userId: String): Flow<List<StolenReport>>

    @Query("SELECT * FROM stolen_reports WHERE id = :id")
    suspend fun getById(id: String): StolenReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: StolenReport)

    @Update
    suspend fun update(report: StolenReport)

    @Query("UPDATE stolen_reports SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: ReportStatus)

    @Query("SELECT COUNT(*) FROM stolen_reports")
    fun countFlow(): Flow<Int>
}
