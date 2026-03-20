package com.byron.trucaller.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * HTTP client for communicating with the Trucaller backend API.
 *
 * Handles authentication (JWT), request building, and response parsing.
 */
object ApiClient {

    private const val TAG = "ApiClient"

    // Default to local backend — override via setBaseUrl()
    private var baseUrl = "https://trucaller-backend.onrender.com"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()
    }

    private var authToken: String? = null

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun setAuthToken(token: String?) {
        authToken = token
    }

    // ── Auth Endpoints ──────────────────────────────────────────────────

    suspend fun login(phoneNumber: String, password: String): ApiResult<TokenResponse> =
        post("/api/auth/login", mapOf("phoneNumber" to phoneNumber, "password" to password))

    suspend fun register(fullName: String, phoneNumber: String, password: String): ApiResult<TokenResponse> =
        post("/api/auth/register", mapOf(
            "fullName" to fullName,
            "phoneNumber" to phoneNumber,
            "password" to password
        ))

    suspend fun sendOtp(phoneNumber: String, purpose: String = "registration"): ApiResult<Map<String, Any>> =
        post("/api/auth/send-otp", mapOf("phoneNumber" to phoneNumber, "purpose" to purpose))

    suspend fun verifyOtp(phoneNumber: String, code: String): ApiResult<Unit> =
        post("/api/auth/verify-otp", mapOf("phoneNumber" to phoneNumber, "code" to code))

    suspend fun resetPassword(phoneNumber: String, code: String, newPassword: String): ApiResult<Unit> =
        post("/api/auth/reset-password", mapOf(
            "phoneNumber" to phoneNumber,
            "code" to code,
            "newPassword" to newPassword
        ))

    // ── Device Endpoints ────────────────────────────────────────────────

    suspend fun registerDevice(deviceData: Map<String, Any>): ApiResult<Map<String, Any>> =
        post("/api/devices/register", deviceData)

    suspend fun getDevices(userId: String): ApiResult<List<Map<String, Any>>> =
        get("/api/devices/$userId")

    suspend fun getDeviceIpLogs(deviceId: String): ApiResult<List<Map<String, Any>>> =
        get("/api/devices/$deviceId/ip-logs")

    suspend fun updateFcmToken(deviceId: String, fcmToken: String): ApiResult<Unit> =
        put("/api/devices/fcm-token", mapOf("deviceId" to deviceId, "fcmToken" to fcmToken))

    // ── Contact Endpoints ───────────────────────────────────────────────

    suspend fun uploadContacts(contacts: List<Map<String, Any>>): ApiResult<Unit> =
        post("/api/contacts/upload", mapOf("contacts" to contacts))

    suspend fun syncContacts(since: String? = null): ApiResult<List<Map<String, Any>>> =
        if (since != null) get("/api/contacts/sync?since=$since") else get("/api/contacts")

    // ── Caller ID Endpoints ─────────────────────────────────────────────

    suspend fun lookupCallerId(phoneNumber: String): ApiResult<Map<String, Any>> =
        get("/api/caller-id/lookup/$phoneNumber")

    suspend fun uploadCallerIds(entries: List<Map<String, Any>>): ApiResult<Unit> =
        post("/api/caller-id/upload", mapOf("entries" to entries))

    suspend fun reportSpamCall(phoneNumber: String, reason: String? = null): ApiResult<Unit> =
        post("/api/caller-id/report", mapOf("phoneNumber" to phoneNumber, "reason" to (reason ?: "")))

    // ── Stolen Reports ──────────────────────────────────────────────────

    suspend fun reportStolen(reportData: Map<String, Any>): ApiResult<Unit> =
        post("/api/stolen/report", reportData)

    suspend fun getStolenReports(): ApiResult<List<Map<String, Any>>> =
        get("/api/stolen/reports")

    // ── Alarms ──────────────────────────────────────────────────────────

    suspend fun triggerAlarm(alarmData: Map<String, Any>): ApiResult<Unit> =
        post("/api/alarms/trigger", alarmData)

    suspend fun getAlarmLogs(deviceId: String): ApiResult<List<Map<String, Any>>> =
        get("/api/alarms/logs/$deviceId")

    suspend fun updateAlarmLogResult(logId: String, result: String, notes: String? = null): ApiResult<Unit> =
        put("/api/alarms/logs/$logId/result", mapOf("result" to result, "notes" to (notes ?: "")))

    // ── SMS Spam ────────────────────────────────────────────────────────

    suspend fun reportSmsSpam(senderNumber: String, messageBody: String?, reason: String? = null): ApiResult<Unit> =
        post("/api/sms/report", mapOf(
            "senderNumber" to senderNumber,
            "messageBody" to (messageBody ?: ""),
            "reason" to (reason ?: "SMS spam")
        ))

    suspend fun bulkReportSmsSpam(reports: List<Map<String, String>>): ApiResult<Unit> =
        post("/api/sms/report/bulk", mapOf("reports" to reports))

    suspend fun checkSmsSpam(phoneNumber: String): ApiResult<Map<String, Any>> =
        get("/api/sms/check/$phoneNumber")

    suspend fun getSmsSpamReports(): ApiResult<List<Map<String, Any>>> =
        get("/api/sms/reports")

    // ── Geofences ─────────────────────────────────────────────────────────

    suspend fun createGeofence(
        deviceId: String,
        label: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 200
    ): ApiResult<Map<String, Any>> =
        post("/api/geofences", mapOf(
            "deviceId" to deviceId,
            "label" to label,
            "latitude" to latitude,
            "longitude" to longitude,
            "radiusMeters" to radiusMeters
        ))

    suspend fun getGeofences(deviceId: String): ApiResult<List<Map<String, Any>>> =
        get("/api/geofences/$deviceId")

    suspend fun getGeofenceDetail(geofenceId: String): ApiResult<Map<String, Any>> =
        get("/api/geofences/detail/$geofenceId")

    suspend fun updateGeofence(
        geofenceId: String,
        label: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusMeters: Int? = null,
        isActive: Boolean? = null
    ): ApiResult<Unit> {
        val body = mutableMapOf<String, Any>()
        label?.let { body["label"] = it }
        latitude?.let { body["latitude"] = it }
        longitude?.let { body["longitude"] = it }
        radiusMeters?.let { body["radiusMeters"] = it }
        isActive?.let { body["isActive"] = it }
        return put("/api/geofences/$geofenceId", body)
    }

    suspend fun deleteGeofence(geofenceId: String): ApiResult<Unit> =
        delete("/api/geofences/$geofenceId")

    suspend fun recordGeofenceEvent(
        geofenceId: String,
        deviceId: String,
        transitionType: String,
        latitude: Double,
        longitude: Double
    ): ApiResult<Map<String, Any>> =
        post("/api/geofence-events", mapOf(
            "geofenceId" to geofenceId,
            "deviceId" to deviceId,
            "transitionType" to transitionType,
            "latitude" to latitude,
            "longitude" to longitude
        ))

    suspend fun getGeofenceEvents(deviceId: String): ApiResult<List<Map<String, Any>>> =
        get("/api/geofence-events/$deviceId")

    suspend fun getGeofenceEventsByFence(geofenceId: String): ApiResult<List<Map<String, Any>>> =
        get("/api/geofence-events/fence/$geofenceId")

    suspend fun syncGeofenceEvent(eventData: Map<String, Any>): ApiResult<Map<String, Any>> =
        post("/api/geofence-events", eventData)

    // ── Blocked Numbers ─────────────────────────────────────────────────

    suspend fun blockNumber(data: Map<String, Any>): ApiResult<Unit> =
        post("/api/blocked/add", data)

    suspend fun getBlockedNumbers(): ApiResult<List<Map<String, Any>>> =
        get("/api/blocked")

    suspend fun unblockNumber(id: String): ApiResult<Unit> =
        delete("/api/blocked/$id")

    suspend fun isNumberBlocked(phoneNumber: String): ApiResult<Boolean> =
        get("/api/blocked/check/$phoneNumber")

    // ── Stolen Reports (additional) ────────────────────────────────────

    suspend fun updateStolenReportStatus(reportId: String, status: String): ApiResult<Unit> =
        put("/api/stolen/reports/$reportId/status", mapOf("status" to status))

    suspend fun recoverDevice(deviceId: String): ApiResult<Unit> =
        put("/api/devices/$deviceId/recover", mapOf("status" to "ACTIVE"))

    // ── SMS (additional) ───────────────────────────────────────────────

    suspend fun getSpamNumbers(skip: Int = 0, limit: Int = 50): ApiResult<List<Map<String, Any>>> =
        get("/api/sms/spam-numbers?skip=$skip&limit=$limit")

    // ── Admin Endpoints ──────────────────────────────────────────────────

    suspend fun adminLogin(email: String, password: String): ApiResult<TokenResponse> =
        post("/api/admin/login", mapOf("email" to email, "password" to password))

    suspend fun getDashboardStats(): ApiResult<Map<String, Any>> =
        get("/api/admin/dashboard")

    suspend fun getAdminUsers(skip: Int = 0, limit: Int = 20): ApiResult<List<String>> =
        get("/api/admin/users?skip=$skip&limit=$limit")

    suspend fun getAdminDevices(skip: Int = 0, limit: Int = 20): ApiResult<List<String>> =
        get("/api/admin/devices?skip=$skip&limit=$limit")

    suspend fun getAdminCallerIds(skip: Int = 0, limit: Int = 20): ApiResult<List<String>> =
        get("/api/admin/caller-ids?skip=$skip&limit=$limit")

    suspend fun getAdminStolenReports(): ApiResult<List<String>> =
        get("/api/admin/stolen-reports")

    suspend fun getAdminAlarmLogs(): ApiResult<List<String>> =
        get("/api/admin/alarm-logs")

    suspend fun getAdminSmsSpamReports(skip: Int = 0, limit: Int = 20): ApiResult<List<String>> =
        get("/api/admin/sms-spam-reports?skip=$skip&limit=$limit")

    suspend fun getAdminUserDetail(userId: String): ApiResult<Map<String, Any>> =
        get("/api/admin/users/$userId")

    suspend fun createAdminCallerId(
        id: String,
        phoneNumber: String,
        name: String,
        spamScore: Int,
        reportCount: Int,
        category: String,
        lastUpdated: String
    ): ApiResult<Unit> =
        post("/api/admin/caller-ids", mapOf(
            "id" to id,
            "phoneNumber" to phoneNumber,
            "name" to name,
            "spamScore" to spamScore,
            "reportCount" to reportCount,
            "category" to category,
            "lastUpdated" to lastUpdated
        ))

    suspend fun updateAdminCallerId(
        entryId: String,
        spamScore: Int? = null,
        category: String? = null,
        name: String? = null
    ): ApiResult<Unit> {
        val body = mutableMapOf<String, Any>()
        spamScore?.let { body["spamScore"] = it }
        category?.let { body["category"] = it }
        name?.let { body["name"] = it }
        return put("/api/admin/caller-ids/$entryId", body)
    }

    suspend fun deleteAdminCallerId(entryId: String): ApiResult<Unit> =
        delete("/api/admin/caller-ids/$entryId")

    suspend fun updateAdminStolenReportStatus(reportId: String, status: String): ApiResult<Unit> =
        put("/api/admin/stolen-reports/$reportId/status", mapOf("status" to status))

    suspend fun adminUpdateDeviceStatus(
        deviceId: String,
        status: String,
        changedBy: String,
        changedByName: String
    ): ApiResult<Unit> =
        put("/api/admin/devices/$deviceId/status", mapOf(
            "status" to status,
            "changedBy" to changedBy,
            "changedByName" to changedByName
        ))

    suspend fun updateAdminProfile(name: String, email: String): ApiResult<Unit> =
        put("/api/admin/profile", mapOf("name" to name, "email" to email))

    suspend fun updateAdminPassword(currentPassword: String, newPassword: String): ApiResult<Unit> =
        put("/api/admin/password", mapOf("currentPassword" to currentPassword, "newPassword" to newPassword))

    // ── Internal HTTP methods ───────────────────────────────────────────

    private suspend inline fun <reified T> get(path: String): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .get()
                .build()

            val response = client.newCall(request).execute()
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "GET $path failed", e)
            ApiResult(success = false, error = e.message ?: "Network error")
        }
    }

    private suspend inline fun <reified T> put(path: String, body: Any): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(body).toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("$baseUrl$path")
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .put(jsonBody)
                .build()

            val response = client.newCall(request).execute()
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "PUT $path failed", e)
            ApiResult(success = false, error = e.message ?: "Network error")
        }
    }

    private suspend inline fun <reified T> delete(path: String): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .delete()
                .build()

            val response = client.newCall(request).execute()
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "DELETE $path failed", e)
            ApiResult(success = false, error = e.message ?: "Network error")
        }
    }

    private suspend inline fun <reified T> post(path: String, body: Any): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = gson.toJson(body).toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url("$baseUrl$path")
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .post(jsonBody)
                .build()

            val response = client.newCall(request).execute()
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "POST $path failed", e)
            ApiResult(success = false, error = e.message ?: "Network error")
        }
    }

    private inline fun <reified T> parseResponse(response: okhttp3.Response): ApiResult<T> {
        val bodyString = response.body?.string() ?: ""
        return try {
            val type = object : TypeToken<ApiResult<T>>() {}.type
            gson.fromJson(bodyString, type)
        } catch (e: Exception) {
            if (response.isSuccessful) {
                ApiResult(success = true, message = "OK")
            } else {
                ApiResult(success = false, error = "HTTP ${response.code}: $bodyString")
            }
        }
    }
}

/**
 * Generic API response wrapper matching the backend's ApiResponse format.
 */
data class ApiResult<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)

data class TokenResponse(
    val token: String,
    val refreshToken: String? = null,
    val expiresIn: Long = 0,
    val userId: String = ""
)
