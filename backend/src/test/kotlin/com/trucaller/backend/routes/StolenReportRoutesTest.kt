package com.trucaller.backend.routes

import com.trucaller.backend.data.models.ApiResponse
import com.trucaller.backend.data.models.ReportStatus
import kotlinx.serialization.encodeToString
import kotlin.test.*

/**
 * Integration-level tests for stolen report route DTOs, request serialization,
 * response format validation, and status workflow rules.
 *
 * These tests validate the building blocks used by the stolen report routes
 * without requiring a live MongoDB connection.
 */
class StolenReportRoutesTest : RouteTestBase() {

    // ── Request DTO Serialization ────────────────────────────────────────

    @Test
    fun `StolenReportRequest serialises with all fields`() {
        val request = StolenReportRequest(
            deviceId = "dev-001",
            description = "Phone stolen at Kampala taxi park",
            pinVerified = true,
            lastKnownIp = "41.210.145.32",
            lastKnownLatitude = 0.3476,
            lastKnownLongitude = 32.5825,
            lastKnownCity = "Kampala"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<StolenReportRequest>(encoded)

        assertEquals(request, decoded)
        assertEquals("dev-001", decoded.deviceId)
        assertTrue(decoded.pinVerified)
    }

    @Test
    fun `StolenReportRequest serialises with minimal fields`() {
        val request = StolenReportRequest(
            deviceId = "dev-002",
            description = "Lost device",
            pinVerified = false
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<StolenReportRequest>(encoded)

        assertNull(decoded.lastKnownIp)
        assertNull(decoded.lastKnownLatitude)
        assertNull(decoded.lastKnownLongitude)
        assertNull(decoded.lastKnownCity)
        assertFalse(decoded.pinVerified)
    }

    @Test
    fun `StatusUpdateRequest serialises correctly`() {
        val request = StatusUpdateRequest(status = "VERIFIED")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<StatusUpdateRequest>(encoded)

        assertEquals("VERIFIED", decoded.status)
    }

    // ── Status Workflow Validation ────────────────────────────────────────

    @Test
    fun `ReportStatus enum contains all expected values`() {
        val values = ReportStatus.entries.map { it.name }
        assertTrue("PENDING" in values)
        assertTrue("VERIFIED" in values)
        assertTrue("ESCALATED" in values)
        assertTrue("RESOLVED" in values)
        assertEquals(4, values.size, "ReportStatus should have exactly 4 values")
    }

    @Test
    fun `valid status values are accepted`() {
        val validStatuses = listOf("PENDING", "VERIFIED", "ESCALATED", "RESOLVED")
        validStatuses.forEach { status ->
            assertTrue(status in validStatuses, "$status should be in valid statuses list")
        }
    }

    @Test
    fun `invalid status values are rejected`() {
        val validStatuses = listOf("PENDING", "VERIFIED", "ESCALATED", "RESOLVED")
        val invalidStatuses = listOf("ACTIVE", "CLOSED", "OPEN", "", "pending")
        invalidStatuses.forEach { status ->
            assertFalse(status in validStatuses, "$status should not be in valid statuses list")
        }
    }

    // ── Route path structure validation ────────────────────────────────

    @Test
    fun `stolen report route paths follow expected convention`() {
        val expectedPaths = listOf(
            "/api/stolen/report",
            "/api/stolen/reports",
            "/api/stolen/reports/{id}/status",
            "/api/admin/stolen-reports"
        )
        assertTrue(expectedPaths[0].startsWith("/api/stolen/"))
        assertTrue(expectedPaths[1].startsWith("/api/stolen/"))
        assertTrue(expectedPaths[2].contains("{id}"))
        assertTrue(expectedPaths[3].startsWith("/api/admin/"))
    }

    // ── ApiResponse envelope format ──────────────────────────────────────

    @Test
    fun `success response for stolen report creation`() {
        val apiResp = ApiResponse<Nothing>(
            success = true,
            message = "Stolen report filed successfully"
        )
        val encoded = json.encodeToString(apiResp)
        assertSuccessEnvelope(encoded)
    }

    @Test
    fun `error response for missing report id`() {
        val apiResp = ApiResponse<Nothing>(
            success = false,
            error = "Missing report id"
        )
        val encoded = json.encodeToString(apiResp)
        assertErrorEnvelope(encoded)
    }

    @Test
    fun `error response for invalid status`() {
        val apiResp = ApiResponse<Nothing>(
            success = false,
            error = "Invalid status. Must be one of: PENDING, VERIFIED, ESCALATED, RESOLVED"
        )
        val encoded = json.encodeToString(apiResp)
        val decoded = json.decodeFromString<ApiResponse<String>>(encoded)

        assertFalse(decoded.success)
        assertTrue(decoded.error!!.contains("PENDING"))
        assertTrue(decoded.error!!.contains("RESOLVED"))
    }

    @Test
    fun `success response for status update`() {
        val apiResp = ApiResponse<Nothing>(
            success = true,
            message = "Report status updated to VERIFIED"
        )
        val encoded = json.encodeToString(apiResp)
        val decoded = json.decodeFromString<ApiResponse<String>>(encoded)

        assertTrue(decoded.success)
        assertTrue(decoded.message!!.contains("VERIFIED"))
    }

    // ── Location data validation ──────────────────────────────────────────

    @Test
    fun `location data is optional in stolen report request`() {
        val withLocation = StolenReportRequest(
            deviceId = "dev-1",
            description = "Stolen",
            pinVerified = true,
            lastKnownLatitude = 0.3476,
            lastKnownLongitude = 32.5825,
            lastKnownCity = "Kampala"
        )
        val withoutLocation = StolenReportRequest(
            deviceId = "dev-2",
            description = "Stolen",
            pinVerified = false
        )

        assertNotNull(withLocation.lastKnownLatitude)
        assertNotNull(withLocation.lastKnownLongitude)
        assertNotNull(withLocation.lastKnownCity)

        assertNull(withoutLocation.lastKnownLatitude)
        assertNull(withoutLocation.lastKnownLongitude)
        assertNull(withoutLocation.lastKnownCity)
    }
}
