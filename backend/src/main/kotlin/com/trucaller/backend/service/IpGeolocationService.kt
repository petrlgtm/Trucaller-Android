package com.trucaller.backend.service

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves IP addresses to geographic locations via ip-api.com.
 *
 * Features:
 * - In-memory cache with 5-minute TTL to respect rate limits
 * - Automatic detection of private/local IPs (returns "Local Network")
 * - Non-blocking suspend function for use in Ktor routes
 */
object IpGeolocationService {

    private val log = LoggerFactory.getLogger(IpGeolocationService::class.java)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    // ── Cache ────────────────────────────────────────────────────────────

    private data class CacheEntry(val result: GeoResult, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val TTL_MS = 5 * 60 * 1000L // 5 minutes

    // ── Private IP detection ─────────────────────────────────────────────

    private val PRIVATE_IP_PATTERNS = listOf(
        Regex("^127\\..*"),
        Regex("^10\\..*"),
        Regex("^192\\.168\\..*"),
        Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\..*"),
        Regex("^0\\.0\\.0\\.0$"),
        Regex("^::1$"),
        Regex("^localhost$", RegexOption.IGNORE_CASE)
    )

    private fun isPrivateIp(ip: String): Boolean =
        PRIVATE_IP_PATTERNS.any { it.matches(ip) }

    private val LOCAL_NETWORK_RESULT = GeoResult(
        status = "success",
        country = "Local Network",
        city = "Local Network",
        lat = 0.0,
        lon = 0.0,
        isp = "Local Network",
        org = "Local Network",
        query = "local"
    )

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Resolves the given [ip] to a [GeoResult].
     *
     * Returns a "Local Network" result for private/reserved IPs.
     * Returns `null` if the external API call fails or the IP is invalid.
     */
    suspend fun resolve(ip: String): GeoResult? {
        val trimmedIp = ip.trim()

        // Skip private/local IPs
        if (isPrivateIp(trimmedIp)) {
            return LOCAL_NETWORK_RESULT.copy(query = trimmedIp)
        }

        // Check cache
        val now = System.currentTimeMillis()
        cache[trimmedIp]?.let { entry ->
            if (entry.expiresAt > now) {
                return entry.result
            } else {
                cache.remove(trimmedIp)
            }
        }

        // Call ip-api.com
        return try {
            val response: HttpResponse = httpClient.get(
                "http://ip-api.com/json/$trimmedIp?fields=status,country,city,lat,lon,isp,org,query"
            )
            val body = response.bodyAsText()
            val result = json.decodeFromString<GeoResult>(body)

            if (result.status == "success") {
                cache[trimmedIp] = CacheEntry(result, now + TTL_MS)
                result
            } else {
                log.warn("ip-api.com returned non-success for IP $trimmedIp: ${result.status}")
                null
            }
        } catch (e: Exception) {
            log.error("IP geolocation lookup failed for $trimmedIp: ${e.message}", e)
            null
        }
    }
}

/**
 * Data class matching the ip-api.com JSON response for the requested fields.
 */
@Serializable
data class GeoResult(
    val status: String = "",
    val country: String = "",
    val city: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val isp: String = "",
    val org: String = "",
    val query: String = ""
)
