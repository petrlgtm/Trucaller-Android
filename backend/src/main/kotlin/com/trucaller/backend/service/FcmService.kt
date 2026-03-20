package com.trucaller.backend.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Singleton service that initialises Firebase Admin SDK and provides
 * a coroutine-friendly helper to send FCM data messages to devices.
 */
object FcmService {

    private val log = LoggerFactory.getLogger(FcmService::class.java)
    private var initialised = false

    /**
     * Initialises the Firebase Admin SDK.
     *
     * Credentials are resolved via the standard
     * `GOOGLE_APPLICATION_CREDENTIALS` environment variable (path to a
     * service-account JSON file) which is picked up automatically by
     * [GoogleCredentials.getApplicationDefault].
     *
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun initialize() {
        if (initialised) return

        try {
            if (FirebaseApp.getApps().isEmpty()) {
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build()
                FirebaseApp.initializeApp(options)
            }
            initialised = true
            log.info("Firebase Admin SDK initialised successfully")
        } catch (e: Exception) {
            log.error("Failed to initialise Firebase Admin SDK: ${e.message}", e)
        }
    }

    /**
     * Sends an FCM **data-only** message to the device identified by
     * [fcmToken].
     *
     * @return `true` when the message was accepted by FCM, `false` on
     *         any failure (invalid token, network error, SDK not ready, etc.).
     */
    suspend fun sendPush(fcmToken: String, data: Map<String, String>): Boolean {
        if (!initialised) {
            log.warn("FcmService not initialised — cannot send push")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val message = Message.builder()
                    .setToken(fcmToken)
                    .putAllData(data)
                    .build()

                val messageId = FirebaseMessaging.getInstance().send(message)
                log.info("FCM push sent successfully (messageId=$messageId)")
                true
            } catch (e: Exception) {
                log.error("FCM push failed: ${e.message}", e)
                false
            }
        }
    }
}
