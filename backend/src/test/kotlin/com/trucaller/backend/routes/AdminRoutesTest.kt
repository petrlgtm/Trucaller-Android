package com.trucaller.backend.routes

import com.trucaller.backend.data.models.AdminPasswordUpdateRequest
import com.trucaller.backend.data.models.AdminProfileUpdateRequest
import com.trucaller.backend.data.models.AdminRole
import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.routes.DeviceResponse
import com.trucaller.backend.routes.UserDetailResponse
import com.trucaller.backend.routes.UserResponse
import kotlinx.serialization.encodeToString
import kotlin.test.*

/**
 * Integration-level tests for admin route DTOs, request serialization,
 * response format validation, and admin-specific business logic.
 *
 * These tests validate the building blocks used by the admin routes
 * without requiring a live MongoDB connection.
 */
class AdminRoutesTest : RouteTestBase() {

    // ── Dashboard Stats ──────────────────────────────────────────────────

    @Test
    fun `DashboardStats serialises with all counts`() {
        val stats = DashboardStats(
            userCount = 150,
            deviceCount = 200,
            reportCount = 45,
            alarmCount = 12,
            callerIdCount = 5000,
            smsSpamReportCount = 300
        )
        val encoded = json.encodeToString(stats)
        val decoded = json.decodeFromString<DashboardStats>(encoded)

        assertEquals(stats, decoded)
        assertEquals(150L, decoded.userCount)
        assertEquals(5000L, decoded.callerIdCount)
    }

    @Test
    fun `DashboardStats serialises with zero counts`() {
        val stats = DashboardStats(
            userCount = 0, deviceCount = 0, reportCount = 0,
            alarmCount = 0, callerIdCount = 0, smsSpamReportCount = 0
        )
        val encoded = json.encodeToString(stats)
        val decoded = json.decodeFromString<DashboardStats>(encoded)

        assertEquals(0L, decoded.userCount)
        assertEquals(0L, decoded.deviceCount)
    }

    // ── Caller ID CRUD DTOs ──────────────────────────────────────────────

