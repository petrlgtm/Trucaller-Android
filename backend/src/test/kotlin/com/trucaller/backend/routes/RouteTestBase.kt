package com.trucaller.backend.routes

import com.trucaller.backend.data.models.ApiResponse
import kotlinx.serialization.json.Json
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Base class providing shared test utilities for backend route integration tests.
 *
 * Includes JSON parsing configuration and helper assertion methods for
 * validating the standard [ApiResponse] envelope format used across all endpoints.
 */
abstract class RouteTestBase {

    protected val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Asserts that the given JSON string conforms to the standard success response shape:
     * `{ "success": true, "message": "..." }` or `{ "success": true, "data": ... }`.
     */
    protected fun assertSuccessEnvelope(jsonString: String) {
        val decoded = json.decodeFromString<ApiResponse<String>>(jsonString)
        assertTrue(decoded.success, "Expected success=true in response")
    }

    /**
     * Asserts that the given JSON string conforms to the standard error response shape:
     * `{ "success": false, "error": "..." }`.
     */
    protected fun assertErrorEnvelope(jsonString: String) {
        val decoded = json.decodeFromString<ApiResponse<String>>(jsonString)
        assertFalse(decoded.success, "Expected success=false in response")
        assertNotNull(decoded.error, "Error response must include an 'error' field")
    }

    /**
     * Parses a JSON response and returns its 'success' flag.
     */
    protected fun isSuccessResponse(jsonString: String): Boolean {
        return try {
            val decoded = json.decodeFromString<ApiResponse<String>>(jsonString)
            decoded.success
        } catch (e: Exception) {
            false
        }
    }
}
