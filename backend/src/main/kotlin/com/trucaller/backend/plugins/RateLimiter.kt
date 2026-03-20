package com.trucaller.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * In-memory sliding-window rate limiter plugin for Ktor.
 *
 * Authenticated requests get [authLimitPerMin] requests/minute;
 * unauthenticated requests get [unauthLimitPerMin] requests/minute.
 * Keyed by client IP (+ userId for authenticated requests).
 */
class RateLimiterConfig {
    var authLimitPerMin: Int = 100
    var unauthLimitPerMin: Int = 10
}

val RateLimiterPlugin = createApplicationPlugin(name = "RateLimiter", createConfiguration = ::RateLimiterConfig) {
    val logger = LoggerFactory.getLogger("RateLimiter")
    val authLimit = pluginConfig.authLimitPerMin
    val unauthLimit = pluginConfig.unauthLimitPerMin

    // IP -> deque of timestamps (epoch millis)
    val requestLog = ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>>()

    val windowMs = 60_000L

    onCall { call ->
        val ip = call.request.local.remoteAddress
        val principal = call.principal<JWTPrincipal>()
        val key = if (principal != null) "auth:${principal.payload.getClaim("userId").asString() ?: ip}" else "unauth:$ip"
        val limit = if (principal != null) authLimit else unauthLimit

        val now = System.currentTimeMillis()
        val cutoff = now - windowMs

        val timestamps = requestLog.computeIfAbsent(key) { ConcurrentLinkedDeque() }

        // Evict expired entries
        while (timestamps.peekFirst()?.let { it < cutoff } == true) {
            timestamps.pollFirst()
        }

        if (timestamps.size >= limit) {
            logger.warn("Rate limit exceeded for key=$key (limit=$limit/min)")
            call.respond(
                HttpStatusCode.TooManyRequests,
                mapOf("success" to false, "error" to "Rate limit exceeded. Try again later.")
            )
            return@onCall
        }

        timestamps.addLast(now)
    }

    // Periodic cleanup of stale keys every 5 minutes (best-effort)
    environment?.monitor?.subscribe(ApplicationStarted) {
        val cleanupThread = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(300_000) // 5 min
                    val cutoff = System.currentTimeMillis() - windowMs
                    requestLog.entries.removeIf { (_, deque) ->
                        deque.removeIf { it < cutoff }
                        deque.isEmpty()
                    }
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "rate-limiter-cleanup")
        cleanupThread.isDaemon = true
        cleanupThread.start()
    }
}
