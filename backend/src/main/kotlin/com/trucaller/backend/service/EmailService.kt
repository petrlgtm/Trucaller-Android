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
 * Sends OTP and transactional emails via the Resend HTTP API.
 *
 * Required environment variables:
 *   RESEND_API_KEY — API key from resend.com
 *   RESEND_FROM    — Verified sender address, e.g. "TruCaller <noreply@yourdomain.com>"
 *                    Defaults to "TruCaller <onboarding@resend.dev>" (test only)
 *
 * When RESEND_API_KEY is absent the service operates in dev-log mode:
 * OTP codes are printed to the server log instead of being sent.
 */
object EmailService {

    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    private val apiKey: String? = System.getenv("RESEND_API_KEY")
    private val fromAddress: String = System.getenv("RESEND_FROM") ?: "TruCaller <onboarding@resend.dev>"

    val isEnabled: Boolean get() = !apiKey.isNullOrBlank()

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
            socketTimeoutMillis = 10_000
        }
    }

    /**
     * Sends a 6-digit OTP to [toEmail] via Resend.
     * Returns true if accepted, false on failure.
     * In dev-log mode always returns true.
     */
    suspend fun sendOtp(toEmail: String, code: String): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled) {
            logger.warn("Email gateway not configured (RESEND_API_KEY missing) — OTP for $toEmail: $code")
            return@withContext true
        }

        val body = """
            {
              "from": "$fromAddress",
              "to": ["$toEmail"],
              "subject": "Your TruCaller verification code",
              "text": "Your TruCaller verification code is:\n\n$code\n\nThis code expires in 10 minutes. Do not share it with anyone."
            }
        """.trimIndent()

        return@withContext try {
            val response: HttpResponse = httpClient.post("https://api.resend.com/emails") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.isSuccess()) {
                logger.info("OTP email sent to $toEmail via Resend")
                true
            } else {
                logger.error("Resend rejected email to $toEmail: HTTP ${response.status.value} — ${response.bodyAsText()}")
                false
            }
        } catch (e: Exception) {
            logger.error("Email send failed for $toEmail: ${e.message}", e)
            false
        }
    }
}
