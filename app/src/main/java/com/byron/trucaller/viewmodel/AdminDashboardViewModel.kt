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
import com.byron.trucaller.data.repository.AlarmRepository
import com.byron.trucaller.data.repository.CallerIdRepository
import com.byron.trucaller.data.repository.DeviceRepository
import com.byron.trucaller.data.repository.SmsRepository
import com.byron.trucaller.data.repository.StolenReportRepository
import com.byron.trucaller.data.repository.UserRepository
import com.byron.trucaller.service.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class DashboardStats(
    val userCount: Int = 0,
    val deviceCount: Int = 0,
    val reportCount: Int = 0,
    val alarmCount: Int = 0,
    val callerIdCount: Int = 0,
    val smsSpamReportCount: Int = 0,
    val lastUpdated: Long = 0L
)

class AdminDashboardViewModel(
    application: Application,
    private val userRepository: UserRepository,
    private val deviceRepository: DeviceRepository,
    private val stolenReportRepository: StolenReportRepository,
    private val alarmRepository: AlarmRepository,
    private val callerIdRepository: CallerIdRepository,
    private val smsRepository: SmsRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AdminDashboardVM"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                AdminDashboardViewModel(
                    app,
                    app.container.userRepository,
                    app.container.deviceRepository,
                    app.container.stolenReportRepository,
                    app.container.alarmRepository,
                    app.container.callerIdRepository,
                    app.container.smsRepository
                )
            }
        }
    }

    private val _stats = MutableStateFlow(DashboardStats())
    val stats: StateFlow<DashboardStats> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        fetchStats()
    }

    fun refresh() {
        fetchStats()
    }

    private fun fetchStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val result = ApiClient.getDashboardStats()
                if (result.success && result.data != null) {
                    val data = result.data
                    _stats.value = DashboardStats(
                        userCount = (data["userCount"] as? Number)?.toInt() ?: 0,
                        deviceCount = (data["deviceCount"] as? Number)?.toInt() ?: 0,
                        reportCount = (data["reportCount"] as? Number)?.toInt() ?: 0,
                        alarmCount = (data["alarmCount"] as? Number)?.toInt() ?: 0,
                        callerIdCount = (data["callerIdCount"] as? Number)?.toInt() ?: 0,
                        smsSpamReportCount = (data["smsSpamReportCount"] as? Number)?.toInt() ?: 0,
                        lastUpdated = System.currentTimeMillis()
                    )
                    _isLoading.value = false
                    return@launch
                }
                // API failed — fall back to local Room counts
                Log.w(TAG, "API fetch failed: ${result.error}, falling back to local Room")
                fallbackToLocal()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch dashboard stats from API", e)
                fallbackToLocal()
            }
        }
    }

    private suspend fun fallbackToLocal() {
        try {
            val users = userRepository.getUserCount().firstOrNull() ?: 0
            val devices = deviceRepository.getDeviceCount().firstOrNull() ?: 0
            val reports = stolenReportRepository.getReportCount().firstOrNull() ?: 0
            val alarms = alarmRepository.getLogCount().firstOrNull() ?: 0
            val callerIds = callerIdRepository.getEntryCount().firstOrNull() ?: 0
            val smsSpam = smsRepository.getTotalSpamReportCount().firstOrNull() ?: 0

            _stats.value = DashboardStats(
                userCount = users,
                deviceCount = devices,
                reportCount = reports,
                alarmCount = alarms,
                callerIdCount = callerIds,
                smsSpamReportCount = smsSpam,
                lastUpdated = System.currentTimeMillis()
            )
            _error.value = "Showing local data (offline)"
        } catch (e: Exception) {
            Log.e(TAG, "Local fallback also failed", e)
            _error.value = "Unable to load dashboard stats"
        } finally {
            _isLoading.value = false
        }
    }
}
