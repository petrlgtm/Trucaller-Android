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
import com.byron.trucaller.data.model.IpLog
import com.byron.trucaller.data.model.NearbyDevice
import com.byron.trucaller.data.repository.DeviceRepository
import com.byron.trucaller.service.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Holds aggregated ISP analysis data: unique ISPs with their occurrence count.
 */
data class IspAnalysis(
    val isp: String,
    val count: Int,
    val percentage: Float
)

/**
 * Holds aggregated geographic summary data: country/city pairs with frequency.
 */
data class GeoSummary(
    val country: String,
    val city: String,
    val count: Int,
    val percentage: Float
)

/**
 * Represents a detected network change between consecutive IP logs.
 */
data class NetworkChange(
    val fromIp: String,
    val toIp: String,
    val fromIsp: String,
    val toIsp: String,
    val fromNetworkType: String,
    val toNetworkType: String,
    val fromCity: String,
    val toCity: String,
    val timestamp: String
)

data class NetworkForensicsState(
    val isLoading: Boolean = true,
    val timeline: List<IpLog> = emptyList(),
    val ispAnalysis: List<IspAnalysis> = emptyList(),
    val geoSummary: List<GeoSummary> = emptyList(),
    val networkChanges: List<NetworkChange> = emptyList(),
    val lastSeenLog: IpLog? = null,
    val error: String? = null
)

class NetworkForensicsViewModel(
    application: Application,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(NetworkForensicsState())
    val state: StateFlow<NetworkForensicsState> = _state.asStateFlow()

    fun loadForensics(deviceId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                // Try remote API first
                val apiResult = try {
                    ApiClient.getDeviceForensics(deviceId)
                } catch (e: Exception) {
                    Log.w(TAG, "API forensics fetch failed, falling back to local", e)
                    null
                }

                val ipLogs: List<IpLog> = if (apiResult?.success == true && apiResult.data != null) {
                    // Backend returns { deviceId, ipLogs: [...], uniqueIsps: [...] }
                    @Suppress("UNCHECKED_CAST")
                    val rawList = (apiResult.data["ipLogs"] as? List<*>) ?: emptyList<Any>()
                    rawList.mapNotNull { item ->
                        val map = item as? Map<*, *> ?: return@mapNotNull null
                        try {
                            IpLog(
                                id = map["id"]?.toString() ?: return@mapNotNull null,
                                deviceId = map["deviceId"]?.toString() ?: deviceId,
                                ipAddress = map["ipAddress"]?.toString() ?: "",
                                isp = map["isp"]?.toString() ?: "Unknown",
                                city = map["city"]?.toString() ?: "Unknown",
                                country = map["country"]?.toString() ?: "Unknown",
                                latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0,
                                longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0,
                                networkType = map["networkType"]?.toString() ?: "unknown",
                                timestamp = map["timestamp"]?.toString() ?: "",
                                startTime = map["startTime"]?.toString() ?: map["timestamp"]?.toString() ?: "",
                                lastSeen = map["lastSeen"]?.toString() ?: map["timestamp"]?.toString() ?: "",
                                nearbyDevices = (map["nearbyDevices"] as? List<*>)?.mapNotNull { bItem ->
                                    val bMap = bItem as? Map<*, *> ?: return@mapNotNull null
                                    NearbyDevice(
                                        name = bMap["name"]?.toString() ?: "Unknown",
                                        macAddress = bMap["macAddress"]?.toString() ?: "??",
                                        rssi = (bMap["rssi"] as? Number)?.toInt() ?: 0,
                                        type = bMap["type"]?.toString() ?: "UNKNOWN"
                                    )
                                } ?: emptyList()
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse forensics entry", e)
                            null
                        }
                    }
                } else {
                    // Fallback to local Room data
                    deviceRepository.getIpLogsByDevice(deviceId).firstOrNull() ?: emptyList()
                }

                val sorted = ipLogs.sortedByDescending { it.timestamp }
                val total = sorted.size.coerceAtLeast(1)

                // ISP Analysis
                val ispGroups = sorted.groupBy { it.isp }
                val ispAnalysis = ispGroups.map { (isp, logs) ->
                    IspAnalysis(
                        isp = isp,
                        count = logs.size,
                        percentage = logs.size.toFloat() / total * 100f
                    )
                }.sortedByDescending { it.count }

                // Geographic Summary
                val geoGroups = sorted.groupBy { "${it.country}|${it.city}" }
                val geoSummary = geoGroups.map { (key, logs) ->
                    val parts = key.split("|")
                    GeoSummary(
                        country = parts.getOrElse(0) { "Unknown" },
                        city = parts.getOrElse(1) { "Unknown" },
                        count = logs.size,
                        percentage = logs.size.toFloat() / total * 100f
                    )
                }.sortedByDescending { it.count }

                // Network Changes: detect consecutive entries where ISP, IP, or network type changed
                val networkChanges = mutableListOf<NetworkChange>()
                for (i in 0 until sorted.size - 1) {
                    val current = sorted[i]
                    val previous = sorted[i + 1]
                    if (current.isp != previous.isp ||
                        current.networkType != previous.networkType ||
                        current.city != previous.city
                    ) {
                        networkChanges.add(
                            NetworkChange(
                                fromIp = previous.ipAddress,
                                toIp = current.ipAddress,
                                fromIsp = previous.isp,
                                toIsp = current.isp,
                                fromNetworkType = previous.networkType,
                                toNetworkType = current.networkType,
                                fromCity = previous.city,
                                toCity = current.city,
                                timestamp = current.timestamp
                            )
                        )
                    }
                }

                _state.value = NetworkForensicsState(
                    isLoading = false,
                    timeline = sorted,
                    ispAnalysis = ispAnalysis,
                    geoSummary = geoSummary,
                    networkChanges = networkChanges,
                    lastSeenLog = sorted.firstOrNull(),
                    error = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load forensics for device $deviceId", e)
                _state.value = NetworkForensicsState(
                    isLoading = false,
                    error = "Failed to load network forensics: ${e.message}"
                )
            }
        }
    }

    companion object {
        private const val TAG = "NetworkForensicsVM"
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                NetworkForensicsViewModel(app, app.container.deviceRepository)
            }
        }
    }
}
