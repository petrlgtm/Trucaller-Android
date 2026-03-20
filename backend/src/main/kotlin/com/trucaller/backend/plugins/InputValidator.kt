package com.trucaller.backend.plugins

/**
 * Input validation utilities for sanitizing and checking user input.
 */
object InputValidator {

    /** Maximum length for general string fields (names, labels, etc.) */
    const val MAX_NAME_LENGTH = 200

    /** Maximum length for phone number fields. */
    const val MAX_PHONE_LENGTH = 16

    /** Maximum length for free-text fields (reasons, messages, notes). */
    const val MAX_TEXT_LENGTH = 1000

    /** Maximum length for password fields. */
    const val MAX_PASSWORD_LENGTH = 128

    /**
     * Sanitizes a string by trimming whitespace and limiting length.
     */
    fun sanitize(input: String, maxLength: Int = MAX_NAME_LENGTH): String =
        input.trim().take(maxLength)

    /**
     * Sanitizes a phone number — trims and limits length.
     */
    fun sanitizePhone(input: String): String =
        input.trim().take(MAX_PHONE_LENGTH)

    /**
     * Validates that a string is not blank and within length limits after trimming.
     */
    fun isValidName(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.isNotBlank() && trimmed.length <= MAX_NAME_LENGTH
    }

    /**
     * Validates E.164 phone number format.
     */
    fun isValidPhone(input: String): Boolean =
        input.trim().matches(Regex("^\\+[1-9]\\d{6,14}$"))

    /**
     * Validates password meets minimum requirements.
     */
    fun isValidPassword(input: String): Boolean =
        input.length in 6..MAX_PASSWORD_LENGTH
}
