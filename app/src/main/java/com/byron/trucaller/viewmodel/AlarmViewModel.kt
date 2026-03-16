package com.byron.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.AlarmLog
import com.byron.trucaller.data.model.AlarmResult
import com.byron.trucaller.data.model.AlarmType
import com.byron.trucaller.data.repository.AlarmRepository
import com.byron.trucaller.data.repository.DeviceRepository
import com.byron.trucaller.service.AlarmSoundManager
import com.byron.trucaller.service.DeviceRegistrationService
import com.byron.trucaller.util.DeviceAdminHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmViewModel(
    application: Application,
    private val alarmRepository: AlarmRepository,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val _alarmPlaying = MutableStateFlow(false)
    val alarmPlaying: StateFlow<Boolean> = _alarmPlaying.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() { _actionMessage.value = null }

    val allLogs: Flow<List<AlarmLog>> = alarmRepository.getAllLogs()
    val pendingCount: Flow<Int> = alarmRepository.getPendingCount()
    val logCount: Flow<Int> = alarmRepository.getLogCount()

    fun getLogsByDevice(deviceId: String): Flow<List<AlarmLog>> = alarmRepository.getLogsByDevice(deviceId)

    fun triggerAlarm(
        deviceId: String,
        triggeredBy: String,
        triggeredByName: String,
        triggeredByRole: String,
        type: AlarmType = AlarmType.REMOTE_ALARM,
        notes: String? = null
    ) {
        viewModelScope.launch {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            val logId = "alm-${System.currentTimeMillis()}"
            val log = AlarmLog(
                id = logId,
                deviceId = deviceId,
                triggeredBy = triggeredBy,
                triggeredByName = triggeredByName,
                triggeredByRole = triggeredByRole,
                triggeredAt = now,
                type = type,
                result = AlarmResult.PENDING,
                notes = notes ?: "Alarm triggered, waiting for playback..."
            )
            alarmRepository.insertLog(log)

            // Play actual alarm sound
            try {
                AlarmSoundManager.triggerAlarm(getApplication())
                _alarmPlaying.value = true

                // Mark as SUCCESS since alarm started playing
                alarmRepository.updateResult(logId, AlarmResult.SUCCESS.name, "Alarm sounded for 30 seconds")

                // Auto-update state when alarm stops
                viewModelScope.launch {
                    delay(30_000)
                    _alarmPlaying.value = false
                }
            } catch (e: Exception) {
                // Mark as FAILED if alarm couldn't play
                alarmRepository.updateResult(logId, AlarmResult.FAILED.name, "Alarm failed: ${e.message}")
                _alarmPlaying.value = false
            }
        }
    }

    fun stopAlarm() {
        AlarmSoundManager.stopAlarm()
        _alarmPlaying.value = false
    }

    fun requestLocation(
        deviceId: String,
        triggeredBy: String,
        triggeredByName: String,
        triggeredByRole: String
    ) {
        viewModelScope.launch {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            val logId = "alm-loc-${System.currentTimeMillis()}"
            val log = AlarmLog(
                id = logId,
                deviceId = deviceId,
                triggeredBy = triggeredBy,
                triggeredByName = triggeredByName,
                triggeredByRole = triggeredByRole,
                triggeredAt = now,
                type = AlarmType.LOCATION_REQUEST,
                result = AlarmResult.PENDING,
                notes = "Location request initiated"
            )
            alarmRepository.insertLog(log)

            try {
                // Refresh IP/location data for the device
                val app = getApplication<TruCallerApplication>()
                val regService = DeviceRegistrationService(app, deviceRepository)
                regService.registerOrUpdateDevice(triggeredBy)
                alarmRepository.updateResult(logId, AlarmResult.SUCCESS.name, "Location updated via IP geolocation")
                _actionMessage.value = "Location updated successfully"
            } catch (e: Exception) {
                alarmRepository.updateResult(logId, AlarmResult.FAILED.name, "Location request failed: ${e.message}")
                _actionMessage.value = "Location request failed"
            }
        }
    }

    fun lockDevice(
        deviceId: String,
        triggeredBy: String,
        triggeredByName: String,
        triggeredByRole: String
    ) {
        viewModelScope.launch {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            val logId = "alm-lock-${System.currentTimeMillis()}"
            val log = AlarmLog(
                id = logId,
                deviceId = deviceId,
                triggeredBy = triggeredBy,
                triggeredByName = triggeredByName,
                triggeredByRole = triggeredByRole,
                triggeredAt = now,
                type = AlarmType.LOCK_DEVICE,
                result = AlarmResult.PENDING,
                notes = "Device lock initiated"
            )
            alarmRepository.insertLog(log)

            try {
                val app = getApplication<TruCallerApplication>()
                val locked = DeviceAdminHelper.lockDevice(app)
                if (locked) {
                    alarmRepository.updateResult(logId, AlarmResult.SUCCESS.name, "Device locked successfully")
                    _actionMessage.value = "Device locked successfully"
                } else {
                    alarmRepository.updateResult(logId, AlarmResult.FAILED.name, "Device admin not active - cannot lock")
                    _actionMessage.value = "Cannot lock: Device admin permission required"
                }
            } catch (e: Exception) {
                alarmRepository.updateResult(logId, AlarmResult.FAILED.name, "Lock failed: ${e.message}")
                _actionMessage.value = "Device lock failed"
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                AlarmViewModel(app, app.container.alarmRepository, app.container.deviceRepository)
            }
        }
    }
}
