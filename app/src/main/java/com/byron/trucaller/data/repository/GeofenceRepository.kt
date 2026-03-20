package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.GeofenceDao
import com.byron.trucaller.data.dao.GeofenceEventDao
import com.byron.trucaller.data.model.Geofence
import com.byron.trucaller.data.model.GeofenceEvent
import kotlinx.coroutines.flow.Flow

class GeofenceRepository(
    private val geofenceDao: GeofenceDao,
    private val geofenceEventDao: GeofenceEventDao
) {
    // ── Geofence CRUD ────────────────────────────────────────────────────

    fun getGeofencesByDevice(deviceId: String): Flow<List<Geofence>> =
        geofenceDao.getByDeviceId(deviceId)

    fun getActiveGeofencesByDevice(deviceId: String): Flow<List<Geofence>> =
        geofenceDao.getActiveByDeviceId(deviceId)

    suspend fun getGeofenceById(id: String): Geofence? =
        geofenceDao.getById(id)

    suspend fun insertGeofence(geofence: Geofence) =
        geofenceDao.insert(geofence)

    suspend fun insertGeofences(geofences: List<Geofence>) =
        geofenceDao.insertAll(geofences)

    suspend fun updateGeofence(geofence: Geofence) =
        geofenceDao.update(geofence)

    suspend fun updateGeofenceActive(id: String, isActive: Boolean) =
        geofenceDao.updateActive(id, isActive)

    suspend fun incrementTriggeredCount(id: String) =
        geofenceDao.incrementTriggeredCount(id)

    suspend fun deleteGeofence(geofence: Geofence) =
        geofenceDao.delete(geofence)

    suspend fun deleteGeofenceById(id: String) =
        geofenceDao.deleteById(id)

    fun getGeofenceCountByDevice(deviceId: String): Flow<Int> =
        geofenceDao.getCountByDevice(deviceId)

    // ── Geofence Event CRUD ──────────────────────────────────────────────

    fun getEventsByDevice(deviceId: String): Flow<List<GeofenceEvent>> =
        geofenceEventDao.getByDeviceId(deviceId)

    fun getEventsByGeofence(geofenceId: String): Flow<List<GeofenceEvent>> =
        geofenceEventDao.getByGeofenceId(geofenceId)

    suspend fun getEventById(id: String): GeofenceEvent? =
        geofenceEventDao.getById(id)

    suspend fun insertEvent(event: GeofenceEvent) =
        geofenceEventDao.insert(event)

    suspend fun insertEvents(events: List<GeofenceEvent>) =
        geofenceEventDao.insertAll(events)

    suspend fun getUnsyncedEvents(): List<GeofenceEvent> =
        geofenceEventDao.getUnsynced()

    suspend fun markEventSynced(id: String) =
        geofenceEventDao.markSynced(id)

    suspend fun deleteEventsByGeofence(geofenceId: String) =
        geofenceEventDao.deleteByGeofenceId(geofenceId)

    fun getEventCountByDevice(deviceId: String): Flow<Int> =
        geofenceEventDao.getCountByDevice(deviceId)

    fun getEventCountByGeofence(geofenceId: String): Flow<Int> =
        geofenceEventDao.getCountByGeofence(geofenceId)
}
