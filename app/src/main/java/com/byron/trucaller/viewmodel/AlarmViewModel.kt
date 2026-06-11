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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    // tracks the deviceId of a remote device we sent REMOTE_ALARM to (null = none active)
    private val _alarmSentDeviceId = MutableStateFlow<String?>(null)
    val alarmSentDeviceId: StateFlow<String?> = _alarmSentDeviceId.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() { _actionMessage.value = null }

    init {
        // Keep alarmPlaying in sync with AlarmSoundManager (covers FCM-triggered alarms)
        viewModelScope.launch {
            AlarmSoundManager.alarmActiveFlow.collect { active ->
                _alarmPlaying.value = active
            }
        }
    }

    val allLogs: Flow<List<AlarmLog>> = alarmRepository.getAllLogs()
    val pendingCount: Flow<Int> = alarmRepository.getPendingCount()
    val logCount: Flow<Int> = alarmRepository.getLogCount()

    fun getLogsByDevice(deviceId: String): Flow<List<AlarmLog>> =
        alarmRepository.getLogsByDevice(deviceId)

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    private suspend fun isCurrentDevice(deviceId: String): Boolean {
        val device = deviceRepository.getDeviceById(deviceId) ?: return false
        val androidId = Settings.Secure.getString(
            getApplication<TruCallerApplication>().contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return device.deviceId == androidId
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
            // Use a stable local ID for the Room log entry
            val localLogId = "alm-${System.currentTimeMillis()}"
            val log = AlarmLog(
                id = localLogId,
                deviceId = deviceId,
                triggeredBy = triggeredBy,
                triggeredByName = triggeredByName,
                triggeredByRole = triggeredByRole,
                triggeredAt = nowIso(),
                type = type,
                result = AlarmResult.PENDING,
                notes = notes ?: "Awaiting device response…"
            )
            alarmRepository.insertLog(log)

            if (isCurrentDevice(deviceId)) {
                executeLocalAlarm(localLogId)
                return@launch
            }

            // Remote device — send via backend (which sends FCM)
            try {
                val result = ApiClient.triggerAlarm(
                    deviceId = deviceId,
                    type = type.name,
                    notes = notes
                )
                if (result.success) {
                    // PENDING: command reached backend and FCM was queued.
                    // The device will report SUCCESS/FAILED via PUT result callback.
                    // We keep the local log as PENDING — it reflects "command sent".
                    _actionMessage.value = when (type) {
                        AlarmType.REMOTE_ALARM     -> "Alarm command sent to device"
                        AlarmType.LOCATION_REQUEST -> "Location request sent to device"
                        AlarmType.LOCK_DEVICE      -> "Lock command sent to device"
                        AlarmType.WIPE_DATA        -> "Wipe command sent to device"
                        AlarmType.STOP_ALARM       -> "Stop alarm command sent"
                    }
                    if (type == AlarmType.REMOTE_ALARM) {
                        _alarmSentDeviceId.value = deviceId
                        viewModelScope.launch {
                            delay(30_000)
                            if (_alarmSentDeviceId.value == deviceId) _alarmSentDeviceId.value = null
                        }
                    }
                } else {
                    alarmRepository.updateResult(
                        localLogId, AlarmResult.FAILED,
                        "Backend rejected command: ${result.error}"
                    )
                    _actionMessage.value = "Command failed: ${result.error}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "triggerAlarm API call failed", e)
                alarmRepository.updateResult(
                    localLogId, AlarmResult.FAILED, "Network error: ${e.message}"
                )
                _actionMessage.value = "Command failed: network error"
            }
        }
    }

    // ── Local execution (this device is the target) ──────────────────────

    private var alarmTimeoutJob: Job? = null

    fun executeLocalAlarm(logId: String) {
        viewModelScope.launch {
            try {
                AlarmSoundManager.triggerAlarm(getApplication())
                _alarmPlaying.value = true
                alarmRepository.updateResult(logId, AlarmResult.SUCCESS, "Alarm sounded on device")

                alarmTimeoutJob?.cancel()
                alarmTimeoutJob = viewModelScope.launch {
                    delay(30_000)
                    stopAlarm()
                }
            } catch (e: Exception) {
                alarmRepository.updateResult(logId, AlarmResult.FAILED, "Alarm failed: ${e.message}")
                _alarmPlaying.value = false
            }
        }
    }

    fun stopAlarm() {
        alarmTimeoutJob?.cancel()
        alarmTimeoutJob = null
        AlarmSoundManager.stopAlarm()
        _alarmPlaying.value = false
    }

    fun stopRemoteAlarm(deviceId: String) {
        viewModelScope.launch {
            if (isCurrentDevice(deviceId)) {
                _alarmSentDeviceId.value = null
                stopAlarm()
                return@launch
            }
            try {
                ApiClient.triggerAlarm(deviceId = deviceId, type = AlarmType.STOP_ALARM.name, notes = null)
                _alarmSentDeviceId.value = null
                _actionMessage.value = "Stop command sent to device"
            } catch (e: Exception) {
                Log.e(TAG, "stopRemoteAlarm failed", e)
                _actionMessage.value = "Failed to send stop command"
            }
        }
    }

    // ── Request Location ─────────────────────────────────────────────────

    fun requestLocation(
        deviceId: String,
        triggeredBy: String,
        triggeredByName: String,
        triggeredByRole: String
    ) {
        viewModelScope.launch {
            val localLogId = "alm-loc-${System.currentTimeMillis()}"
            alarmRepository.insertLog(
                AlarmLog(
                    id = localLogId,
                    deviceId = deviceId,
                    triggeredBy = triggeredBy,
                    triggeredByName = triggeredByName,
                    triggeredByRole = triggeredByRole,
                    triggeredAt = nowIso(),
                    type = AlarmType.LOCATION_REQUEST,
                    result = AlarmResult.PENDING,
                    notes = "Location request initiated"
                )
            )

            if (isCurrentDevice(deviceId)) {
                executeLocalLocationRequest(localLogId, triggeredBy)
                return@launch
            }

            try {
                val result = ApiClient.triggerAlarm(
                    deviceId = deviceId,
                    type = AlarmType.LOCATION_REQUEST.name,
                    notes = "Location request"
                )
                if (result.success) {
                    _actionMessage.value = "Location request sent to device"
                } else {
                    alarmRepository.updateResult(
                        localLogId, AlarmResult.FAILED,
                        "Backend rejected: ${result.error}"
                    )
                    _actionMessage.value = "Location request failed: ${result.error}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "requestLocation API call failed", e)
                alarmRepository.updateResult(localLogId, AlarmResult.FAILED, "Network error: ${e.message}")
                _actionMessage.value = "Location request failed: network error"
            }
        }
    }

    fun executeLocalLocationRequest(logId: String, triggeredBy: String) {
        viewModelScope.launch {
            try {
                val app = getApplication<TruCallerApplication>()
                val ownerId = app.container.userPreferences.loggedInUserId.first() ?: triggeredBy
                DeviceRegistrationService(app, app.container.deviceRepository)
                    .registerOrUpdateDevice(ownerId)
                alarmRepository.updateResult(logId, AlarmResult.SUCCESS, "Location updated")
                _actionMessage.value = "Location updated successfully"
            } catch (e: Exception) {
                alarmRepository.updateResult(logId, AlarmResult.FAILED, "Location failed: ${e.message}")
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
            val localLogId = "alm-lock-${System.currentTimeMillis()}"
            alarmRepository.insertLog(
                AlarmLog(
                    id = localLogId,
                    deviceId = deviceId,
                    triggeredBy = triggeredBy,
                    triggeredByName = triggeredByName,
                    triggeredByRole = triggeredByRole,
                    triggeredAt = nowIso(),
                    type = AlarmType.LOCK_DEVICE,
                    result = AlarmResult.PENDING,
                    notes = "Device lock initiated"
                )
            )

            if (isCurrentDevice(deviceId)) {
                executeLocalLockDevice(localLogId)
                return@launch
            }

            try {
                val result = ApiClient.triggerAlarm(
                    deviceId = deviceId,
                    type = AlarmType.LOCK_DEVICE.name,
                    notes = "Lock device"
                )
                if (result.success) {
                    _actionMessage.value = "Lock command sent to device"
                } else {
                    alarmRepository.updateResult(
                        localLogId, AlarmResult.FAILED,
                        "Backend rejected: ${result.error}"
                    )
                    _actionMessage.value = "Lock request failed: ${result.error}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "lockDevice API call failed", e)
                alarmRepository.updateResult(localLogId, AlarmResult.FAILED, "Network error: ${e.message}")
                _actionMessage.value = "Lock request failed: network error"
            }
        }
    }

    fun executeLocalLockDevice(logId: String) {
        viewModelScope.launch {
            try {
                val locked = DeviceAdminHelper.lockDevice(getApplication())
                if (locked) {
                    alarmRepository.updateResult(logId, AlarmResult.SUCCESS, "Device locked")
                    _actionMessage.value = "Device locked successfully"
                } else {
                    alarmRepository.updateResult(
                        logId, AlarmResult.FAILED, "Device admin not active"
                    )
                    _actionMessage.value = "Cannot lock: Device admin permission required"
                }
            } catch (e: Exception) {
                alarmRepository.updateResult(logId, AlarmResult.FAILED, "Lock failed: ${e.message}")
                _actionMessage.value = "Device lock failed"
            }
        }
    }

    // ── Wipe Device ──────────────────────────────────────────────────────

    fun wipeDevice(
        deviceId: String,
        triggeredBy: String,
        triggeredByName: String,
        triggeredByRole: String
    ) {
        viewModelScope.launch {
            val localLogId = "alm-wipe-${System.currentTimeMillis()}"
            alarmRepository.insertLog(
                AlarmLog(
                    id = localLogId,
                    deviceId = deviceId,
                    triggeredBy = triggeredBy,
                    triggeredByName = triggeredByName,
                    triggeredByRole = triggeredByRole,
                    triggeredAt = nowIso(),
                    type = AlarmType.WIPE_DATA,
                    result = AlarmResult.PENDING,
                    notes = "Remote wipe initiated"
                )
            )
            try {
                val result = ApiClient.triggerAlarm(
                    deviceId = deviceId,
                    type = AlarmType.WIPE_DATA.name,
                    notes = "Remote factory reset"
                )
                if (result.success) {
                    _actionMessage.value = "Wipe command sent. Device will factory reset."
                } else {
                    alarmRepository.updateResult(localLogId, AlarmResult.FAILED, "Backend rejected: ${result.error}")
                    _actionMessage.value = "Wipe request failed: ${result.error}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "wipeDevice API call failed", e)
                alarmRepository.updateResult(localLogId, AlarmResult.FAILED, "Network error: ${e.message}")
                _actionMessage.value = "Wipe request failed: network error"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAlarm()
    }
}
