package com.byron.trucaller.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    // ---- isValidPhone ----

    @Test
    fun `isValidPhone accepts correct E164 Ugandan number`() {
        assertTrue(isValidPhone("+256712345678"))
    }

    @Test
    fun `isValidPhone rejects number without country code`() {
        assertFalse(isValidPhone("0712345678"))
    }

    @Test
    fun `isValidPhone rejects number with wrong country code`() {
        assertFalse(isValidPhone("+254712345678"))
    }

    @Test
    fun `isValidPhone rejects too short number`() {
        assertFalse(isValidPhone("+25671234567"))
    }

    @Test
    fun `isValidPhone rejects too long number`() {
        assertFalse(isValidPhone("+2567123456789"))
    }

    @Test
    fun `isValidPhone strips whitespace before validation`() {
        assertTrue(isValidPhone("+256 712 345 678"))
    }

    @Test
    fun `isValidPhone rejects empty string`() {
        assertFalse(isValidPhone(""))
    }

    @Test
    fun `isValidPhone rejects letters mixed in`() {
        assertFalse(isValidPhone("+256712abc678"))
    }

    // ---- isValidPhoneInput ----

    @Test
    fun `isValidPhoneInput accepts 9 digit local number`() {
        assertTrue(isValidPhoneInput("712345678"))
    }

    @Test
    fun `isValidPhoneInput rejects 8 digits`() {
        assertFalse(isValidPhoneInput("71234567"))
    }

    @Test
    fun `isValidPhoneInput rejects 10 digits`() {
        assertFalse(isValidPhoneInput("7123456789"))
    }

    @Test
    fun `isValidPhoneInput strips whitespace`() {
        assertTrue(isValidPhoneInput("712 345 678"))
    }

    @Test
    fun `isValidPhoneInput rejects input with plus prefix`() {
        assertFalse(isValidPhoneInput("+712345678"))
    }

    // ---- isValidOtp ----

    @Test
    fun `isValidOtp accepts 6 digit OTP`() {
        assertTrue(isValidOtp("123456"))
    }

    @Test
    fun `isValidOtp rejects 5 digit OTP`() {
        assertFalse(isValidOtp("12345"))
    }

    @Test
    fun `isValidOtp rejects 7 digit OTP`() {
        assertFalse(isValidOtp("1234567"))
    }

    @Test
    fun `isValidOtp rejects OTP with letters`() {
        assertFalse(isValidOtp("12345a"))
    }

    @Test
    fun `isValidOtp rejects empty string`() {
        assertFalse(isValidOtp(""))
    }

    @Test
    fun `isValidOtp rejects OTP with spaces`() {
        assertFalse(isValidOtp("123 456"))
    }

    // ---- isValidPassword ----

    @Test
    fun `isValidPassword accepts 6 character password`() {
        assertTrue(isValidPassword("abcdef"))
    }

    @Test
    fun `isValidPassword accepts long password`() {
        assertTrue(isValidPassword("abcdefghijklmnop"))
    }

    @Test
    fun `isValidPassword rejects 5 character password`() {
        assertFalse(isValidPassword("abcde"))
    }

    @Test
    fun `isValidPassword rejects empty password`() {
        assertFalse(isValidPassword(""))
    }

    // ---- getPasswordStrength ----

    @Test
    fun `getPasswordStrength returns weak for short password`() {
        assertEquals("weak", getPasswordStrength("abc"))
    }

    @Test
    fun `getPasswordStrength returns weak for 5 chars`() {
        assertEquals("weak", getPasswordStrength("abcde"))
    }

    @Test
    fun `getPasswordStrength returns medium for 6 chars with upper and lower`() {
        // 6+ chars, score=2 (hasUpper + hasLower) -> medium
        assertEquals("medium", getPasswordStrength("Abcdef"))
    }

    @Test
    fun `getPasswordStrength returns medium for 6 chars with lower and number`() {
        // 6+ chars, score=2 (hasLower + hasNumber) -> medium
        assertEquals("medium", getPasswordStrength("abcde1"))
    }

    @Test
    fun `getPasswordStrength returns strong for 8 chars with 3 categories`() {
        // 8+ chars, score=3 (hasUpper + hasLower + hasNumber) -> strong
        assertEquals("strong", getPasswordStrength("Abcdef12"))
    }

    @Test
    fun `getPasswordStrength returns strong for 8 chars with all categories`() {
        // 8+ chars, score=4 -> strong
        assertEquals("strong", getPasswordStrength("Abcde1@x"))
    }

    @Test
    fun `getPasswordStrength returns weak for 6 lowercase only`() {
        // 6 chars, score=1 (hasLower only) -> not >= 2, falls to else -> weak
        assertEquals("weak", getPasswordStrength("abcdef"))
    }

    @Test
    fun `getPasswordStrength returns weak for 8 lowercase only`() {
        // 8+ chars, score=1 (hasLower only) -> not >= 3, falls through
        // Then 8 >= 6 && score(1) >= 2? No -> else -> weak
        assertEquals("weak", getPasswordStrength("abcdefgh"))
    }

    // ---- isValidPin ----

    @Test
    fun `isValidPin accepts exactly 4 digits`() {
        assertTrue(isValidPin("1234"))
    }

    @Test
    fun `isValidPin rejects 3 digits`() {
        assertFalse(isValidPin("123"))
    }

    @Test
    fun `isValidPin rejects 5 digits`() {
        assertFalse(isValidPin("12345"))
    }

    @Test
    fun `isValidPin rejects letters`() {
        assertFalse(isValidPin("12ab"))
    }

    @Test
    fun `isValidPin rejects empty string`() {
        assertFalse(isValidPin(""))
    }

    @Test
    fun `isValidPin accepts all zeros`() {
        assertTrue(isValidPin("0000"))
    }

    // ---- hashPassword ----

    @Test
    fun `hashPassword returns consistent SHA-256 hex for same input`() {
        val hash1 = hashPassword("password123")
        val hash2 = hashPassword("password123")
        assertEquals(hash1, hash2)
    }

    @Test
    fun `hashPassword returns 64 hex characters`() {
        val hash = hashPassword("test")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `hashPassword produces different hashes for different inputs`() {
        assertNotEquals(hashPassword("password1"), hashPassword("password2"))
    }

    @Test
    fun `hashPassword matches known SHA-256 value`() {
        // SHA-256 of empty string is well-known
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, hashPassword(""))
    }
}
