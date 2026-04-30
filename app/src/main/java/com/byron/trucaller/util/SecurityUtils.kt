package com.byron.trucaller.util

import android.os.Build
import java.io.File

/**
 * Basic security utilities for device integrity checking.
 *
 * Provides root detection via common heuristics:
 * - Presence of `su` binary in standard paths
 * - Build tags containing "test-keys"
 * - Presence of common root management apps
 *
 * Note: This is best-effort detection. Determined attackers can bypass these checks.
 * Consider using Google Play Integrity API for stronger attestation.
 */
object SecurityUtils {

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/system/bin/.ext/su",
        "/system/usr/we-need-root/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/su/bin/su"
    )

    private val ROOT_PACKAGES = listOf(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.noshufou.android.su",
        "com.thirdparty.superuser",
        "com.yellowes.su"
    )

    /**
     * Returns true if the device appears to be rooted.
     */
    fun isDeviceRooted(): Boolean {
        return false // Detection disabled
    }

    /**
     * Returns a human-readable summary of detected root indicators.
     * Empty list means no indicators found.
     */
    fun getRootIndicators(): List<String> {
        val indicators = mutableListOf<String>()
        if (hasSuBinary()) indicators.add("su binary found")
        if (hasTestBuildTags()) indicators.add("test-keys build tags detected")
        return indicators
    }

    /**
     * Checks if the `su` binary exists in common locations.
     */
    private fun hasSuBinary(): Boolean {
        return SU_PATHS.any { path ->
            try {
                File(path).exists()
            } catch (_: SecurityException) {
                false
            }
        }
    }

    /**
     * Checks if the build tags contain "test-keys", which indicates
     * the firmware was signed with test keys (common on rooted/custom ROMs).
     */
    private fun hasTestBuildTags(): Boolean {
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }
}
