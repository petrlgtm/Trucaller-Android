package com.trucaller.backend.service

object PhoneNormalizer {

    // EAC country prefixes: (countryCode, localLength, localPrefix)
    // localLength = digits after stripping leading 0 or country code
    private data class CountryRule(
        val countryCode: String,   // e.g. "256"
        val localDigits: Int,      // subscriber number length (after 0 or CC stripped)
        val localPrefixes: List<String> = emptyList() // optional prefix constraints
    )

    private val eacRules = listOf(
        CountryRule("256", 9),  // Uganda: 0XX XXXXXXX  / 256XX XXXXXXX
        CountryRule("254", 9),  // Kenya: 07X XXXXXXX / 01X XXXXXXX
        CountryRule("255", 9),  // Tanzania: 07X XXXXXXX
        CountryRule("250", 9),  // Rwanda: 07X XXXXXXX
        CountryRule("257", 8),  // Burundi: 07X XXXXX
        CountryRule("211", 9),  // South Sudan: 09X XXXXXXX
        CountryRule("243", 9),  // DRC: 08X XXXXXXX
    )

    fun normalize(phone: String): String {
        val digits = phone.replace(Regex("[^+\\d]"), "")

        // Already E.164 (starts with +)
        if (digits.startsWith("+")) return digits

        // Try to match a full number with known country code prefix (no +)
        for (rule in eacRules) {
            val fullLen = rule.countryCode.length + rule.localDigits
            if (digits.startsWith(rule.countryCode) && digits.length == fullLen) {
                return "+$digits"
            }
        }

        // Try local format: leading 0 + subscriber digits — match by length
        if (digits.startsWith("0")) {
            val subscriber = digits.substring(1)
            for (rule in eacRules) {
                if (subscriber.length == rule.localDigits) {
                    // Uganda is the default for ambiguous local numbers
                    if (rule.countryCode == "256") return "+256$subscriber"
                }
            }
            // Unknown country, best-effort Uganda fallback for 9-digit subscriber
            if (digits.length == 10) return "+256${digits.substring(1)}"
        }

        // Bare 9-digit number — assume Uganda
        if (digits.length == 9 && !digits.startsWith("0")) return "+256$digits"

        return "+$digits"
    }

    fun isValid(phone: String): Boolean =
        phone.matches(Regex("^\\+[1-9]\\d{7,14}$"))
}
