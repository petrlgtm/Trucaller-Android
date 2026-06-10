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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

private const val WS_RECONNECT_DELAY_MS = 5_000L
private const val POLL_FALLBACK_INTERVAL_MS = 30_000L

data class DashboardStats(
    val userCount: Int = 0,
    val deviceCount: Int = 0,
    val reportCount: Int = 0,
    val alarmCount: Int = 0,
    val callerIdCount: Int = 0,
    val smsSpamReportCount: Int = 0,
    val pendingReports: Int = 0,
    val activeDevices: Int = 0,
    val verifiedReports: Int = 0,
    val resolvedReports: Int = 0,
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
        private const val WS_PATH = "/api/admin/ws/dashboard"

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

    private val gson = Gson()

    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // no read timeout for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private var wsJob: Job? = null
    private var fallbackJob: Job? = null
    private var wsConnected = false

    init {
        fetchStatsHttp()
        connectWebSocket()
    }

    fun refresh() {
        if (wsConnected) return // WebSocket is pushing updates; no manual poll needed
        fetchStatsHttp()
    }

    private fun connectWebSocket() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            while (isActive) {
                val url = ApiClient.buildWsUrl(WS_PATH)
                if (ApiClient.getAuthToken() == null) {
                    Log.w(TAG, "No auth token — skipping WebSocket, using HTTP poll")
                    startFallbackPolling()
                    break
                }

                val request = Request.Builder().url(url).build()
                val listener = object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket connected")
                        wsConnected = true
                        fallbackJob?.cancel()
                        _error.value = null
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        parseAndEmit(text)
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "WebSocket failure: ${t.message}")
                        wsConnected = false
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closed: $reason")
                        wsConnected = false
                    }
                }

                webSocket = wsClient.newWebSocket(request, listener)

                // Wait for WS_RECONNECT_DELAY_MS, then reconnect if still not connected
                delay(WS_RECONNECT_DELAY_MS)
                if (!wsConnected) {
                    Log.w(TAG, "WebSocket not connected after ${WS_RECONNECT_DELAY_MS}ms, retrying...")
                    webSocket?.cancel()
                    webSocket = null
                    startFallbackPolling()
                    delay(WS_RECONNECT_DELAY_MS * 2)
                } else {
                    // WebSocket is live — loop will keep it alive via reconnect if needed
                    while (isActive && wsConnected) {
                        delay(1_000L)
                    }
                    // WebSocket dropped — cancel and reconnect
                    webSocket?.cancel()
                    webSocket = null
                }
            }
        }
    }

    private fun startFallbackPolling() {
        if (fallbackJob?.isActive == true) return
        fallbackJob = viewModelScope.launch {
            while (isActive && !wsConnected) {
                fetchStatsHttp(silent = true)
                delay(POLL_FALLBACK_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wsJob?.cancel()
        fallbackJob?.cancel()
        webSocket?.cancel()
        wsClient.dispatcher.executorService.shutdown()
    }

    private fun fetchStatsHttp(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _isLoading.value = true
                _error.value = null
            }
            try {
                val result = ApiClient.getDashboardStats()
                if (result.success && result.data != null) {
                    _stats.value = mapToStats(result.data)
                    _isLoading.value = false
                    return@launch
                }
                Log.w(TAG, "HTTP fetch failed: ${result.error}")
                fallbackToLocal(silent)
            } catch (e: Exception) {
                Log.e(TAG, "HTTP stats fetch failed", e)
                fallbackToLocal(silent)
            }
        }
    }

    private fun parseAndEmit(json: String) {
        try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val envelope: Map<String, Any> = gson.fromJson(json, type)
            if (envelope["success"] == true) {
                @Suppress("UNCHECKED_CAST")
                val data = envelope["data"] as? Map<String, Any> ?: return
                _stats.value = mapToStats(data)
                _isLoading.value = false
                _error.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WebSocket frame", e)
        }
    }

    private fun mapToStats(data: Map<String, Any>): DashboardStats = DashboardStats(
        userCount = (data["userCount"] as? Number)?.toInt() ?: 0,
        deviceCount = (data["deviceCount"] as? Number)?.toInt() ?: 0,
        reportCount = (data["reportCount"] as? Number)?.toInt() ?: 0,
        alarmCount = (data["alarmCount"] as? Number)?.toInt() ?: 0,
        callerIdCount = (data["callerIdCount"] as? Number)?.toInt() ?: 0,
        smsSpamReportCount = (data["smsSpamReportCount"] as? Number)?.toInt() ?: 0,
        pendingReports = (data["pendingReports"] as? Number)?.toInt() ?: 0,
        activeDevices = (data["activeDevices"] as? Number)?.toInt() ?: 0,
        verifiedReports = (data["verifiedReports"] as? Number)?.toInt() ?: 0,
        resolvedReports = (data["resolvedReports"] as? Number)?.toInt() ?: 0,
        lastUpdated = System.currentTimeMillis()
    )

    private suspend fun fallbackToLocal(silent: Boolean = false) {
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
            if (!silent) {
                _error.value = "Showing local data (offline)"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Local fallback failed", e)
            if (!silent) {
                _error.value = "Unable to load dashboard stats"
            }
        } finally {
            if (!silent) {
                _isLoading.value = false
            }
        }
    }
}
