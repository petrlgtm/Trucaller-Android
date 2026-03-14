package com.example.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.trucaller.TruCallerApplication
import com.example.trucaller.data.model.AlarmLog
import com.example.trucaller.data.model.AlarmResult
import com.example.trucaller.data.model.AlarmType
import com.example.trucaller.data.repository.AlarmRepository
import com.example.trucaller.service.AlarmSoundManager
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
    private val alarmRepository: AlarmRepository
) : AndroidViewModel(application) {

    private val _alarmPlaying = MutableStateFlow(false)
    val alarmPlaying: StateFlow<Boolean> = _alarmPlaying.asStateFlow()

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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                AlarmViewModel(app, app.container.alarmRepository)
            }
        }
    }
}
