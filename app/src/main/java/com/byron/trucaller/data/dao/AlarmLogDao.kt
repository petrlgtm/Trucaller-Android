package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.byron.trucaller.data.model.AlarmLog
import com.byron.trucaller.data.model.AlarmResult
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmLogDao {
    @Query("SELECT * FROM alarm_logs ORDER BY triggeredAt DESC")
    fun getAll(): Flow<List<AlarmLog>>

    @Query("SELECT * FROM alarm_logs WHERE deviceId = :deviceId ORDER BY triggeredAt DESC")
    fun getByDeviceId(deviceId: String): Flow<List<AlarmLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarmLog: AlarmLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alarmLogs: List<AlarmLog>)

    @Query("UPDATE alarm_logs SET result = :result, notes = :notes WHERE id = :id")
    suspend fun updateResult(id: String, result: AlarmResult, notes: String)

    @Query("SELECT COUNT(*) FROM alarm_logs WHERE result = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM alarm_logs")
    fun countFlow(): Flow<Int>
}
