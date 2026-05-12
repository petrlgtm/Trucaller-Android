package com.trucaller.backend.service

object PhoneNormalizer {

    fun normalize(phone: String): String {
        val digits = phone.replace(Regex("[^+\\d]"), "")
        return when {
            digits.startsWith("+") -> digits
            // 256XXXXXXXXX (12 digits) → +256XXXXXXXXX
            digits.startsWith("256") && digits.length == 12 -> "+$digits"
            // 0XXXXXXXXX (10 digits, Uganda local) → +256XXXXXXXXX
            digits.startsWith("0") && digits.length == 10 -> "+256${digits.substring(1)}"
            // Bare 9-digit number without country prefix → assume Uganda +256
            digits.length == 9 && !digits.startsWith("0") -> "+256$digits"
            else -> "+$digits"
        }
    }

    fun isValid(phone: String): Boolean =
        phone.matches(Regex("^\\+[1-9]\\d{7,14}$"))
}
