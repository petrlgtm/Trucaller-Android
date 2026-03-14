package com.example.trucaller.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import com.example.trucaller.data.model.Device
import com.example.trucaller.data.model.DeviceStatus
import com.example.trucaller.data.model.IpLog
import com.example.trucaller.data.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceRegistrationService(
    private val context: Context,
    private val deviceRepository: DeviceRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    /**
     * Registers the current physical device for the given user.
     * If the user already has a device registered, updates lastSeen and logs a new IP.
     */
    suspend fun registerOrUpdateDevice(userId: String) {
        val now = dateFormat.format(Date())
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        val existingDevice = deviceRepository.getFirstDeviceByUser(userId)

        // Fetch real IP info
        val ipInfo = fetchIpInfo()

        if (existingDevice != null) {
            // Update existing device with current info
            val updated = existingDevice.copy(
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                osVersion = "Android ${Build.VERSION.RELEASE}",
                lastIp = ipInfo.ip,
                lastSeen = now
            )
            deviceRepository.updateDevice(updated)

            // Log new IP entry
            val ipLog = IpLog(
                id = "ipl-${System.currentTimeMillis()}",
                deviceId = existingDevice.id,
                ipAddress = ipInfo.ip,
                isp = ipInfo.isp,
                city = ipInfo.city,
                country = ipInfo.country,
                latitude = ipInfo.latitude,
                longitude = ipInfo.longitude,
                networkType = getNetworkType(),
                timestamp = now
            )
            deviceRepository.insertIpLog(ipLog)
        } else {
            // Register new device
            val deviceId = "dev-${userId}-${System.currentTimeMillis()}"
            val device = Device(
                id = deviceId,
                userId = userId,
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                osVersion = "Android ${Build.VERSION.RELEASE}",
                deviceId = androidId ?: "unknown",
                status = DeviceStatus.ACTIVE,
                lastIp = ipInfo.ip,
                lastSeen = now,
                registeredAt = now
            )
            deviceRepository.insertDevice(device)

            // Log initial IP
            val ipLog = IpLog(
                id = "ipl-${System.currentTimeMillis()}",
                deviceId = deviceId,
                ipAddress = ipInfo.ip,
                isp = ipInfo.isp,
                city = ipInfo.city,
                country = ipInfo.country,
                latitude = ipInfo.latitude,
                longitude = ipInfo.longitude,
                networkType = getNetworkType(),
                timestamp = now
            )
            deviceRepository.insertIpLog(ipLog)
        }
    }

    private fun getNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "unknown"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "unknown"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }

    /**
     * Fetches real IP geolocation info from ip-api.com (free, no API key needed).
     * Falls back to defaults if the request fails.
     */
    private suspend fun fetchIpInfo(): IpInfo {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("http://ip-api.com/json/?fields=query,isp,city,country,lat,lon")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    IpInfo(
                        ip = json.optString("query", "0.0.0.0"),
                        isp = json.optString("isp", "Unknown ISP"),
                        city = json.optString("city", "Unknown"),
                        country = json.optString("country", "Unknown"),
                        latitude = json.optDouble("lat", 0.0),
                        longitude = json.optDouble("lon", 0.0)
                    )
                } else {
                    IpInfo.default()
                }
            } catch (e: Exception) {
                IpInfo.default()
            }
        }
    }
}

data class IpInfo(
    val ip: String,
    val isp: String,
    val city: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        fun default() = IpInfo(
            ip = "0.0.0.0",
            isp = "Unknown ISP",
            city = "Unknown",
            country = "Unknown",
            latitude = 0.0,
            longitude = 0.0
        )
    }
}
