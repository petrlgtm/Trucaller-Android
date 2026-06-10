package com.trucaller.backend.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Sends OTP and transactional emails via the Brevo (Sendinblue) HTTP API.
 *
 * Required environment variables:
 *   BREVO_API_KEY    — API key from brevo.com (Settings → API Keys)
 *   BREVO_FROM_EMAIL — Verified sender email added under Senders & IP → Senders
 *   BREVO_FROM_NAME  — Display name (defaults to "TruCaller")
 *
 * When BREVO_API_KEY is absent the service operates in dev-log mode:
 * OTP codes are printed to the server log instead of being sent.
 */
object EmailService {

    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    private val apiKey: String? = System.getenv("BREVO_API_KEY")
    private val fromEmail: String? = System.getenv("BREVO_FROM_EMAIL")
    private val fromName: String = System.getenv("BREVO_FROM_NAME") ?: "TruCaller"

    val isEnabled: Boolean get() = !apiKey.isNullOrBlank() && !fromEmail.isNullOrBlank()

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
            socketTimeoutMillis = 10_000
        }
    }

    /**
     * Sends a 6-digit OTP to [toEmail] via Brevo.
     * Returns true if accepted, false on failure.
     * In dev-log mode always returns true.
     */
    suspend fun sendOtp(toEmail: String, code: String): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled) {
            logger.warn("Email gateway not configured (BREVO_API_KEY/BREVO_FROM_EMAIL missing) — OTP for $toEmail: $code")
            return@withContext true
        }

        val body = """
            {
              "sender": { "name": "$fromName", "email": "$fromEmail" },
              "to": [{ "email": "$toEmail" }],
              "subject": "Your TruCaller verification code",
              "textContent": "Your TruCaller verification code is:\n\n$code\n\nThis code expires in 10 minutes. Do not share it with anyone."
            }
        """.trimIndent()

        return@withContext try {
            val response: HttpResponse = httpClient.post("https://api.brevo.com/v3/smtp/email") {
                header("api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.isSuccess()) {
                logger.info("OTP email sent to $toEmail via Brevo")
                true
            } else {
                logger.error("Brevo rejected email to $toEmail: HTTP ${response.status.value} — ${response.bodyAsText()}")
                false
            }
        } catch (e: Exception) {
            logger.error("Email send failed for $toEmail: ${e.message}", e)
            false
        }
    }
}
