package com.byron.trucaller.service

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ApiClient] using [MockWebServer] to intercept and validate
 * HTTP requests and responses without hitting the real backend.
 *
 * Tests cover:
 * - Correct HTTP methods (GET, POST, PUT, DELETE) for each endpoint
 * - Request URL path construction
 * - Authorization header presence when auth token is set
 * - JSON request body serialization
 * - Successful response parsing into [ApiResult]
 * - Error response handling
 */
class ApiClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        ApiClient.setBaseUrl(server.url("/").toString())
        ApiClient.setAuthToken(null)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private fun enqueueSuccess(data: String = "null", message: String = "OK") {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"data":$data,"message":"$message"}""")
                .addHeader("Content-Type", "application/json")
        )
    }

    private fun enqueueError(code: Int = 400, error: String = "Bad Request") {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setBody("""{"success":false,"error":"$error"}""")
                .addHeader("Content-Type", "application/json")
        )
    }

    // ── Auth Endpoints ──────────────────────────────────────────────────

    @Test
    fun `login sends POST to correct path with credentials`() = runTest {
        enqueueSuccess("""{"token":"jwt-tok","expiresIn":86400,"userId":"u-1"}""")

        val result = ApiClient.login("+256700123456", "password123")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/auth/login")
        val body = request.body.readUtf8()
        assertThat(body).contains("phoneNumber")
        assertThat(body).contains("password")
    }

    @Test
    fun `register sends POST to correct path`() = runTest {
        enqueueSuccess("""{"token":"jwt-tok","expiresIn":86400,"userId":"u-1"}""")

        val result = ApiClient.register("John Doe", "+256700123456", "password123")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/auth/register")
    }

    @Test
    fun `sendOtp sends POST with phone and purpose`() = runTest {
        enqueueSuccess("""{}""")

        ApiClient.sendOtp("+256700123456", "registration")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/auth/send-otp")
        assertThat(request.body.readUtf8()).contains("registration")
    }

    @Test
    fun `verifyOtp sends POST with phone and code`() = runTest {
        enqueueSuccess()

        ApiClient.verifyOtp("+256700123456", "123456")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/auth/verify-otp")
    }

    @Test
    fun `resetPassword sends POST to correct path`() = runTest {
        enqueueSuccess()

        ApiClient.resetPassword("+256700123456", "654321", "newPass123")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/auth/reset-password")
    }

    // ── Authorization Header ──────────────────────────────────────────────

    @Test
    fun `requests include Bearer token when auth token is set`() = runTest {
        ApiClient.setAuthToken("my-jwt-token")
        enqueueSuccess("""[]""")

        ApiClient.getBlockedNumbers()

        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer my-jwt-token")
    }

    @Test
    fun `requests omit Authorization header when no token is set`() = runTest {
        ApiClient.setAuthToken(null)
        enqueueSuccess("""[]""")

        ApiClient.getBlockedNumbers()

        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isNull()
    }

    // ── Device Endpoints ──────────────────────────────────────────────────

    @Test
    fun `registerDevice sends POST to devices register`() = runTest {
        enqueueSuccess("""{}""")

        ApiClient.registerDevice(mapOf("model" to "Pixel 7", "deviceId" to "dev-1"))

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/devices/register")
    }

    @Test
    fun `getDevices sends GET with userId in path`() = runTest {
        enqueueSuccess("""[]""")

        ApiClient.getDevices("user-123")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/devices/user-123")
    }

    @Test
    fun `getDeviceIpLogs sends GET with deviceId in path`() = runTest {
        enqueueSuccess("""[]""")

        ApiClient.getDeviceIpLogs("dev-001")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/devices/dev-001/ip-logs")
    }

    @Test
    fun `updateFcmToken sends PUT to fcm-token endpoint`() = runTest {
        enqueueSuccess()

        ApiClient.updateFcmToken("dev-001", "new-fcm-token")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/devices/fcm-token")
    }

    @Test
    fun `getDeviceForensics sends GET with deviceId`() = runTest {
        enqueueSuccess("""[]""")

        ApiClient.getDeviceForensics("dev-001")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/devices/dev-001/forensics")
    }

    @Test
    fun `recoverDevice sends PUT with deviceId`() = runTest {
        enqueueSuccess()

        ApiClient.recoverDevice("dev-001")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/devices/dev-001/recover")
    }

    // ── Caller ID Endpoints ──────────────────────────────────────────────

    @Test
    fun `lookupCallerId sends GET with phone number`() = runTest {
        enqueueSuccess("""{}""")

        ApiClient.lookupCallerId("+256700123456")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/caller-id/lookup/+256700123456")
    }

    @Test
    fun `reportSpamCall sends POST to report endpoint`() = runTest {
        enqueueSuccess()

        ApiClient.reportSpamCall("+256700111222", "Scam caller")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/caller-id/report")
    }

    // ── Blocked Numbers ──────────────────────────────────────────────────

    @Test
    fun `blockNumber sends POST to blocked add`() = runTest {
        enqueueSuccess()

        ApiClient.blockNumber(mapOf("phoneNumber" to "+256700123456"))

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/blocked/add")
    }

    @Test
    fun `getBlockedNumbers sends GET to blocked`() = runTest {
        enqueueSuccess("""[]""")

        ApiClient.getBlockedNumbers()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/blocked")
    }

    @Test
    fun `unblockNumber sends DELETE with id`() = runTest {
        enqueueSuccess()

        ApiClient.unblockNumber("blocked-123")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/blocked/blocked-123")
    }

    @Test
    fun `isNumberBlocked sends GET to check endpoint`() = runTest {
        enqueueSuccess("""true""")

        ApiClient.isNumberBlocked("+256700123456")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/blocked/check/+256700123456")
    }

    // ── Stolen Reports ───────────────────────────────────────────────────

    @Test
    fun `reportStolen sends POST to stolen report`() = runTest {
        enqueueSuccess()

        ApiClient.reportStolen(mapOf("deviceId" to "dev-1", "description" to "Stolen"))

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/stolen/report")
    }

    @Test
    fun `getStolenReports sends GET to stolen reports`() = runTest {
        enqueueSuccess("""[]""")

        ApiClient.getStolenReports()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/stolen/reports")
    }

    @Test
    fun `updateStolenReportStatus sends PUT with id and status`() = runTest {
        enqueueSuccess()

        ApiClient.updateStolenReportStatus("report-1", "VERIFIED")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/stolen/reports/report-1/status")
    }

    // ── Admin Endpoints ──────────────────────────────────────────────────

    @Test
    fun `adminLogin sends POST to admin login`() = runTest {
        enqueueSuccess("""{"token":"jwt","expiresIn":86400,"userId":"admin-1"}""")

        ApiClient.adminLogin("admin@trucaller.ug", "adminPass")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/admin/login")
    }

    @Test
    fun `getDashboardStats sends GET to admin dashboard`() = runTest {
        enqueueSuccess("""{}""")

        ApiClient.getDashboardStats()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/admin/dashboard")
    }

    @Test
    fun `getAdminUsers sends GET with pagination params`() = runTest {
        enqueueSuccess("""[]""")

        ApiClient.getAdminUsers(skip = 10, limit = 5)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/admin/users?skip=10&limit=5")
    }

    @Test
    fun `getAdminUserDetail sends GET with userId`() = runTest {
        enqueueSuccess("""{}""")

        ApiClient.getAdminUserDetail("user-123")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/admin/users/user-123")
    }

    @Test
    fun `createAdminCallerId sends POST`() = runTest {
        enqueueSuccess()

        ApiClient.createAdminCallerId(
            id = "cid-1",
            phoneNumber = "+256700123456",
            name = "Test",
            spamScore = 50,
            reportCount = 5,
            category = "SUSPECTED_SPAM",
            lastUpdated = "2026-03-20T00:00:00Z"
        )

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/admin/caller-ids")
    }

    @Test
    fun `updateAdminCallerId sends PUT with id`() = runTest {
        enqueueSuccess()

        ApiClient.updateAdminCallerId("cid-1", spamScore = 90, category = "FRAUD")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/admin/caller-ids/cid-1")
    }

    @Test
    fun `deleteAdminCallerId sends DELETE with id`() = runTest {
        enqueueSuccess()

        ApiClient.deleteAdminCallerId("cid-1")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/admin/caller-ids/cid-1")
    }

    // ── Error Handling ───────────────────────────────────────────────────

    @Test
    fun `server error returns ApiResult with success false`() = runTest {
        enqueueError(500, "Internal Server Error")

        val result = ApiClient.getBlockedNumbers()

        assertThat(result.success).isFalse()
    }

    @Test
    fun `401 unauthorized returns error result`() = runTest {
        enqueueError(401, "Authentication required")

        val result = ApiClient.getDashboardStats()

        assertThat(result.success).isFalse()
    }

    @Test
    fun `malformed JSON response returns error result`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("not-json")
                .addHeader("Content-Type", "application/json")
        )

        val result = ApiClient.getBlockedNumbers()

        // Should handle gracefully without throwing
        assertThat(result).isNotNull()
    }

    // ── Family Group Endpoints ───────────────────────────────────────────

    @Test
    fun `createFamilyGroup sends POST to family create`() = runTest {
        enqueueSuccess("""{}""")

        ApiClient.createFamilyGroup("My Family", "user-1")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/family/create")
    }

    @Test
    fun `getMyFamilyGroups sends GET to family my-groups`() = runTest {
        enqueueSuccess("""[]""")

        ApiClient.getMyFamilyGroups()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/family/my-groups")
    }

    // ── Geofence Endpoints ───────────────────────────────────────────────

    @Test
    fun `createGeofence sends POST to geofences`() = runTest {
        enqueueSuccess("""{}""")

        ApiClient.createGeofence("dev-1", "Home", 0.3476, 32.5825, 200)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/geofences")
    }

    @Test
    fun `deleteGeofence sends DELETE with geofenceId`() = runTest {
        enqueueSuccess()

        ApiClient.deleteGeofence("geo-1")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/geofences/geo-1")
    }
}
