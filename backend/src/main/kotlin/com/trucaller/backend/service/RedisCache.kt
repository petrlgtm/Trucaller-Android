package com.trucaller.backend.service

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

object RedisCache {

    private val logger = LoggerFactory.getLogger(RedisCache::class.java)

    private var client: RedisClient? = null
    private var connection: StatefulRedisConnection<String, String>? = null
    private var commands: RedisCommands<String, String>? = null

    private const val HIT_TTL = 300L        // 5 min for found caller-ID results
    private const val MISS_TTL = 60L        // 1 min for not-found results
    private const val PREFIX = "caller:"
    private const val REVOKE_PREFIX = "revoked_jti:"
    private const val LOGIN_ATTEMPTS_PREFIX = "login_attempts:"

    val isEnabled: Boolean get() = commands != null

    fun initialize(url: String?) {
        if (url.isNullOrBlank()) {
            logger.warn("REDIS_URL not set — cache disabled, all lookups hit MongoDB directly")
            return
        }
        try {
            client = RedisClient.create(url)
            connection = client!!.connect()
            commands = connection!!.sync()
            logger.info("Redis cache connected")
        } catch (e: Exception) {
            logger.error("Redis connection failed — cache disabled: ${e.message}")
        }
    }

    suspend fun get(phone: String): String? {
        val cmds = commands ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { cmds.get("$PREFIX$phone") }.getOrNull()
        }
    }

    suspend fun set(phone: String, json: String, notFound: Boolean = false) {
        val cmds = commands ?: return
        val ttl = if (notFound) MISS_TTL else HIT_TTL
        withContext(Dispatchers.IO) {
            runCatching { cmds.setex("$PREFIX$phone", ttl, json) }
        }
    }

    suspend fun invalidate(phone: String) {
        val cmds = commands ?: return
        withContext(Dispatchers.IO) {
            runCatching { cmds.del("$PREFIX$phone") }
        }
    }

    // ── Token revocation ─────────────────────────────────────────────────────

    /** Marks a JWT jti as revoked; TTL matches the token's remaining validity. */
    suspend fun revokeToken(jti: String, ttlSeconds: Long) {
        val cmds = commands ?: return
        withContext(Dispatchers.IO) {
            runCatching { cmds.setex("$REVOKE_PREFIX$jti", ttlSeconds, "1") }
        }
    }

    /** Returns true if this jti has been explicitly revoked. */
    fun isRevoked(jti: String): Boolean {
        val cmds = commands ?: return false
        return runCatching { cmds.exists("$REVOKE_PREFIX$jti") ?: 0L > 0L }.getOrDefault(false)
    }

    // ── Login brute-force protection ─────────────────────────────────────────

    private const val LOGIN_WINDOW_SECONDS = 900L  // 15 minutes
    private const val LOGIN_MAX_ATTEMPTS = 5

    /** Increments and returns the failed login counter for [phone]. */
    fun recordFailedLogin(phone: String): Long {
        val cmds = commands ?: return 0L
        val key = "$LOGIN_ATTEMPTS_PREFIX$phone"
        return runCatching {
            val count = cmds.incr(key) ?: 1L
            if (count == 1L) cmds.expire(key, LOGIN_WINDOW_SECONDS)
            count
        }.getOrDefault(0L)
    }

    /** Returns true if this phone has exceeded the allowed login attempts. */
    fun isLoginLocked(phone: String): Boolean {
        val cmds = commands ?: return false
        val key = "$LOGIN_ATTEMPTS_PREFIX$phone"
        return runCatching {
            (cmds.get(key)?.toLongOrNull() ?: 0L) >= LOGIN_MAX_ATTEMPTS
        }.getOrDefault(false)
    }

    /** Clears the failed login counter on successful authentication. */
    fun clearLoginAttempts(phone: String) {
        val cmds = commands ?: return
        runCatching { cmds.del("$LOGIN_ATTEMPTS_PREFIX$phone") }
    }

    // ── Rate limiter (Redis-backed sliding window) ───────────────────────────

    /**
     * Increments a sliding-window counter keyed by [key] within a [windowSeconds] window.
     * Returns the current count. Sets TTL on first increment.
     */
    fun incrementRateLimit(key: String, windowSeconds: Long): Long {
        val cmds = commands ?: return 0L
        return runCatching {
            val count = cmds.incr(key) ?: 1L
            if (count == 1L) cmds.expire(key, windowSeconds)
            count
        }.getOrDefault(0L)
    }

    fun close() {
        runCatching { connection?.close() }
        runCatching { client?.shutdown() }
        logger.info("Redis connection closed")
    }
}
