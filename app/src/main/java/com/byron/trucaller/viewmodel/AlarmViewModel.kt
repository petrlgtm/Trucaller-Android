package com.byron.trucaller.viewmodel

import android.app.Application
import android.provider.Settings
import android.util.Log
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
import com.byron.trucaller.service.ApiClient
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

    companion object {
        private const val TAG = "AlarmViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                AlarmViewModel(app, app.container.alarmRepository, app.container.deviceRepository)
            }
        }
    }

    private val _alarmPlaying = MutableStateFlow(false)
    val alarmPlaying: StateFlow<Boolean> = _alarmPlaying.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() { _actionMessage.value = null }

    val allLogs: Flow<List<AlarmLog>> = alarmRepository.getAllLogs()
    val pendingCount: Flow<Int> = alarmRepository.getPendingCount()
    val logCount: Flow<Int> = alarmRepository.getLogCount()

    fun getLogsByDevice(deviceId: String): Flow<List<AlarmLog>> = alarmRepository.getLogsByDevice(deviceId)

    /**
     * Returns true if the given device record's hardware ID matches this physical device.
     */
    private suspend fun isCurrentDevice(deviceId: String): Boolean {
        val device = deviceRepository.getDeviceById(deviceId) ?: return false
        val currentAndroidId = Settings.Secure.getString(
            getApplication<TruCallerApplication>().contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return device.deviceId == currentAndroidId
    }

    // ── Trigger Alarm ────────────────────────────────────────────────────

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
                notes = notes ?: "Remote alarm requested, awaiting delivery..."
            )
            alarmRepository.insertLog(log)

            if (isCurrentDevice(deviceId)) {
                // Target is this physical device — execute locally
                executeLocalAlarm(logId)
            } else {
                // Target is a remote device — send via backend API + FCM
                try {
                    val result = ApiClient.triggerAlarm(mapOf(
                        "deviceId" to deviceId,
                        "action" to "REMOTE_ALARM",
                        "triggeredBy" to triggeredBy,
                        "triggeredByName" to triggeredByName,
                        "triggeredByRole" to triggeredByRole,
                        "notes" to (notes ?: "Remote alarm triggered"),
                        "logId" to logId
                    ))
                    if (result.success) {
                        alarmRepository.updateResult(logId, AlarmResult.SUCCESS, "Alarm command sent to device via FCM")
                        _actionMessage.value = "Alarm command sent to device"
                    } else {
                        alarmRepository.updateResult(logId, AlarmResult.FAILED, "Backend rejected alarm: ${result.error}")
                        _actionMessage.value = "Alarm request failed: ${result.error}"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "triggerAlarm API call failed", e)
                    alarmRepository.updateResult(logId, AlarmResult.FAILED, "Network error: ${e.message}")
                    _actionMessage.value = "Alarm request failed: network error"
                }
            }
        }
    }

    /**
     * Executes alarm sound locally on this device.
     * Called directly when target is the current device, or from TruCallerMessagingService via FCM.
     */
    fun executeLocalAlarm(logId: String) {
        viewModelScope.launch {
            try {
                AlarmSoundManager.triggerAlarm(getApplication())
                _alarmPlaying.value = true
                alarmRepository.updateResult(logId, AlarmResult.SUCCESS, "Alarm sounded for 30 seconds")

                viewModelScope.launch {
                    delay(30_000)
                    _alarmPlaying.value = false
                }
            } catch (e: Exception) {
                alarmRepository.updateResult(logId, AlarmResult.FAILED, "Alarm failed: ${e.message}")
                _alarmPlaying.value = false
            }
        }
    }

    fun stopAlarm() {
        AlarmSoundManager.stopAlarm()
        _alarmPlaying.value = false
    }

    // ── Request Location ─────────────────────────────────────────────────

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

            if (isCurrentDevice(deviceId)) {
                // Target is this physical device — execute locally
                executeLocalLocationRequest(logId, triggeredBy)
            } else {
                // Target is a remote device — send via backend API + FCM
                try {
                    val result = ApiClient.triggerAlarm(mapOf(
                        "deviceId" to deviceId,
                        "action" to "LOCATION_REQUEST",
                        "triggeredBy" to triggeredBy,
                        "triggeredByName" to triggeredByName,
                        "triggeredByRole" to triggeredByRole,
                        "notes" to "Location request via FCM",
                        "logId" to logId
                    ))
                    if (result.success) {
                        alarmRepository.updateResult(logId, AlarmResult.SUCCESS, "Location request sent to device via FCM")
                        _actionMessage.value = "Location request sent to device"
                    } else {
                        alarmRepository.updateResult(logId, AlarmResult.FAILED, "Backend rejected location request: ${result.error}")
                        _actionMessage.value = "Location request failed: ${result.error}"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "requestLocation API call failed", e)
                    alarmRepository.updateResult(logId, AlarmResult.FAILED, "Network error: ${e.message}")
                    _actionMessage.value = "Location request failed: network error"
                }
            }
        }
    }

    /**
     * Executes location refresh locally on this device.
     * Called directly when target is the current device, or from TruCallerMessagingService via FCM.
     */
    fun executeLocalLocationRequest(logId: String, triggeredBy: String) {
        viewModelScope.launch {
            try {
                val app = getApplication<TruCallerApplication>()
                val regService = DeviceRegistrationService(app, deviceRepository)
                regService.registerOrUpdateDevice(triggeredBy)
                alarmRepository.updateResult(logId, AlarmResult.SUCCESS, "Location updated via IP geolocation")
                _actionMessage.value = "Location updated successfully"
            } catch (e: Exception) {
                alarmRepository.updateResult(logId, AlarmResult.FAILED, "Location request failed: ${e.message}")
                _actionMessage.value = "Location request failed"
            }
        }
    }

    // ── Lock Device ──────────────────────────────────────────────────────

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

            if (isCurrentDevice(deviceId)) {
                // Target is this physical device — execute locally
                executeLocalLockDevice(logId)
            } else {
                // Target is a remote device — send via backend API + FCM
                try {
                    val result = ApiClient.triggerAlarm(mapOf(
                        "deviceId" to deviceId,
                        "action" to "LOCK_DEVICE",
                        "triggeredBy" to triggeredBy,
                        "triggeredByName" to triggeredByName,
                        "triggeredByRole" to triggeredByRole,
                        "notes" to "Lock device via FCM",
                        "logId" to logId
                    ))
                    if (result.success) {
                        alarmRepository.updateResult(logId, AlarmResult.SUCCESS, "Lock command sent to device via FCM")
                        _actionMessage.value = "Lock command sent to device"
                    } else {
                        alarmRepository.updateResult(logId, AlarmResult.FAILED, "Backend rejected lock: ${result.error}")
                        _actionMessage.value = "Lock request failed: ${result.error}"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "lockDevice API call failed", e)
                    alarmRepository.updateResult(logId, AlarmResult.FAILED, "Network error: ${e.message}")
                    _actionMessage.value = "Lock request failed: network error"
                }
            }
        }
    }

    /**
     * Executes device lock locally on this device.
     * Called directly when target is the current device, or from TruCallerMessagingService via FCM.
     */
    fun executeLocalLockDevice(logId: String) {
        viewModelScope.launch {
            try {
                val app = getApplication<TruCallerApplication>()
                val locked = DeviceAdminHelper.lockDevice(app)
                if (locked) {
                    alarmRepository.updateResult(logId, AlarmResult.SUCCESS, "Device locked successfully")
                    _actionMessage.value = "Device locked successfully"
                } else {
                    alarmRepository.updateResult(logId, AlarmResult.FAILED, "Device admin not active - cannot lock")
                    _actionMessage.value = "Cannot lock: Device admin permission required"
                }
            } catch (e: Exception) {
                alarmRepository.updateResult(logId, AlarmResult.FAILED, "Lock failed: ${e.message}")
                _actionMessage.value = "Device lock failed"
            }
        }
    }
}
