package com.trucaller.backend.service

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Sends OTP and transactional emails via Gmail SMTP.
 *
 * Required environment variables:
 *   SMTP_HOST     — defaults to smtp.gmail.com
 *   SMTP_PORT     — defaults to 587
 *   SMTP_USER     — Gmail address used as sender
 *   SMTP_PASSWORD — Gmail App Password (16-char, spaces stripped)
 *
 * When SMTP_USER/SMTP_PASSWORD are absent the service operates in dev-log mode:
 * OTP codes are printed to the server log instead of being sent.
 */
object EmailService {

    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    private val smtpHost: String = System.getenv("SMTP_HOST") ?: "smtp.gmail.com"
    private val smtpPort: String = System.getenv("SMTP_PORT") ?: "587"
    private val smtpUser: String? = System.getenv("SMTP_USER")
    private val smtpPassword: String? = System.getenv("SMTP_PASSWORD")?.replace(" ", "")

    val isEnabled: Boolean get() = !smtpUser.isNullOrBlank() && !smtpPassword.isNullOrBlank()

    private fun buildSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort)
            put("mail.smtp.ssl.trust", smtpHost)
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(smtpUser, smtpPassword)
        })
    }

    /**
     * Sends a 6-digit OTP to [toEmail].
     * Returns true if the email was accepted, false on transport failure.
     * In dev-log mode always returns true.
     */
    suspend fun sendOtp(toEmail: String, code: String): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled) {
            logger.warn("Email gateway not configured (SMTP_USER/SMTP_PASSWORD missing) — OTP for $toEmail: $code")
            return@withContext true
        }

        return@withContext try {
            val session = buildSession()
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(smtpUser, "TruCaller"))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                subject = "Your TruCaller verification code"
                setText(
                    """
                    Your TruCaller verification code is:

                    $code

                    This code expires in 10 minutes. Do not share it with anyone.
                    """.trimIndent()
                )
            }
            Transport.send(message)
            logger.info("OTP email sent to $toEmail")
            true
        } catch (e: Exception) {
            logger.error("Email send failed for $toEmail: ${e.message}", e)
            false
        }
    }
}
