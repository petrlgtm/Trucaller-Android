package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.Device
import com.byron.trucaller.data.model.DeviceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices")
    fun getAll(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getById(id: String): Device?

    @Query("SELECT * FROM devices WHERE userId = :userId")
    fun getByUserId(userId: String): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE userId = :userId LIMIT 1")
    suspend fun getFirstByUserId(userId: String): Device?

    @Query("SELECT * FROM devices WHERE userId = :userId LIMIT 1")
    fun observeFirstByUserId(userId: String): Flow<Device?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: Device)

    @Update
    suspend fun update(device: Device)

    @Query("UPDATE devices SET status = :status WHERE id = :deviceId")
    suspend fun updateStatus(deviceId: String, status: DeviceStatus)

    @Query("SELECT COUNT(*) FROM devices")
    fun countFlow(): Flow<Int>

    @Query("UPDATE devices SET fcmToken = :token WHERE id = :deviceId")
    suspend fun updateFcmToken(deviceId: String, token: String)

    @Query("SELECT * FROM devices WHERE userId = :userId AND status != 'STOLEN' LIMIT 1")
    suspend fun getActiveDeviceByUser(userId: String): Device?
}
