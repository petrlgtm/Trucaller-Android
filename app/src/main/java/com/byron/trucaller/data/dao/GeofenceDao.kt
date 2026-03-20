package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.byron.trucaller.data.model.Geofence
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceDao {
    @Query("SELECT * FROM geofences WHERE deviceId = :deviceId ORDER BY createdAt DESC")
    fun getByDeviceId(deviceId: String): Flow<List<Geofence>>

    @Query("SELECT * FROM geofences WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Geofence?

    @Query("SELECT * FROM geofences WHERE deviceId = :deviceId AND isActive = 1")
    fun getActiveByDeviceId(deviceId: String): Flow<List<Geofence>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(geofence: Geofence)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(geofences: List<Geofence>)

    @Update
    suspend fun update(geofence: Geofence)

    @Query("UPDATE geofences SET isActive = :isActive WHERE id = :id")
    suspend fun updateActive(id: String, isActive: Boolean)

    @Query("UPDATE geofences SET triggeredCount = triggeredCount + 1 WHERE id = :id")
    suspend fun incrementTriggeredCount(id: String)

    @Delete
    suspend fun delete(geofence: Geofence)

    @Query("DELETE FROM geofences WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM geofences WHERE deviceId = :deviceId")
    fun getCountByDevice(deviceId: String): Flow<Int>
}
