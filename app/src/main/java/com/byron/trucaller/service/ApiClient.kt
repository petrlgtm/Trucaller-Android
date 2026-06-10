package com.byron.trucaller.service

import android.content.Intent
import android.util.Log
import com.byron.trucaller.BuildConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
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
    private var baseUrl = "https://trucaller-backend-sh3t.onrender.com"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    /**
     * Certificate pinner for the production backend (Render.com).
     * Pins the Let's Encrypt ISRG Root X1 intermediate CA used by Render.
     * Update these pins when the backend's TLS certificate chain changes.
     */
    private val certificatePinner = CertificatePinner.Builder()
        .add(
            "trucaller-backend-sh3t.onrender.com",
            // Let's Encrypt ISRG Root X1 — SHA-256 pin
            "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="
        )
        .build()

    // Used only for token refresh calls — no authenticator to prevent recursion
    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply { if (!BuildConfig.DEBUG) certificatePinner(certificatePinner) }
            .build()
    }

    private val tokenAuthenticator = okhttp3.Authenticator { _, response ->
        // If the failing request had no Authorization header, don't retry
        if (response.request.header("Authorization") == null) return@Authenticator null

        synchronized(refreshLock) {
            val currentRefreshToken = refreshToken
            if (currentRefreshToken == null) {
                broadcastSessionExpired()
                return@synchronized null
            }

            // Another thread may have already refreshed — check if current token differs
            val failingToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            val currentAccessToken = authToken
            if (currentAccessToken != null && failingToken != currentAccessToken) {
                return@synchronized response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            try {
                val refreshBody = gson.toJson(mapOf("refreshToken" to currentRefreshToken))
                    .toRequestBody(JSON_MEDIA)
                val refreshRequest = Request.Builder()
                    .url("$baseUrl/api/auth/refresh")
                    .post(refreshBody)
                    .build()

                val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                if (!refreshResponse.isSuccessful) {
                    authToken = null
                    refreshToken = null
                    broadcastSessionExpired()
                    return@synchronized null
                }

                val bodyStr = refreshResponse.body?.string() ?: return@synchronized null
                val type = object : TypeToken<ApiResult<TokenResponse>>() {}.type
                val result: ApiResult<TokenResponse> = gson.fromJson(bodyStr, type)
                val newTokens = result.data ?: run {
                    authToken = null
                    refreshToken = null
                    broadcastSessionExpired()
                    return@synchronized null
                }

                authToken = newTokens.token
                refreshToken = newTokens.refreshToken

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.token}")
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed", e)
                broadcastSessionExpired()
                null
            }
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                // Only pin certificates in release builds; debug needs flexibility
                if (!BuildConfig.DEBUG) {
                    certificatePinner(certificatePinner)
                }
            }
            .authenticator(tokenAuthenticator)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.LOG_HTTP_REQUESTS) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()
    }

    private var authToken: String? = null
    @Volatile private var refreshToken: String? = null
    private val refreshLock = Any()

    var sessionExpiredBroadcastAction: String? = null

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun setAuthToken(token: String?) {
        authToken = token
    }

    fun setRefreshToken(token: String?) {
        refreshToken = token
    }

    fun getAuthToken(): String? = authToken

    fun buildWsUrl(path: String): String {
        val wsBase = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val token = authToken
        return if (token != null) "$wsBase$path?token=$token" else "$wsBase$path"
    }

    // ── Session helpers ─────────────────────────────────────────────────

    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private fun broadcastSessionExpired() {
        val action = sessionExpiredBroadcastAction ?: return
        val ctx = appContext ?: return
        ctx.sendBroadcast(Intent(action))
    }

    // ── Auth Endpoints ──────────────────────────────────────────────────

    suspend fun login(phoneNumber: String, password: String): ApiResult<TokenResponse> {
        val result: ApiResult<TokenResponse> = post("/api/auth/login", mapOf("phoneNumber" to phoneNumber, "password" to password))
        result.data?.let { setRefreshToken(it.refreshToken) }
        return result
    }

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

    suspend fun deleteAccount(): ApiResult<Unit> =
        delete("/api/auth/delete-account")

    suspend fun updateProfile(fullName: String): ApiResult<Unit> =
        put("/api/auth/profile", mapOf("fullName" to fullName))

    suspend fun logout(refreshToken: String? = null): ApiResult<Unit> =
        post("/api/auth/logout", if (refreshToken != null) mapOf("refreshToken" to refreshToken) else emptyMap<String, Any>())

    suspend fun revokeAllSessions(): ApiResult<Unit> =
        delete("/api/auth/sessions")

    // ── Device Endpoints ────────────────────────────────────────────────

    suspend fun registerDevice(deviceData: Map<String, Any>): ApiResult<Map<String, Any>> =
        post("/api/devices/register", deviceData)

    suspend fun getDevices(userId: String): ApiResult<List<Map<String, Any>>> =
        get("/api/devices/$userId")

    suspend fun getDeviceIpLogs(deviceId: String): ApiResult<List<Map<String, Any>>> =
        get("/api/devices/$deviceId/ip-logs")

    suspend fun updateFcmToken(deviceId: String, fcmToken: String): ApiResult<Unit> =
        put("/api/devices/fcm-token", mapOf("deviceId" to deviceId, "fcmToken" to fcmToken))

    suspend fun getDeviceForensics(deviceId: String): ApiResult<Map<String, Any>> =
        get("/api/devices/$deviceId/forensics")

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

    suspend fun getLatestSpamSignatures(): ApiResult<List<Map<String, Any>>> =
        get("/api/caller-id/spam-sync")

    suspend fun reportSpamCall(phoneNumber: String, reason: String? = null): ApiResult<Unit> =
        post("/api/caller-id/report", mapOf("phoneNumber" to phoneNumber, "reason" to (reason ?: "")))

    suspend fun verifySpam(phoneNumber: String, vote: String): ApiResult<Map<String, Any>> =
        post("/api/caller-id/verify", mapOf("phoneNumber" to phoneNumber, "vote" to vote))

    suspend fun getSpamVerification(phoneNumber: String): ApiResult<Map<String, Any>> =
        get("/api/caller-id/verify/$phoneNumber")

    // ── Trust Endpoints ──────────────────────────────────────────────────

    suspend fun getUserTrust(userId: String): ApiResult<Map<String, Any>> =
        get("/api/admin/users/$userId/trust")

    suspend fun updateUserTrust(userId: String, trustScore: Int? = null, trustLevel: String? = null): ApiResult<Unit> {
        val body = mutableMapOf<String, Any>()
        trustScore?.let { body["trustScore"] = it }
        trustLevel?.let { body["trustLevel"] = it }
        return put("/api/admin/users/$userId/trust", body)
    }

    // ── Stolen Reports ──────────────────────────────────────────────────

    suspend fun reportStolen(reportData: Map<String, Any>): ApiResult<Unit> =
        post("/api/stolen/report", reportData)

    suspend fun getStolenReports(): ApiResult<List<Map<String, Any>>> =
        get("/api/stolen/reports")

    // ── Alarms ──────────────────────────────────────────────────────────

    suspend fun triggerAlarm(
        deviceId: String,
        type: String,
        notes: String? = null
    ): ApiResult<Map<String, Any>> =
        post("/api/alarms/trigger", buildMap {
            put("deviceId", deviceId)
            put("type", type)
            if (notes != null) put("notes", notes)
        })

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

    // ── User Analytics ─────────────────────────────────────────────────

    suspend fun getUserAnalytics(): ApiResult<Map<String, Any>> =
        get("/api/analytics/me")

    suspend fun getUserAnalyticsHistory(): ApiResult<List<Map<String, Any>>> =
        get("/api/analytics/me/history")

    // ── Admin Endpoints ──────────────────────────────────────────────────

    suspend fun adminLogin(phoneNumber: String, password: String): ApiResult<TokenResponse> =
        post("/api/admin/login", mapOf("phoneNumber" to phoneNumber, "password" to password))

    suspend fun getDashboardStats(): ApiResult<Map<String, Any>> =
        get("/api/admin/dashboard")

    suspend fun getAdminUsers(skip: Int = 0, limit: Int = 20): ApiResult<List<Map<String, Any>>> =
        get("/api/admin/users?skip=$skip&limit=$limit")

    suspend fun getAdminDevices(skip: Int = 0, limit: Int = 20): ApiResult<List<Map<String, Any>>> =
        get("/api/admin/devices?skip=$skip&limit=$limit")

    suspend fun getAdminCallerIds(skip: Int = 0, limit: Int = 20): ApiResult<List<Map<String, Any>>> =
        get("/api/admin/caller-ids?skip=$skip&limit=$limit")

    suspend fun getAdminStolenReports(): ApiResult<List<Map<String, Any>>> =
        get("/api/admin/stolen-reports")

    suspend fun getAdminAlarmLogs(): ApiResult<List<Map<String, Any>>> =
        get("/api/admin/alarm-logs")

    suspend fun getAdminSmsSpamReports(skip: Int = 0, limit: Int = 20): ApiResult<List<Map<String, Any>>> =
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

    // ── Family Group Endpoints ──────────────────────────────────────────

    suspend fun createFamilyGroup(name: String, createdBy: String = ""): ApiResult<Map<String, Any>> =
        post("/api/family/create", mapOf("name" to name))

    suspend fun getMyFamilyGroups(): ApiResult<List<Map<String, Any>>> =
        get("/api/family/my-groups")

    /** @deprecated Use [getMyFamilyGroups] instead (JWT determines the user). */
    suspend fun getFamilyGroups(userId: String): ApiResult<List<Map<String, Any>>> =
        get("/api/family/my-groups")

    suspend fun getFamilyGroupDetail(groupId: String): ApiResult<Map<String, Any>> =
        get("/api/family/$groupId")

    /** @deprecated Use [getFamilyGroupDetail] instead. */
    suspend fun getFamilyGroup(groupId: String): ApiResult<Map<String, Any>> =
        get("/api/family/$groupId")

    suspend fun inviteFamilyMember(groupId: String, phoneNumber: String, role: String = "MEMBER"): ApiResult<Map<String, Any>> =
        post("/api/family/$groupId/invite", mapOf("phoneNumber" to phoneNumber, "role" to role))

    suspend fun joinFamilyGroup(inviteCode: String, userId: String = ""): ApiResult<Map<String, Any>> =
        post("/api/family/join", mapOf("inviteCode" to inviteCode))

    suspend fun updateFamilyMemberRole(groupId: String, userId: String, role: String): ApiResult<Unit> =
        put("/api/family/$groupId/members/$userId/role", mapOf("role" to role))

    suspend fun removeFamilyMember(groupId: String, userId: String): ApiResult<Unit> =
        delete("/api/family/$groupId/members/$userId")

    suspend fun getFamilyGroupDevices(groupId: String): ApiResult<List<Map<String, Any>>> =
        get("/api/family/$groupId/devices")

    suspend fun sendFamilyAlert(
        groupId: String,
        type: String,
        message: String? = null,
        targetUserId: String? = null
    ): ApiResult<Unit> {
        val body = mutableMapOf<String, Any>("type" to type)
        message?.let { body["message"] = it }
        targetUserId?.let { body["targetUserId"] = it }
        return post("/api/family/$groupId/alert", body)
    }

    suspend fun deleteFamilyGroup(groupId: String): ApiResult<Unit> =
        delete("/api/family/$groupId")

    /** @deprecated Use [removeFamilyMember] for self-removal (leave). */
    suspend fun leaveFamilyGroup(groupId: String, userId: String): ApiResult<Unit> =
        delete("/api/family/$groupId/members/$userId")

    /** @deprecated Use [removeFamilyMember] instead. */
    suspend fun removeFamilyGroupMember(groupId: String, memberId: String): ApiResult<Unit> =
        delete("/api/family/$groupId/members/$memberId")

    /** @deprecated Use [updateFamilyMemberRole] with role="ADMIN" instead. */
    suspend fun promoteFamilyGroupMember(groupId: String, memberId: String): ApiResult<Unit> =
        put("/api/family/$groupId/members/$memberId/role", mapOf("role" to "ADMIN"))

    /** @deprecated No longer supported in the new API. */
    suspend fun updateFamilyGroup(groupId: String, name: String): ApiResult<Unit> =
        put("/api/family/$groupId", mapOf("name" to name))

    /** @deprecated No longer supported in the new API. */
    suspend fun regenerateInviteCode(groupId: String): ApiResult<Map<String, Any>> =
        post("/api/family/$groupId/regenerate-invite", emptyMap<String, Any>())

    // ── Internal HTTP methods ───────────────────────────────────────────

    private suspend inline fun <reified T> get(path: String): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .apply { authToken?.let { addHeader("Authorization", "Bearer $it") } }
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.use { parseResponse(it) }
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
            response.use { parseResponse(it) }
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
            response.use { parseResponse(it) }
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
            response.use { parseResponse(it) }
        } catch (e: Exception) {
            Log.e(TAG, "POST $path failed", e)
            ApiResult(success = false, error = e.message ?: "Network error")
        }
    }

    /**
     * Check if the backend is reachable. Returns true if /api/health returns 200.
     */
    suspend fun isBackendReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/api/health").get().build()
            val response = client.newCall(request).execute()
            response.use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private inline fun <reified T> parseResponse(response: okhttp3.Response): ApiResult<T> {
        val bodyString = response.body?.string() ?: ""

        // Log 404s clearly — most likely means backend is not deployed or route is wrong
        if (response.code == 404) {
            Log.w(TAG, "404 Not Found: ${response.request.url} — backend may not be deployed or route does not exist")
            return ApiResult(success = false, error = "Server endpoint not found (404). Backend may need redeployment.")
        }

        return try {
            val type = object : TypeToken<ApiResult<T>>() {}.type
            val parsed: ApiResult<T> = gson.fromJson(bodyString, type)
            parsed
        } catch (e: Exception) {
            if (response.isSuccessful) {
                try {
                    val dataType = object : TypeToken<T>() {}.type
                    val rawData: T = gson.fromJson(bodyString, dataType)
                    ApiResult(success = true, data = rawData)
                } catch (_: Exception) {
                    ApiResult(success = true, message = "OK")
                }
            } else {
                Log.w(TAG, "HTTP ${response.code}: ${response.request.url}")
                ApiResult(success = false, error = "HTTP ${response.code}: ${bodyString.take(200)}")
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
    val userId: String = "",
    val fullName: String = "",
    val role: String = ""
)
