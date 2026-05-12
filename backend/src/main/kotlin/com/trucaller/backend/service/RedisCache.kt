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

    private const val HIT_TTL = 300L   // 5 min for found results
    private const val MISS_TTL = 60L   // 1 min for not-found results
    private const val PREFIX = "caller:"

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

    fun close() {
        runCatching { connection?.close() }
        runCatching { client?.shutdown() }
        logger.info("Redis connection closed")
    }
}
