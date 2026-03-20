package com.byron.trucaller.service

import com.byron.trucaller.data.repository.LookupResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory cache for caller-ID lookup results.
 *
 * The call screening service stores its [LookupResult] here so the overlay
 * service can reuse it instead of performing a redundant lookup. Entries
 * automatically expire after [EXPIRY_MS] milliseconds (30 seconds).
 */
object CallerIdCache {

    private const val EXPIRY_MS = 30_000L

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    data class CacheEntry(
        val result: LookupResult,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Store a lookup result for the given phone number.
     */
    fun put(number: String, result: LookupResult) {
        cache[number] = CacheEntry(result)
    }

    /**
     * Retrieve a cached lookup result. Returns `null` if the entry does not
     * exist or has expired (older than 30 seconds).
     */
    fun get(number: String): LookupResult? {
        val entry = cache[number] ?: return null
        val age = System.currentTimeMillis() - entry.timestamp
        if (age > EXPIRY_MS) {
            cache.remove(number)
            return null
        }
        return entry.result
    }

    /**
     * Remove all cached entries.
     */
    fun clear() {
        cache.clear()
    }
}
