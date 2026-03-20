package com.byron.trucaller.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.byron.trucaller.data.model.Device
import com.byron.trucaller.data.model.DeviceStatus
import com.byron.trucaller.data.model.IpLog
import com.byron.trucaller.data.repository.DeviceRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
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
    private val locationService = LocationService(context)

    /**
     * Registers the current physical device for the given user.
     * If the user already has a device registered, updates lastSeen and logs a new IP.
     * Uses GPS location when permission granted, falls back to IP geolocation.
     */
    suspend fun registerOrUpdateDevice(userId: String) {
        val now = dateFormat.format(Date())
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        // Get FCM token for push notifications
        val fcmToken = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.w("DeviceRegService", "FCM token unavailable", e)
            null
        }

        // Find an active (non-stolen) device for this user, or the first device
        val activeDevice = deviceRepository.getActiveDeviceByUser(userId)
        val existingDevice = activeDevice ?: deviceRepository.getFirstDeviceByUser(userId)

        // Fetch IP info (for IP address and ISP)
        val ipInfo = fetchIpInfo()

        // Get accurate GPS location if permission granted, otherwise use IP-based location
        val gpsLocation = if (locationService.hasLocationPermission()) {
            locationService.getCurrentLocation()
        } else null

        // Merge: prefer GPS for coordinates/city/country, IP for ip/isp
        val latitude = if (gpsLocation != null && gpsLocation.latitude != 0.0) gpsLocation.latitude else ipInfo.latitude
        val longitude = if (gpsLocation != null && gpsLocation.longitude != 0.0) gpsLocation.longitude else ipInfo.longitude
        val city = if (gpsLocation != null && gpsLocation.city != "Unknown") gpsLocation.city else ipInfo.city
        val country = if (gpsLocation != null && gpsLocation.country != "Unknown") gpsLocation.country else ipInfo.country

        fun createIpLog(deviceId: String, idPrefix: String = "ipl") = IpLog(
            id = "$idPrefix-${System.currentTimeMillis()}",
            deviceId = deviceId,
            ipAddress = ipInfo.ip,
            isp = ipInfo.isp,
            city = city,
            country = country,
            latitude = latitude,
            longitude = longitude,
            networkType = getNetworkType(),
            timestamp = now
        )

        // If the only existing device is STOLEN, create a new device record
        if (existingDevice != null && existingDevice.status == DeviceStatus.STOLEN) {
            // Log IP on the stolen device (keeps tracking it)
            deviceRepository.insertIpLog(createIpLog(existingDevice.id, "ipl-stolen"))

            // Register a new device for the user (they're logging in from a different phone)
            val newDeviceId = "dev-${userId}-${System.currentTimeMillis()}"
            val newDevice = Device(
                id = newDeviceId,
                userId = userId,
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                osVersion = "Android ${Build.VERSION.RELEASE}",
                deviceId = androidId ?: "unknown",
                status = DeviceStatus.ACTIVE,
                lastIp = ipInfo.ip,
                lastSeen = now,
                registeredAt = now,
                fcmToken = fcmToken
            )
            deviceRepository.insertDevice(newDevice)
            deviceRepository.insertIpLog(createIpLog(newDeviceId))
        } else if (existingDevice != null) {
            // Update existing active device with current info
            val updated = existingDevice.copy(
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                osVersion = "Android ${Build.VERSION.RELEASE}",
                lastIp = ipInfo.ip,
                lastSeen = now,
                fcmToken = fcmToken ?: existingDevice.fcmToken
            )
            deviceRepository.updateDevice(updated)
            deviceRepository.insertIpLog(createIpLog(existingDevice.id))
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
                registeredAt = now,
                fcmToken = fcmToken
            )
            deviceRepository.insertDevice(device)
            deviceRepository.insertIpLog(createIpLog(deviceId))
        }

        // Sync device to backend MongoDB
        try {
            ApiClient.registerDevice(mapOf(
                "userId" to userId,
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                "osVersion" to "Android ${Build.VERSION.RELEASE}",
                "deviceId" to (androidId ?: "unknown"),
                "status" to "ACTIVE",
                "lastIp" to ipInfo.ip,
                "fcmToken" to (fcmToken ?: ""),
                "isp" to ipInfo.isp,
                "city" to city,
                "country" to country,
                "latitude" to latitude,
                "longitude" to longitude,
                "networkType" to getNetworkType()
            ))
        } catch (e: Exception) {
            Log.w("DeviceRegService", "Backend sync failed (offline?)", e)
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
                try {
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
                } finally {
                    connection.disconnect()
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
