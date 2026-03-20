package com.byron.trucaller.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.Geofence
import com.byron.trucaller.data.model.GeofenceEvent
import com.byron.trucaller.data.repository.GeofenceRepository
import com.byron.trucaller.service.LocationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeofenceViewModel(
    application: Application,
    private val geofenceRepository: GeofenceRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GeofenceViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                GeofenceViewModel(app, app.container.geofenceRepository)
            }
        }
    }

    private val locationService = LocationService(application)

    // ── UI State ──────────────────────────────────────────────────────────

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /** Current device location (latitude, longitude) for placing new geofences. */
    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    fun clearError() { _error.value = null }
    fun clearStatusMessage() { _statusMessage.value = null }

    // ── Data Flows ────────────────────────────────────────────────────────

    fun getGeofences(deviceId: String): Flow<List<Geofence>> =
        geofenceRepository.getGeofencesByDevice(deviceId)

    fun getEvents(deviceId: String): Flow<List<GeofenceEvent>> =
        geofenceRepository.getEventsByDevice(deviceId)

    fun getGeofenceCount(deviceId: String): Flow<Int> =
        geofenceRepository.getGeofenceCountByDevice(deviceId)

    // ── Location ──────────────────────────────────────────────────────────

    fun fetchCurrentLocation() {
        viewModelScope.launch {
            try {
                val location = locationService.getCurrentLocation()
                if (location.latitude != 0.0 || location.longitude != 0.0) {
                    _currentLocation.value = Pair(location.latitude, location.longitude)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get current location for geofence", e)
            }
        }
    }

    // ── CRUD Operations ───────────────────────────────────────────────────

    fun addGeofence(
        deviceId: String,
        label: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                val geofence = Geofence(
                    id = "gf-${System.currentTimeMillis()}",
                    deviceId = deviceId,
                    label = label,
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters,
                    isActive = true,
                    createdAt = now,
                    triggeredCount = 0
                )
                geofenceRepository.insertGeofence(geofence)
                _statusMessage.value = "Geofence \"$label\" created"
                Log.d(TAG, "Geofence created: ${geofence.id} at ($latitude, $longitude) r=${radiusMeters}m")
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create geofence", e)
                _error.value = "Failed to create geofence: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleGeofenceActive(geofenceId: String, isActive: Boolean) {
        viewModelScope.launch {
            try {
                geofenceRepository.updateGeofenceActive(geofenceId, isActive)
                _statusMessage.value = if (isActive) "Geofence enabled" else "Geofence disabled"
                Log.d(TAG, "Geofence $geofenceId active=$isActive")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle geofence", e)
                _error.value = "Failed to update geofence: ${e.message}"
            }
        }
    }

    fun deleteGeofence(geofenceId: String) {
        viewModelScope.launch {
            try {
                // Delete associated events first, then the geofence
                geofenceRepository.deleteEventsByGeofence(geofenceId)
                geofenceRepository.deleteGeofenceById(geofenceId)
                _statusMessage.value = "Geofence deleted"
                Log.d(TAG, "Geofence $geofenceId deleted")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete geofence $geofenceId", e)
                _error.value = "Failed to delete geofence: ${e.message}"
            }
        }
    }

    fun updateGeofence(geofence: Geofence) {
        viewModelScope.launch {
            try {
                geofenceRepository.updateGeofence(geofence)
                _statusMessage.value = "Geofence updated"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update geofence", e)
                _error.value = "Failed to update geofence: ${e.message}"
            }
        }
    }
}
