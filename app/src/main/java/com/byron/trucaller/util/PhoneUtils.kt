package com.byron.trucaller.util

/**
 * Shared phone number normalization utility.
 * Converts various local and international phone formats to consistent E.164 format.
 * Defaults to +256 (Uganda) for ambiguous local numbers.
 */
object PhoneUtils {

    private val STRIP_PATTERN = Regex("[\\s\\-().]")

    /**
     * Normalize a raw phone string to E.164 format.
     *
     * Handles:
     * - +256XXXXXXXXX  (already E.164 Ugandan)
     * - 0XXXXXXXXX     (local Ugandan with leading zero, 10 digits)
     * - 256XXXXXXXXX   (Ugandan without +, 12 digits)
     * - 7XXXXXXXXX     (9-digit Ugandan subscriber number starting with 7 or 3)
     * - +XXX...        (other international formats, kept as-is)
     *
     * @param raw The raw phone number string from any source
     * @return The phone number in E.164 format, or the cleaned input if no rule matches
     */
    fun normalizePhone(raw: String): String {
        val cleaned = raw.replace(STRIP_PATTERN, "")
        if (cleaned.isEmpty()) return cleaned

        return when {
            // Already international E.164 format — keep as-is
            cleaned.startsWith("+") -> cleaned

            // Ugandan country code without '+' (e.g., "256712345678", length >= 12)
            cleaned.startsWith("256") && cleaned.length >= 12 -> "+$cleaned"

            // Local Ugandan with leading zero (e.g., "0712345678", exactly 10 digits)
            cleaned.startsWith("0") && cleaned.length == 10 -> "+256${cleaned.substring(1)}"

            // Bare Ugandan subscriber number (e.g., "712345678", 9 digits starting with 7 or 3)
            cleaned.length == 9 && (cleaned.startsWith("7") || cleaned.startsWith("3")) -> "+256$cleaned"

            // No matching rule — return cleaned number as-is
            else -> cleaned
        }
    }

    /**
     * Compare two phone numbers for equality using their normalized E.164 forms.
     *
     * @param a First phone number (any format)
     * @param b Second phone number (any format)
     * @return true if both numbers normalize to the same E.164 string
     */
    fun isSameNumber(a: String, b: String): Boolean {
        return normalizePhone(a) == normalizePhone(b)
    }
}
