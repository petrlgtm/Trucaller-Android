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
import com.byron.trucaller.data.model.BlockingSchedule
import com.byron.trucaller.data.model.ScheduleBlockType
import com.byron.trucaller.data.repository.BlockingScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BlockingScheduleViewModel(
    application: Application,
    private val repository: BlockingScheduleRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BlockingScheduleVM"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                BlockingScheduleViewModel(app, app.container.blockingScheduleRepository)
            }
        }

        /** Convert a day-of-week bitmask to a list of short labels. */
        fun daysToLabels(bitmask: Int): List<String> {
            val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            return labels.filterIndexed { index, _ -> bitmask and (1 shl index) != 0 }
        }

        /** Format minutes-since-midnight (0-1439) to "HH:mm" 12-hour string. */
        fun formatTime(minutes: Int): String {
            val h = minutes / 60
            val m = minutes % 60
            val amPm = if (h < 12) "AM" else "PM"
            val displayH = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            return "%d:%02d %s".format(displayH, m, amPm)
        }

        /** Check whether a schedule is currently active right now. */
        fun isCurrentlyActive(schedule: BlockingSchedule): Boolean {
            if (!schedule.isActive) return false
            val cal = Calendar.getInstance()
            val dayBit = 1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1) // Sunday=1 -> bit 0
            if (schedule.daysOfWeek and dayBit == 0) return false
            val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            return if (schedule.startTimeMinutes <= schedule.endTimeMinutes) {
                nowMinutes in schedule.startTimeMinutes..schedule.endTimeMinutes
            } else {
                // Overnight schedule (e.g. 22:00 -> 06:00)
                nowMinutes >= schedule.startTimeMinutes || nowMinutes <= schedule.endTimeMinutes
            }
        }
    }

    private val _schedules = MutableStateFlow<List<BlockingSchedule>>(emptyList())
    val schedules: StateFlow<List<BlockingSchedule>> = _schedules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _editSchedule = MutableStateFlow<BlockingSchedule?>(null)
    val editSchedule: StateFlow<BlockingSchedule?> = _editSchedule.asStateFlow()

    fun clearStatusMessage() { _statusMessage.value = null }

    fun loadSchedules(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getSchedulesByUser(userId).collect { list ->
                    _schedules.value = list
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load schedules", e)
                _statusMessage.value = "Failed to load schedules: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun loadScheduleForEdit(scheduleId: String) {
        viewModelScope.launch {
            try {
                _editSchedule.value = repository.getScheduleById(scheduleId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load schedule $scheduleId", e)
                _statusMessage.value = "Failed to load schedule"
            }
        }
    }

    fun clearEditSchedule() {
        _editSchedule.value = null
    }

    fun addSchedule(
        userId: String,
        name: String,
        startTimeMinutes: Int,
        endTimeMinutes: Int,
        daysOfWeek: Int,
        blockType: ScheduleBlockType
    ) {
        viewModelScope.launch {
            try {
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                val schedule = BlockingSchedule(
                    id = "bs-${System.currentTimeMillis()}",
                    userId = userId,
                    name = name.trim(),
                    isActive = true,
                    startTimeMinutes = startTimeMinutes.coerceIn(0, 1439),
                    endTimeMinutes = endTimeMinutes.coerceIn(0, 1439),
                    daysOfWeek = daysOfWeek,
                    blockType = blockType,
                    createdAt = now
                )
                repository.insertSchedule(schedule)
                _statusMessage.value = "Schedule created"
                Log.d(TAG, "Schedule created: ${schedule.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create schedule", e)
                _statusMessage.value = "Failed to create schedule: ${e.message}"
            }
        }
    }

    fun updateSchedule(
        scheduleId: String,
        userId: String,
        name: String,
        startTimeMinutes: Int,
        endTimeMinutes: Int,
        daysOfWeek: Int,
        blockType: ScheduleBlockType,
        isActive: Boolean,
        createdAt: String
    ) {
        viewModelScope.launch {
            try {
                val schedule = BlockingSchedule(
                    id = scheduleId,
                    userId = userId,
                    name = name.trim(),
                    isActive = isActive,
                    startTimeMinutes = startTimeMinutes.coerceIn(0, 1439),
                    endTimeMinutes = endTimeMinutes.coerceIn(0, 1439),
                    daysOfWeek = daysOfWeek,
                    blockType = blockType,
                    createdAt = createdAt
                )
                repository.updateSchedule(schedule)
                _statusMessage.value = "Schedule updated"
                Log.d(TAG, "Schedule updated: $scheduleId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update schedule $scheduleId", e)
                _statusMessage.value = "Failed to update schedule: ${e.message}"
            }
        }
    }

    fun toggleSchedule(scheduleId: String, isActive: Boolean) {
        viewModelScope.launch {
            try {
                repository.toggleScheduleActive(scheduleId, isActive)
                _statusMessage.value = if (isActive) "Schedule enabled" else "Schedule disabled"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle schedule $scheduleId", e)
                _statusMessage.value = "Failed to toggle schedule: ${e.message}"
            }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            try {
                repository.deleteScheduleById(scheduleId)
                _statusMessage.value = "Schedule deleted"
                Log.d(TAG, "Schedule deleted: $scheduleId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete schedule $scheduleId", e)
                _statusMessage.value = "Failed to delete schedule: ${e.message}"
            }
        }
    }
}
