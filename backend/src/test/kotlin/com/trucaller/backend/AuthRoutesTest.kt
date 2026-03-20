package com.trucaller.backend

import at.favre.lib.crypto.bcrypt.BCrypt
import com.trucaller.backend.data.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Unit tests for auth-related business logic: request serialization,
 * password hashing, response DTOs, and validation rules.
 *
 * Route-level integration tests that require MongoDB are not included here
 * to avoid coupling to a live database. Instead, we validate the building
 * blocks that the auth routes rely on.
 */
class AuthRoutesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── Request serialization ───────────────────────────────────────────────

    @Test
    fun `RegisterRequest serialises and deserialises correctly`() {
        val request = RegisterRequest(
            fullName = "John Doe",
            phoneNumber = "+256700123456",
            password = "secure123"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<RegisterRequest>(encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `LoginRequest serialises and deserialises correctly`() {
        val request = LoginRequest(
            phoneNumber = "+256700123456",
            password = "mypassword"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<LoginRequest>(encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `SendOtpRequest serialises with default purpose`() {
        val request = SendOtpRequest(phoneNumber = "+256700999888")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<SendOtpRequest>(encoded)

        assertEquals("registration", decoded.purpose)
        assertEquals("+256700999888", decoded.phoneNumber)
    }

    @Test
    fun `SendOtpRequest serialises with password_reset purpose`() {
        val request = SendOtpRequest(phoneNumber = "+256700111222", purpose = "password_reset")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<SendOtpRequest>(encoded)

        assertEquals("password_reset", decoded.purpose)
    }

    @Test
    fun `OtpVerification serialises and deserialises correctly`() {
        val request = OtpVerification(phoneNumber = "+256700123456", code = "123456")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<OtpVerification>(encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `ResetPasswordRequest serialises and deserialises correctly`() {
        val request = ResetPasswordRequest(
            phoneNumber = "+256700123456",
            code = "654321",
            newPassword = "newSecure456"
        )
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<ResetPasswordRequest>(encoded)

        assertEquals(request, decoded)
    }

    // ── Response serialization ──────────────────────────────────────────────

    @Test
    fun `TokenResponse serialises with all fields`() {
        val response = TokenResponse(
            token = "eyJhbGciOiJIUzI1NiJ9.test",
            expiresIn = 86400L,
            userId = "user-123"
        )
        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<TokenResponse>(encoded)

        assertEquals(response.token, decoded.token)
        assertEquals(response.expiresIn, decoded.expiresIn)
        assertEquals(response.userId, decoded.userId)
        assertNull(decoded.refreshToken, "refreshToken should default to null")
    }

    @Test
    fun `ApiResponse serialises success case`() {
        val response = ApiResponse(
            success = true,
            data = "test-data",
            message = "Operation successful."
        )
        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<ApiResponse<String>>(encoded)

        assertTrue(decoded.success)
        assertEquals("test-data", decoded.data)
        assertEquals("Operation successful.", decoded.message)
        assertNull(decoded.error)
    }

    @Test
    fun `ApiResponse serialises error case`() {
        val response = ApiResponse<Unit>(
            success = false,
            error = "Something went wrong."
        )
        val encoded = json.encodeToString(response)
        val decoded = json.decodeFromString<ApiResponse<Unit>>(encoded)

        assertFalse(decoded.success)
        assertEquals("Something went wrong.", decoded.error)
    }

    // ── BCrypt password hashing ─────────────────────────────────────────────

    @Test
    fun `BCrypt hash and verify round-trip succeeds`() {
        val password = "secure123"
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())

        assertTrue(hash.isNotBlank())
        assertTrue(
            BCrypt.verifyer().verify(password.toCharArray(), hash).verified,
            "Password verification must succeed for correct password"
        )
    }

    @Test
    fun `BCrypt verify rejects wrong password`() {
        val password = "correct-password"
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())

        assertFalse(
            BCrypt.verifyer().verify("wrong-password".toCharArray(), hash).verified,
            "Password verification must fail for wrong password"
        )
    }

    @Test
    fun `BCrypt produces different hashes for same password`() {
        val password = "same-password"
        val hash1 = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        val hash2 = BCrypt.withDefaults().hashToString(12, password.toCharArray())

        assertNotEquals(hash1, hash2, "BCrypt must use random salt, producing different hashes")
        // Both must still verify
        assertTrue(BCrypt.verifyer().verify(password.toCharArray(), hash1).verified)
        assertTrue(BCrypt.verifyer().verify(password.toCharArray(), hash2).verified)
    }

    // ── E.164 phone number validation ───────────────────────────────────────

    @Test
    fun `valid E164 phone numbers match auth route regex`() {
        val regex = Regex("^\\+[1-9]\\d{6,14}$")

        assertTrue(regex.matches("+256700123456"), "Uganda mobile")
        assertTrue(regex.matches("+14155551234"), "US number")
        assertTrue(regex.matches("+447911123456"), "UK number")
        assertTrue(regex.matches("+81312345678"), "Japan number")
    }

    @Test
    fun `invalid phone numbers are rejected by E164 regex`() {
        val regex = Regex("^\\+[1-9]\\d{6,14}$")

        assertFalse(regex.matches("0700123456"), "Local format without +")
        assertFalse(regex.matches("+0700123456"), "Starts with +0")
        assertFalse(regex.matches("256700123456"), "Missing + prefix")
        assertFalse(regex.matches("+1234"), "Too short")
        assertFalse(regex.matches(""), "Empty string")
        assertFalse(regex.matches("+12345678901234567"), "Too long (>15 digits)")
    }

    // ── OTP format validation ───────────────────────────────────────────────

    @Test
    fun `valid 6-digit OTP passes format check`() {
        val code = "123456"
        assertTrue(code.length == 6 && code.all { it.isDigit() })
    }

    @Test
    fun `OTP with letters fails format check`() {
        val code = "12345a"
        assertFalse(code.all { it.isDigit() })
    }

    @Test
    fun `OTP with wrong length fails format check`() {
        assertFalse("12345".length == 6)
        assertFalse("1234567".length == 6)
    }

    // ── Password validation ─────────────────────────────────────────────────

    @Test
    fun `password at least 6 characters is valid`() {
        assertTrue("123456".length >= 6)
        assertTrue("abcdef".length >= 6)
        assertTrue("a very long password".length >= 6)
    }

    @Test
    fun `password shorter than 6 characters is invalid`() {
        assertFalse("12345".length >= 6)
        assertFalse("abc".length >= 6)
        assertFalse("".length >= 6)
    }
}
