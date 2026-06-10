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
private const val WS_STALE_THRESHOLD_MS = 60_000L

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

enum class DashboardDataSource { LOADING, WEBSOCKET, HTTP, LOCAL_OFFLINE }

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

    private val _dataSource = MutableStateFlow(DashboardDataSource.LOADING)
    val dataSource: StateFlow<DashboardDataSource> = _dataSource.asStateFlow()

    private val gson = Gson()

    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var wsJob: Job? = null
    private var fallbackJob: Job? = null

    @Volatile private var wsConnected = false
    @Volatile private var lastWsMessageAt = 0L

    init {
        fetchStatsHttp()
        connectWebSocket()
    }

    fun refresh() {
        // Always force-fetch from server regardless of WS state
        fetchStatsHttp()
        // Reconnect WS if not connected or stale (no message in WS_STALE_THRESHOLD_MS)
        val wsStale = wsConnected && lastWsMessageAt > 0 &&
                (System.currentTimeMillis() - lastWsMessageAt) > WS_STALE_THRESHOLD_MS
        if (!wsConnected || wsStale) {
            Log.d(TAG, "refresh: reconnecting WebSocket (connected=$wsConnected, stale=$wsStale)")
            webSocket?.cancel()
            webSocket = null
            wsConnected = false
            wsJob?.cancel()
            connectWebSocket()
        }
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
                        lastWsMessageAt = System.currentTimeMillis()
                        parseAndEmit(text)
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "WebSocket failure: ${t.message}")
                        wsConnected = false
                        startFallbackPolling()
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closed: $reason")
                        wsConnected = false
                    }
                }

                webSocket = wsClient.newWebSocket(request, listener)

                delay(WS_RECONNECT_DELAY_MS)
                if (!wsConnected) {
                    Log.w(TAG, "WebSocket not connected after ${WS_RECONNECT_DELAY_MS}ms, retrying...")
                    webSocket?.cancel()
                    webSocket = null
                    startFallbackPolling()
                    delay(WS_RECONNECT_DELAY_MS * 2)
                } else {
                    while (isActive && wsConnected) {
                        // Detect zombie connections: if no message for WS_STALE_THRESHOLD_MS, reconnect
                        if (lastWsMessageAt > 0 &&
                            (System.currentTimeMillis() - lastWsMessageAt) > WS_STALE_THRESHOLD_MS
                        ) {
                            Log.w(TAG, "WebSocket stale — no message in ${WS_STALE_THRESHOLD_MS}ms, reconnecting")
                            wsConnected = false
                        }
                        delay(5_000L)
                    }
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
                    _error.value = null
                    // Only update source to HTTP if WS isn't actively pushing
                    if (_dataSource.value != DashboardDataSource.WEBSOCKET) {
                        _dataSource.value = DashboardDataSource.HTTP
                    }
                    return@launch
                }
                Log.w(TAG, "HTTP stats fetch returned error: ${result.error}")
                if (!silent) {
                    _isLoading.value = false
                    // Only fall back to local if we have no server data yet
                    if (_stats.value.lastUpdated == 0L) {
                        fallbackToLocal()
                    } else {
                        _error.value = result.error ?: "Could not refresh — showing last known data"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "HTTP stats fetch failed", e)
                if (!silent) {
                    _isLoading.value = false
                    if (_stats.value.lastUpdated == 0L) {
                        fallbackToLocal()
                    } else {
                        _error.value = "Network error — showing last known data. Pull to retry."
                    }
                }
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
                _dataSource.value = DashboardDataSource.WEBSOCKET
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
            _error.value = "Offline — showing this device's local data only. Pull to retry."
            _dataSource.value = DashboardDataSource.LOCAL_OFFLINE
        } catch (e: Exception) {
            Log.e(TAG, "Local fallback failed", e)
            _error.value = "Unable to load dashboard stats. Pull to retry."
            _dataSource.value = DashboardDataSource.LOCAL_OFFLINE
        } finally {
            _isLoading.value = false
        }
    }
}