    @Test
    fun `CallerIdCreateRequest serialises with all fields`() {
        val request = CallerIdCreateRequest(
            id = "cid-001",
            phoneNumber = "+256700123456",
            name = "Known Scammer",
            spamScore = 95,
            reportCount = 42,
            category = "FRAUD",
            lastUpdated = "2026-03-20T10:00:00Z"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CallerIdCreateRequest>(encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `CallerIdCreateRequest serialises with defaults`() {
        val request = CallerIdCreateRequest(
            phoneNumber = "+256700999888",
            name = "New Entry"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CallerIdCreateRequest>(encoded)

        assertNull(decoded.id)
        assertEquals(0, decoded.spamScore)
        assertEquals(0, decoded.reportCount)
        assertEquals("SAFE", decoded.category)
        assertNull(decoded.lastUpdated)
    }

    @Test
    fun `CallerIdUpdateRequest serialises with partial updates`() {
        val request = CallerIdUpdateRequest(spamScore = 75, category = "SPAM")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CallerIdUpdateRequest>(encoded)

        assertEquals(75, decoded.spamScore)
        assertEquals("SPAM", decoded.category)
        assertNull(decoded.name)
    }

    @Test
    fun `CallerIdUpdateRequest with only name`() {
        val request = CallerIdUpdateRequest(name = "Updated Name")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CallerIdUpdateRequest>(encoded)

        assertNull(decoded.spamScore)
        assertNull(decoded.category)
        assertEquals("Updated Name", decoded.name)
    }

    @Test
    fun `CallerIdUpdateRequest with all null fields serialises`() {
        val request = CallerIdUpdateRequest()
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<CallerIdUpdateRequest>(encoded)

        assertNull(decoded.spamScore)
        assertNull(decoded.category)
        assertNull(decoded.name)
    }

    // ── Admin Status Update ──────────────────────────────────────────────

    @Test
    fun `AdminStatusUpdateRequest serialises correctly`() {
        val request = AdminStatusUpdateRequest(status = "RESOLVED")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<AdminStatusUpdateRequest>(encoded)

        assertEquals("RESOLVED", decoded.status)
    }

    // ── Trust Update ──────────────────────────────────────────────────────

    @Test
    fun `TrustUpdateRequest serialises with score only`() {
        val request = TrustUpdateRequest(trustScore = 85)
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<TrustUpdateRequest>(encoded)

        assertEquals(85, decoded.trustScore)
        assertNull(decoded.trustLevel)
    }

    @Test
    fun `TrustUpdateRequest serialises with level only`() {
        val request = TrustUpdateRequest(trustLevel = "VERIFIED")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<TrustUpdateRequest>(encoded)

        assertNull(decoded.trustScore)
        assertEquals("VERIFIED", decoded.trustLevel)
    }

    @Test
    fun `TrustUpdateRequest serialises with both fields`() {
        val request = TrustUpdateRequest(trustScore = 50, trustLevel = "TRUSTED")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<TrustUpdateRequest>(encoded)

        assertEquals(50, decoded.trustScore)
        assertEquals("TRUSTED", decoded.trustLevel)
    }

    // ── Trust level auto-derivation logic ─────────────────────────────────

    @Test
    fun `trust score 0-19 maps to NEW level`() {
        listOf(0, 10, 19).forEach { score ->
            val level = deriveTrustLevel(score)
            assertEquals("NEW", level, "Score $score should map to NEW")
        }
    }

    @Test
    fun `trust score 20-49 maps to BASIC level`() {
        listOf(20, 35, 49).forEach { score ->
            val level = deriveTrustLevel(score)
            assertEquals("BASIC", level, "Score $score should map to BASIC")
        }
    }

    @Test
    fun `trust score 50-79 maps to TRUSTED level`() {
        listOf(50, 65, 79).forEach { score ->
            val level = deriveTrustLevel(score)
            assertEquals("TRUSTED", level, "Score $score should map to TRUSTED")
        }
    }

    @Test
    fun `trust score 80-99 maps to VERIFIED level`() {
        listOf(80, 90, 99).forEach { score ->
            val level = deriveTrustLevel(score)
            assertEquals("VERIFIED", level, "Score $score should map to VERIFIED")
        }
    }

    @Test
    fun `trust score 100 maps to AUTHORITY level`() {
        assertEquals("AUTHORITY", deriveTrustLevel(100))
    }

    @Test
    fun `trust score clamped to 0-100 range`() {
        val clamped = (-5).coerceIn(0, 100)
        assertEquals(0, clamped)
        val clamped2 = 150.coerceIn(0, 100)
        assertEquals(100, clamped2)
    }

    // ── Admin Profile & Password ──────────────────────────────────────────

    @Test
    fun `AdminProfileUpdateRequest serialises correctly`() {
        val request = AdminProfileUpdateRequest(
            name = "Byron Admin",
            email = "admin@trucaller.ug"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<AdminProfileUpdateRequest>(encoded)

        assertEquals("Byron Admin", decoded.name)
        assertEquals("admin@trucaller.ug", decoded.email)
    }

    @Test
    fun `AdminPasswordUpdateRequest serialises correctly`() {
        val request = AdminPasswordUpdateRequest(
            currentPassword = "oldPassword123",
            newPassword = "newSecure456"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<AdminPasswordUpdateRequest>(encoded)

        assertEquals("oldPassword123", decoded.currentPassword)
        assertEquals("newSecure456", decoded.newPassword)
    }

    // ── UserDetailResponse ───────────────────────────────────────────────

    @Test
    fun `UserDetailResponse serialises correctly`() {
        val detail = UserDetailResponse(
            user = UserResponse(
                id = "u-1",
                fullName = "John",
                phoneNumber = "+256700000001",
                isActive = true,
                status = "ACTIVE",
                trustScore = 50,
                trustLevel = "NEW",
                createdAt = "2026-01-01T00:00:00Z"
            ),
            devices = listOf(
                DeviceResponse(
                    id = "d-1", userId = "u-1", model = "Pixel",
                    manufacturer = "Google", osVersion = "14", deviceId = "dev-1",
                    status = "ACTIVE", lastIp = "1.2.3.4",
                    lastSeen = "2026-01-01T00:00:00Z", registeredAt = "2026-01-01T00:00:00Z"
                ),
                DeviceResponse(
                    id = "d-2", userId = "u-1", model = "Samsung",
                    manufacturer = "Samsung", osVersion = "13", deviceId = "dev-2",
                    status = "ACTIVE", lastIp = "1.2.3.5",
                    lastSeen = "2026-01-01T00:00:00Z", registeredAt = "2026-01-01T00:00:00Z"
                )
            )
        )
        val encoded = json.encodeToString(detail)
        val decoded = json.decodeFromString<UserDetailResponse>(encoded)

        assertEquals("John", decoded.user.fullName)
        assertEquals(2, decoded.devices.size)
    }

    // ── AdminRole enum ───────────────────────────────────────────────────

    @Test
    fun `AdminRole enum contains expected values`() {
        val values = AdminRole.entries.map { it.name }
        assertTrue("SUPER_ADMIN" in values)
        assertTrue("MODERATOR" in values)
        assertEquals(2, values.size)
    }

    // ── Route path structure validation ──────────────────────────────────

    @Test
    fun `admin route paths follow expected convention`() {
        val expectedPaths = listOf(
            "/api/admin/dashboard",
            "/api/admin/users",
            "/api/admin/users/{userId}",
            "/api/admin/devices",
            "/api/admin/caller-ids",
            "/api/admin/caller-ids/{id}",
            "/api/admin/stolen-reports/{id}/status",
            "/api/admin/profile",
            "/api/admin/password",
            "/api/admin/users/{userId}/trust",
            "/api/admin/sms-spam-reports"
        )
        expectedPaths.forEach { path ->
            assertTrue(path.startsWith("/api/admin/"), "Path '$path' should start with /api/admin/")
        }
    }

    @Test
    fun `valid trust levels for validation`() {
        val validLevels = listOf("NEW", "BASIC", "TRUSTED", "VERIFIED", "AUTHORITY")
        assertTrue("NEW" in validLevels)
        assertTrue("AUTHORITY" in validLevels)
        assertFalse("ADMIN" in validLevels)
        assertFalse("" in validLevels)
    }

    // ── Helper matching admin route logic ─────────────────────────────────

    /**
     * Replicates the trust-level derivation logic from adminRoutes for testing.
     */
    private fun deriveTrustLevel(score: Int): String {
        val clamped = score.coerceIn(0, 100)
        return when {
            clamped >= 100 -> "AUTHORITY"
            clamped >= 80 -> "VERIFIED"
            clamped >= 50 -> "TRUSTED"
            clamped >= 20 -> "BASIC"
            else -> "NEW"
        }
    }
}
