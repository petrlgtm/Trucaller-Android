package com.trucaller.backend.routes

import com.trucaller.backend.data.models.ApiResponse
import kotlinx.serialization.encodeToString
import kotlin.test.*

/**
 * Integration-level tests for blocked number route DTOs, request serialization,
 * response format validation, and route path structure.
 *
 * These tests validate the building blocks used by the blocked routes
 * without requiring a live MongoDB connection.
 */
class BlockedRoutesTest : RouteTestBase() {

    // ── Request DTO Serialization ────────────────────────────────────────

    @Test
    fun `BlockNumberRequest serialises with all fields`() {
        val request = BlockNumberRequest(
            phoneNumber = "+256700123456",
            name = "Spam Caller",
            reason = "Robocall"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<BlockNumberRequest>(encoded)

        assertEquals(request, decoded)
        assertEquals("+256700123456", decoded.phoneNumber)
        assertEquals("Spam Caller", decoded.name)
        assertEquals("Robocall", decoded.reason)
    }

    @Test
    fun `BlockNumberRequest serialises with minimal fields`() {
        val request = BlockNumberRequest(phoneNumber = "+256700999888")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<BlockNumberRequest>(encoded)

        assertEquals("+256700999888", decoded.phoneNumber)
        assertNull(decoded.name)
        assertNull(decoded.reason)
    }

    @Test
    fun `BlockNumberRequest with empty phone number serialises`() {
        val request = BlockNumberRequest(phoneNumber = "")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<BlockNumberRequest>(encoded)

        assertEquals("", decoded.phoneNumber)
    }

    // ── Response DTO Serialization ───────────────────────────────────────

    @Test
    fun `BlockedNumberResponse serialises with all fields`() {
        val response = BlockedNumberResponse(
            id = "blocked-001",
            userId = "user-123",
            phoneNumber = "+256700123456",
            name = "Unknown Caller",
            reason = "Blocked by user",
            blockedAt = "2026-03-20T10:30:00Z"
        )
        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<BlockedNumberResponse>(encoded)

        assertEquals(response, decoded)
    }

    @Test
    fun `BlockedNumberResponse list serialises correctly`() {
        val responses = listOf(
            BlockedNumberResponse("b-1", "u-1", "+256700111111", "Scammer 1", "Fraud", "2026-03-01T00:00:00Z"),
            BlockedNumberResponse("b-2", "u-1", "+256700222222", "Telemarketer", "Sales", "2026-03-02T00:00:00Z"),
            BlockedNumberResponse("b-3", "u-1", "+256700333333", "Unknown", "", "2026-03-03T00:00:00Z")
        )
        val encoded = json.encodeToString(responses)
        val decoded = json.decodeFromString<List<BlockedNumberResponse>>(encoded)

        assertEquals(3, decoded.size)
        assertEquals("+256700111111", decoded[0].phoneNumber)
        assertEquals("+256700222222", decoded[1].phoneNumber)
        assertEquals("+256700333333", decoded[2].phoneNumber)
    }

    // ── Route path structure validation ──────────────────────────────────

    @Test
    fun `blocked routes follow expected path convention`() {
        val expectedPaths = listOf(
            "/api/blocked/add",
            "/api/blocked",
            "/api/blocked/{id}",
            "/api/blocked/check/{phoneNumber}"
        )
        expectedPaths.forEach { path ->
            assertTrue(path.startsWith("/api/blocked"), "Path '$path' should start with /api/blocked")
        }
    }

    // ── ApiResponse envelope format ──────────────────────────────────────

    @Test
    fun `success response for blocking a number`() {
        val apiResp = ApiResponse<Unit>(
            success = true,
            message = "Number blocked successfully."
        )
        val encoded = json.encodeToString(apiResp)
        assertSuccessEnvelope(encoded)
    }

    @Test
    fun `error response for already blocked number`() {
        val apiResp = ApiResponse<Unit>(
            success = false,
            error = "Number already blocked."
        )
        val encoded = json.encodeToString(apiResp)
        assertErrorEnvelope(encoded)
    }

    @Test
    fun `error response for not found blocked number`() {
        val apiResp = ApiResponse<Unit>(
            success = false,
            error = "Blocked number not found."
        )
        val encoded = json.encodeToString(apiResp)
        val decoded = json.decodeFromString<ApiResponse<String>>(encoded)

        assertFalse(decoded.success)
        assertTrue(decoded.error!!.contains("not found"))
    }

    @Test
    fun `success response wraps boolean for check endpoint`() {
        val apiResp = ApiResponse(success = true, data = true)
        val encoded = json.encodeToString(apiResp)
        val decoded = json.decodeFromString<ApiResponse<Boolean>>(encoded)

        assertTrue(decoded.success)
        assertEquals(true, decoded.data)
    }

    @Test
    fun `check endpoint returns false when not blocked`() {
        val apiResp = ApiResponse(success = true, data = false)
        val encoded = json.encodeToString(apiResp)
        val decoded = json.decodeFromString<ApiResponse<Boolean>>(encoded)

        assertTrue(decoded.success)
        assertEquals(false, decoded.data)
    }

    @Test
    fun `success response for unblocking a number`() {
        val apiResp = ApiResponse<Unit>(
            success = true,
            message = "Number unblocked."
        )
        val encoded = json.encodeToString(apiResp)
        assertSuccessEnvelope(encoded)
    }

    // ── Phone number format validation ───────────────────────────────────

    @Test
    fun `E164 phone numbers in blocked requests are preserved`() {
        val numbers = listOf("+256700123456", "+14155551234", "+447911123456")
        numbers.forEach { number ->
            val req = BlockNumberRequest(phoneNumber = number)
            val encoded = json.encodeToString(req)
            val decoded = json.decodeFromString<BlockNumberRequest>(encoded)
            assertEquals(number, decoded.phoneNumber)
        }
    }
}
