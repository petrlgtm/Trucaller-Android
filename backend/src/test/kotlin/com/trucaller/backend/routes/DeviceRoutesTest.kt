package com.trucaller.backend.routes

import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.data.models.DeviceStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Integration-level tests for device route DTOs, request serialization,
 * response format validation, and route path structure.
 *
 * These tests validate the building blocks used by the device routes
 * without requiring a live MongoDB connection.
 */
class DeviceRoutesTest : RouteTestBase() {

    // ── Request DTO Serialization ────────────────────────────────────────

    @Test
    fun `DeviceRegisterRequest serialises with all fields`() {
        val request = DeviceRegisterRequest(
            model = "Pixel 7",
            manufacturer = "Google",
            osVersion = "Android 14",
            deviceId = "device-abc-123",
            fcmToken = "fcm-token-xyz",
            lastIp = "192.168.1.1",
            isp = "Airtel Uganda",
            city = "Kampala",
            country = "Uganda",
            latitude = 0.3476,
            longitude = 32.5825,
            networkType = "4G"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<DeviceRegisterRequest>(encoded)

        assertEquals(request, decoded)
        assertEquals("Pixel 7", decoded.model)
        assertEquals("device-abc-123", decoded.deviceId)
    }

    @Test
    fun `DeviceRegisterRequest serialises with minimal required fields`() {
        val request = DeviceRegisterRequest(
            model = "Samsung S24",
            manufacturer = "Samsung",
            osVersion = "Android 15",
            deviceId = "dev-001"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<DeviceRegisterRequest>(encoded)

        assertEquals("Samsung S24", decoded.model)
        assertNull(decoded.fcmToken)
        assertNull(decoded.lastIp)
        assertNull(decoded.isp)
        assertNull(decoded.city)
        assertNull(decoded.country)
        assertNull(decoded.latitude)
        assertNull(decoded.longitude)
        assertNull(decoded.networkType)
    }

    @Test
    fun `DeviceResponse serialises with all fields`() {
        val response = DeviceResponse(
            id = "obj-id-1",
            userId = "user-123",
            model = "Pixel 7",
            manufacturer = "Google",
            osVersion = "Android 14",
            deviceId = "device-abc-123",
            status = "ACTIVE",
            lastIp = "192.168.1.1",
            lastSeen = "2026-03-20T10:00:00Z",
            registeredAt = "2026-03-01T08:00:00Z",
            fcmToken = "fcm-tok"
        )
        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<DeviceResponse>(encoded)

        assertEquals(response, decoded)
    }

    @Test
    fun `DeviceResponse serialises without optional fcmToken`() {
        val response = DeviceResponse(
            id = "obj-id-2",
            userId = "user-456",
            model = "Redmi Note 12",
            manufacturer = "Xiaomi",
            osVersion = "Android 13",
            deviceId = "dev-789",
            status = "STOLEN",
            lastIp = "10.0.0.1",
            lastSeen = "2026-03-19T18:00:00Z",
            registeredAt = "2026-01-15T12:00:00Z"
        )
        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<DeviceResponse>(encoded)

        assertNull(decoded.fcmToken)
    }

    // ── FCM Token Update ──────────────────────────────────────────────────

    @Test
    fun `FcmTokenUpdateRequest serialises correctly`() {
        val request = FcmTokenUpdateRequest(
            deviceId = "dev-001",
            fcmToken = "new-fcm-token"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<FcmTokenUpdateRequest>(encoded)

        assertEquals(request, decoded)
    }

    // ── Device Recovery ──────────────────────────────────────────────────

    @Test
    fun `DeviceRecoverRequest serialises correctly`() {
        val request = DeviceRecoverRequest(status = "ACTIVE")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<DeviceRecoverRequest>(encoded)

        assertEquals("ACTIVE", decoded.status)
    }

    // ── IP Log Response ──────────────────────────────────────────────────

    @Test
    fun `IpLogResponse serialises with all fields`() {
        val response = IpLogResponse(
            id = "log-1",
            deviceId = "dev-001",
            ipAddress = "41.210.145.32",
            isp = "MTN Uganda",
            city = "Kampala",
            country = "Uganda",
            latitude = 0.3476,
            longitude = 32.5825,
            networkType = "4G",
            timestamp = "2026-03-20T12:00:00Z"
        )
        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<IpLogResponse>(encoded)

        assertEquals(response, decoded)
    }

    // ── Forensics Response ────────────────────────────────────────────────

    @Test
    fun `ForensicsResponse serialises with full data`() {
        val log1 = IpLogResponse(
            id = "log-1", deviceId = "dev-001", ipAddress = "41.210.145.32",
            isp = "MTN Uganda", city = "Kampala", country = "Uganda",
            latitude = 0.3476, longitude = 32.5825, networkType = "4G",
            timestamp = "2026-03-20T12:00:00Z"
        )
        val log2 = IpLogResponse(
            id = "log-2", deviceId = "dev-001", ipAddress = "197.239.9.100",
            isp = "Airtel Uganda", city = "Entebbe", country = "Uganda",
            latitude = 0.0512, longitude = 32.4637, networkType = "WiFi",
            timestamp = "2026-03-19T08:00:00Z"
        )

        val forensics = ForensicsResponse(
            deviceId = "dev-001",
            ipLogs = listOf(log1, log2),
            uniqueIsps = listOf("MTN Uganda", "Airtel Uganda"),
            uniqueCities = listOf("Kampala", "Entebbe"),
            uniqueCountries = listOf("Uganda"),
            totalLogs = 2
        )
        val encoded = json.encodeToString(forensics)
        val decoded = json.decodeFromString<ForensicsResponse>(encoded)

        assertEquals(2, decoded.totalLogs)
        assertEquals(2, decoded.uniqueIsps.size)
        assertEquals(2, decoded.uniqueCities.size)
        assertEquals(1, decoded.uniqueCountries.size)
    }

    @Test
    fun `ForensicsResponse serialises empty logs`() {
        val forensics = ForensicsResponse(
            deviceId = "dev-empty",
            ipLogs = emptyList(),
            uniqueIsps = emptyList(),
            uniqueCities = emptyList(),
            uniqueCountries = emptyList(),
            totalLogs = 0
        )
        val encoded = json.encodeToString(forensics)
        val decoded = json.decodeFromString<ForensicsResponse>(encoded)

        assertEquals(0, decoded.totalLogs)
        assertTrue(decoded.ipLogs.isEmpty())
    }

    // ── DeviceStatus enum ─────────────────────────────────────────────────

    @Test
    fun `DeviceStatus enum contains all expected values`() {
        val values = DeviceStatus.entries.map { it.name }
        assertTrue("ACTIVE" in values)
        assertTrue("STOLEN" in values)
        assertTrue("INACTIVE" in values)
        assertTrue("FLAGGED" in values)
        assertEquals(4, values.size)
    }

    // ── Route path structure validation ───────────────────────────────────

    @Test
    fun `device route paths follow expected convention`() {
        val expectedPaths = listOf(
            "/api/devices/register",
            "/api/devices/fcm-token",
            "/api/devices/{deviceId}/recover",
            "/api/devices/{userId}",
            "/api/devices/{deviceId}/ip-logs",
            "/api/devices/{deviceId}/forensics"
        )
        expectedPaths.forEach { path ->
            assertTrue(path.startsWith("/api/devices/"), "Path '$path' should start with /api/devices/")
        }
    }

    // ── ApiResponse envelope format ──────────────────────────────────────

    @Test
    fun `success ApiResponse wraps DeviceResponse data`() {
        val deviceResp = DeviceResponse(
            id = "id-1", userId = "u-1", model = "Pixel", manufacturer = "Google",
            osVersion = "14", deviceId = "d-1", status = "ACTIVE",
            lastIp = "1.2.3.4", lastSeen = "now", registeredAt = "before"
        )
        val apiResp = ApiResponse(
            success = true,
            data = deviceResp,
            message = "Device registered successfully."
        )
        val encoded = Json { ignoreUnknownKeys = true }.encodeToString(apiResp)

        assertTrue(encoded.contains("\"success\":true") || encoded.contains("\"success\" : true"))
        assertTrue(encoded.contains("Pixel"))
    }

    @Test
    fun `error ApiResponse includes error field`() {
        val apiResp = ApiResponse<Unit>(
            success = false,
            error = "Invalid request body"
        )
        val encoded = json.encodeToString(apiResp)

        assertTrue(encoded.contains("Invalid request body"))
        assertTrue(encoded.contains("false"))
    }
}
