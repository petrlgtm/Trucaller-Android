package com.byron.trucaller.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.byron.trucaller.data.model.GeofenceEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceEventDao {
    @Query("SELECT * FROM geofence_events WHERE deviceId = :deviceId ORDER BY timestamp DESC")
    fun getByDeviceId(deviceId: String): Flow<List<GeofenceEvent>>

    @Query("SELECT * FROM geofence_events WHERE geofenceId = :geofenceId ORDER BY timestamp DESC")
    fun getByGeofenceId(geofenceId: String): Flow<List<GeofenceEvent>>

    @Query("SELECT * FROM geofence_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GeofenceEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: GeofenceEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<GeofenceEvent>)

    @Query("SELECT * FROM geofence_events WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<GeofenceEvent>

    @Query("UPDATE geofence_events SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM geofence_events WHERE geofenceId = :geofenceId")
    suspend fun deleteByGeofenceId(geofenceId: String)

    @Query("SELECT COUNT(*) FROM geofence_events WHERE deviceId = :deviceId")
    fun getCountByDevice(deviceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM geofence_events WHERE geofenceId = :geofenceId")
    fun getCountByGeofence(geofenceId: String): Flow<Int>
}
