package com.trucaller.backend.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Sends OTP and other transactional SMS messages via Africa's Talking SMS API.
 *
 * Credentials are read from environment variables at startup:
 *   AT_API_KEY   — Africa's Talking API key
 *   AT_USERNAME  — Africa's Talking username (e.g. "sandbox" in dev)
 *   AT_SENDER_ID — Alphanumeric sender ID (optional; omit for short-code)
 *
 * If AT_API_KEY is absent the service operates in dev-log mode: OTP codes are
 * printed to the server log instead of being sent. This lets the backend run
 * without real AT credentials during local development.
 */
object SmsService {

    private val logger = LoggerFactory.getLogger(SmsService::class.java)

    private val apiKey: String? = System.getenv("AT_API_KEY")
    private val username: String = System.getenv("AT_USERNAME") ?: "sandbox"
    private val senderId: String? = System.getenv("AT_SENDER_ID")

    private val isEnabled: Boolean get() = !apiKey.isNullOrBlank()

    // Sandbox and production use different base URLs
    private val AT_SMS_URL = if (username == "sandbox")
        "https://api.sandbox.africastalking.com/version1/messaging"
    else
        "https://api.africastalking.com/version1/messaging"

    private val httpClient = HttpClient(CIO) {
        engine { requestTimeout = 15_000 }
    }

    /**
     * Sends a 6-digit OTP to [phoneNumber].
     *
     * The message text conforms to E.164 format (+256XXXXXXXXX).
     *
     * @return true if the SMS was accepted by Africa's Talking, false otherwise.
     *         In dev-log mode always returns true.
     */
    suspend fun sendOtp(phoneNumber: String, code: String): Boolean {
        val message = "Your TruCaller verification code is: $code. Valid for 10 minutes. Do not share this code."

        if (!isEnabled) {
            logger.warn("SMS gateway not configured (AT_API_KEY missing) — OTP for $phoneNumber: $code")
            return true
        }

        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = httpClient.post(AT_SMS_URL) {
                    header("apiKey", apiKey)
                    header("Accept", "application/json")
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(FormDataContent(Parameters.build {
                        append("username", username)
                        append("to", phoneNumber)
                        append("message", message)
                        if (!senderId.isNullOrBlank()) append("from", senderId)
                    }))
                }

                val body = response.bodyAsText()
                if (response.status.isSuccess()) {
                    logger.info("OTP SMS sent to $phoneNumber via Africa's Talking")
                    true
                } else {
                    logger.error("Africa's Talking SMS failed (HTTP ${response.status.value}): $body")
                    false
                }
            } catch (e: Exception) {
                logger.error("SMS send failed for $phoneNumber: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Sends a plain text [message] to [phoneNumber].
     * Used for non-OTP alerts (e.g. stolen-device recovery notification).
     */
    suspend fun sendRaw(phoneNumber: String, message: String): Boolean {
        if (!isEnabled) {
            logger.warn("SMS gateway not configured — skipping message to $phoneNumber: $message")
            return true
        }

        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = httpClient.post(AT_SMS_URL) {
                    header("apiKey", apiKey)
                    header("Accept", "application/json")
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(FormDataContent(Parameters.build {
                        append("username", username)
                        append("to", phoneNumber)
                        append("message", message)
                        if (!senderId.isNullOrBlank()) append("from", senderId)
                    }))
                }

                response.status.isSuccess().also { ok ->
                    if (!ok) logger.error("AT SMS raw send failed: HTTP ${response.status.value}")
                }
            } catch (e: Exception) {
                logger.error("SMS raw send failed for $phoneNumber: ${e.message}", e)
                false
            }
        }
    }
}
