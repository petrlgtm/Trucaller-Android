package com.example.trucaller.util

fun isValidPhone(phone: String): Boolean {
    val cleaned = phone.replace("\\s".toRegex(), "")
    return Regex("^\\+256[0-9]{9}$").matches(cleaned)
}

fun isValidPhoneInput(input: String): Boolean {
    return Regex("^[0-9]{9}$").matches(input.replace("\\s".toRegex(), ""))
}

fun isValidOtp(otp: String): Boolean {
    return Regex("^[0-9]{6}$").matches(otp)
}

fun isValidPassword(password: String): Boolean {
    return password.length >= 6
}

fun getPasswordStrength(password: String): String {
    if (password.length < 6) return "weak"
    val hasUpper = Regex("[A-Z]").containsMatchIn(password)
    val hasLower = Regex("[a-z]").containsMatchIn(password)
    val hasNumber = Regex("[0-9]").containsMatchIn(password)
    val hasSpecial = Regex("[^A-Za-z0-9]").containsMatchIn(password)
    val score = listOf(hasUpper, hasLower, hasNumber, hasSpecial).count { it }
    return when {
        password.length >= 8 && score >= 3 -> "strong"
        password.length >= 6 && score >= 2 -> "medium"
        else -> "weak"
    }
}

fun isValidPin(pin: String): Boolean {
    return Regex("^[0-9]{4}$").matches(pin)
}

fun hashPassword(password: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}
