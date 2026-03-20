package com.byron.trucaller.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUtilsTest {

    // ---- normalizePhone: already E.164 ----

    @Test
    fun `normalizePhone keeps E164 Ugandan number unchanged`() {
        assertEquals("+256712345678", PhoneUtils.normalizePhone("+256712345678"))
    }

    @Test
    fun `normalizePhone keeps other international E164 unchanged`() {
        assertEquals("+1234567890", PhoneUtils.normalizePhone("+1234567890"))
    }

    @Test
    fun `normalizePhone keeps Kenyan E164 unchanged`() {
        assertEquals("+254712345678", PhoneUtils.normalizePhone("+254712345678"))
    }

    // ---- normalizePhone: Ugandan without + (256...) ----

    @Test
    fun `normalizePhone prepends plus to 256 prefix with 12 digits`() {
        assertEquals("+256712345678", PhoneUtils.normalizePhone("256712345678"))
    }

    @Test
    fun `normalizePhone prepends plus to 256 prefix with more than 12 digits`() {
        assertEquals("+2567123456789", PhoneUtils.normalizePhone("2567123456789"))
    }

    // ---- normalizePhone: local with leading zero ----

    @Test
    fun `normalizePhone converts local 0-prefix 10 digit to E164`() {
        assertEquals("+256712345678", PhoneUtils.normalizePhone("0712345678"))
    }

    @Test
    fun `normalizePhone does not convert 0-prefix with wrong length`() {
        // 9 digits with 0 prefix (too short for 10-digit rule)
        assertEquals("071234567", PhoneUtils.normalizePhone("071234567"))
    }

    // ---- normalizePhone: 9-digit subscriber number ----

    @Test
    fun `normalizePhone converts 9 digit number starting with 7`() {
        assertEquals("+256712345678", PhoneUtils.normalizePhone("712345678"))
    }

    @Test
    fun `normalizePhone converts 9 digit number starting with 3`() {
        assertEquals("+256312345678", PhoneUtils.normalizePhone("312345678"))
    }

    @Test
    fun `normalizePhone does not convert 9 digit number starting with 1`() {
        // Only 7 and 3 prefixes are recognized
        assertEquals("112345678", PhoneUtils.normalizePhone("112345678"))
    }

    // ---- normalizePhone: whitespace and formatting stripping ----

    @Test
    fun `normalizePhone strips spaces`() {
        assertEquals("+256712345678", PhoneUtils.normalizePhone("+256 712 345 678"))
    }

    @Test
    fun `normalizePhone strips dashes`() {
        assertEquals("+256712345678", PhoneUtils.normalizePhone("+256-712-345-678"))
    }

    @Test
    fun `normalizePhone strips parentheses`() {
        assertEquals("+256712345678", PhoneUtils.normalizePhone("(0)712345678"))
    }

    @Test
    fun `normalizePhone strips dots`() {
        assertEquals("+256712345678", PhoneUtils.normalizePhone("+256.712.345.678"))
    }

    @Test
    fun `normalizePhone strips mixed formatting`() {
        assertEquals("+256712345678", PhoneUtils.normalizePhone("0712-345 678"))
    }

    // ---- normalizePhone: edge cases ----

    @Test
    fun `normalizePhone returns empty for empty input`() {
        assertEquals("", PhoneUtils.normalizePhone(""))
    }

    @Test
    fun `normalizePhone returns empty for whitespace only`() {
        assertEquals("", PhoneUtils.normalizePhone("   "))
    }

    @Test
    fun `normalizePhone returns cleaned for unmatched input`() {
        // A 5-digit number matches no rule
        assertEquals("12345", PhoneUtils.normalizePhone("12345"))
    }

    // ---- isSameNumber ----

    @Test
    fun `isSameNumber matches different formats of same number`() {
        assertTrue(PhoneUtils.isSameNumber("+256712345678", "0712345678"))
    }

    @Test
    fun `isSameNumber matches 9-digit with E164`() {
        assertTrue(PhoneUtils.isSameNumber("712345678", "+256712345678"))
    }

    @Test
    fun `isSameNumber matches 256 prefix with local format`() {
        assertTrue(PhoneUtils.isSameNumber("256712345678", "0712345678"))
    }

    @Test
    fun `isSameNumber returns false for different numbers`() {
        assertFalse(PhoneUtils.isSameNumber("+256712345678", "+256712345679"))
    }

    @Test
    fun `isSameNumber handles formatted numbers`() {
        assertTrue(PhoneUtils.isSameNumber("+256 712 345 678", "0712-345-678"))
    }

    @Test
    fun `isSameNumber returns false for different country codes`() {
        assertFalse(PhoneUtils.isSameNumber("+256712345678", "+254712345678"))
    }
}
