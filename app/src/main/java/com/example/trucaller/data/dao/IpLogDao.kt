package com.example.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.trucaller.data.model.IpLog
import kotlinx.coroutines.flow.Flow

@Dao
interface IpLogDao {
    @Query("SELECT * FROM ip_logs WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    fun getByDeviceId(deviceId: String): Flow<List<IpLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ipLog: IpLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ipLogs: List<IpLog>)

    @Query("SELECT * FROM ip_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<IpLog>>
}
