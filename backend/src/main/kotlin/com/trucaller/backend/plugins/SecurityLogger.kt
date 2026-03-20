package com.trucaller.backend.plugins

import org.slf4j.LoggerFactory

/**
 * Centralized security event logger for audit trail.
 * Logs authentication events, rate-limit violations, and suspicious activity.
 */
object SecurityLogger {
    private val logger = LoggerFactory.getLogger("SecurityAudit")

    fun logAuthSuccess(userId: String, ip: String) {
        logger.info("AUTH_SUCCESS userId=$userId ip=$ip")
    }

    fun logAuthFailure(phoneNumber: String, ip: String, reason: String) {
        logger.warn("AUTH_FAILURE phone=$phoneNumber ip=$ip reason=$reason")
    }

    fun logRateLimitExceeded(key: String, ip: String) {
        logger.warn("RATE_LIMIT_EXCEEDED key=$key ip=$ip")
    }

    fun logSuspiciousActivity(ip: String, detail: String) {
        logger.warn("SUSPICIOUS_ACTIVITY ip=$ip detail=$detail")
    }

    fun logOtpSent(phoneNumber: String, purpose: String, ip: String) {
        logger.info("OTP_SENT phone=$phoneNumber purpose=$purpose ip=$ip")
    }

    fun logOtpVerified(phoneNumber: String, ip: String) {
        logger.info("OTP_VERIFIED phone=$phoneNumber ip=$ip")
    }

    fun logOtpFailed(phoneNumber: String, ip: String, reason: String) {
        logger.warn("OTP_FAILED phone=$phoneNumber ip=$ip reason=$reason")
    }

    fun logAdminAction(adminId: String, action: String, target: String, ip: String) {
        logger.info("ADMIN_ACTION adminId=$adminId action=$action target=$target ip=$ip")
    }
}
