package com.trucaller.backend.plugins

import com.trucaller.backend.auth.clientIp
import com.trucaller.backend.service.RedisCache
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Sliding-window rate limiter plugin.
 *
 * Uses Redis for persistent, cross-instance limiting when [RedisCache.isEnabled].
 * Falls back to an in-memory map when Redis is unavailable (single-instance dev
 * or Redis connection failure).
 *
 * Authenticated requests get [authLimitPerMin] req/min keyed on userId.
 * Unauthenticated requests get [unauthLimitPerMin] req/min keyed on IP.
 */
class RateLimiterConfig {
    var authLimitPerMin: Int = 100
    var unauthLimitPerMin: Int = 10
}

val RateLimiterPlugin = createApplicationPlugin(name = "RateLimiter", createConfiguration = ::RateLimiterConfig) {
    val logger = LoggerFactory.getLogger("RateLimiter")
    val authLimit = pluginConfig.authLimitPerMin
    val unauthLimit = pluginConfig.unauthLimitPerMin
    val windowSeconds = 60L

    // In-memory fallback (used when Redis is unavailable)
    val memoryLog = ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>>()

    onCall { call ->
        val ip = call.clientIp()
        val principal = call.principal<JWTPrincipal>()
        val key = if (principal != null) {
            "rl:auth:${principal.payload.getClaim("userId").asString() ?: ip}"
        } else {
            "rl:unauth:$ip"
        }
        val limit = if (principal != null) authLimit else unauthLimit

        val exceeded = if (RedisCache.isEnabled) {
            // Redis path — atomic, cross-instance, survives restarts
            val count = RedisCache.incrementRateLimit(key, windowSeconds)
            count > limit
        } else {
            // In-memory fallback
            val now = System.currentTimeMillis()
            val cutoff = now - windowSeconds * 1000
            val timestamps = memoryLog.computeIfAbsent(key) { ConcurrentLinkedDeque() }
            while (timestamps.peekFirst()?.let { it < cutoff } == true) timestamps.pollFirst()
            if (timestamps.size >= limit) true
            else { timestamps.addLast(now); false }
        }

        if (exceeded) {
            logger.warn("Rate limit exceeded for key=$key (limit=$limit/min)")
            call.respond(
                HttpStatusCode.TooManyRequests,
                mapOf("success" to false, "error" to "Rate limit exceeded. Try again later.")
            )
        }
    }

    // Periodic cleanup of stale in-memory entries (only matters in fallback mode)
    environment?.monitor?.subscribe(ApplicationStarted) {
        val cleanupThread = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(300_000)
                    val cutoff = System.currentTimeMillis() - windowSeconds * 1000
                    memoryLog.entries.removeIf { (_, deque) ->
                        deque.removeIf { it < cutoff }
                        deque.isEmpty()
                    }
                } catch (_: InterruptedException) { break }
            }
        }, "rate-limiter-cleanup")
        cleanupThread.isDaemon = true
        cleanupThread.start()
    }
}
