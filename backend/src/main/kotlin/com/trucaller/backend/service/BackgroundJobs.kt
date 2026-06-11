package com.trucaller.backend.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.trucaller.backend.data.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

object BackgroundJobs {

    private val logger = LoggerFactory.getLogger(BackgroundJobs::class.java)

    private const val HOUR_MS = 60 * 60 * 1000L
    private const val DAY_MS = 24 * HOUR_MS
    private const val WEEK_MS = 7 * DAY_MS

    fun start(scope: CoroutineScope) {
        SearchLogger.start(scope)
        scope.launch { hourlyLoop() }
        scope.launch { dailyLoop() }
        scope.launch { weeklyLoop() }
        logger.info("Background jobs started")
    }

    // ── Loops ───────────────────────────────────────────────────────────

    private suspend fun hourlyLoop() {
        while (true) {
            delay(HOUR_MS)
            run("hourly:refreshBestNames") { refreshRecentBestNames() }
            run("hourly:expirePendingAlarms") { expireStalePendingAlarms() }
        }
    }

    private suspend fun dailyLoop() {
        delay(10 * 60 * 1000L) // offset 10 min from startup
        while (true) {
            run("daily:cleanAliases") { cleanJunkAliases() }
            run("daily:rebuildCategories") { rebuildSpamCategories() }
            delay(DAY_MS)
        }
    }

    private suspend fun weeklyLoop() {
        delay(30 * 60 * 1000L) // offset 30 min from startup
        while (true) {
            run("weekly:decayScores") { decayOldSpamScores() }
            delay(WEEK_MS)
        }
    }

    private suspend fun run(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Job $name failed: ${e.message}", e)
        }
    }

    // ── Hourly ──────────────────────────────────────────────────────────

    /** Refresh bestName for any alias updated in the last 2 hours. */
    private suspend fun refreshRecentBestNames() {
        val cutoff = Instant.now().minus(2, ChronoUnit.HOURS).toString()
        val phones = mutableSetOf<String>()
        Collections.aliases
            .find(Filters.gt("updatedAt", cutoff))
            .batchSize(500)
            .collect { doc -> doc.getString("phone")?.let { phones.add(it) } }
        phones.forEach { phone ->
            runCatching { CallerIdService.refreshBestName(phone) }
        }
        if (phones.isNotEmpty()) logger.info("Hourly: refreshed bestName for ${phones.size} numbers")
    }

    /**
     * Mark alarm commands still PENDING after 24h as FAILED — the device
     * never executed them (offline, uninstalled, or never picked them up).
     */
    private suspend fun expireStalePendingAlarms() {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS).toString()
        val result = Collections.alarmLogs.updateMany(
            Filters.and(
                Filters.eq("result", "PENDING"),
                Filters.lt("triggeredAt", cutoff)
            ),
            Updates.combine(
                Updates.set("result", "FAILED"),
                Updates.set("resultNotes", "Expired: device did not execute the command within 24h"),
                Updates.set("completedAt", Instant.now().toString())
            )
        )
        if (result.modifiedCount > 0) {
            logger.info("Hourly: expired ${result.modifiedCount} stale pending alarm(s)")
        }
    }

    // ── Daily ────────────────────────────────────────────────────────────

    /** Delete aliases with blank/missing labels or zero weight. */
    private suspend fun cleanJunkAliases() {
        val result = Collections.aliases.deleteMany(
            Filters.or(
                Filters.lte("weight", 0),
                Filters.eq("label", ""),
                Filters.exists("label", false)
            )
        )
        logger.info("Daily: removed ${result.deletedCount} junk aliases")
    }

    /** Recompute the category field from the current spamScore for every callerIds doc. */
    private suspend fun rebuildSpamCategories() {
        var updated = 0
        Collections.callerIds.find().batchSize(500).collect { doc ->
            val score = doc.getInteger("spamScore", 0)
            val expected = categoryFromScore(score)
            if (doc.getString("category") != expected) {
                Collections.callerIds.updateOne(
                    Filters.eq("_id", doc["_id"]),
                    Updates.set("category", expected)
                )
                updated++
            }
        }
        logger.info("Daily: re-categorised $updated caller ID entries")
    }

    // ── Weekly ───────────────────────────────────────────────────────────

    /**
     * Decay spam scores for numbers with no activity in 90+ days (−5 per pass, floor 0).
     * Also invalidates the Redis cache for each decayed number.
     */
    private suspend fun decayOldSpamScores() {
        val cutoff = Instant.now().minus(90, ChronoUnit.DAYS).toString()
        val now = Instant.now().toString()
        var decayed = 0
        Collections.callerIds
            .find(Filters.and(
                Filters.gt("spamScore", 0),
                Filters.lt("lastUpdated", cutoff)
            ))
            .batchSize(500)
            .collect { doc ->
                val newScore = maxOf(doc.getInteger("spamScore", 0) - 5, 0)
                Collections.callerIds.updateOne(
                    Filters.eq("_id", doc["_id"]),
                    Updates.combine(
                        Updates.set("spamScore", newScore),
                        Updates.set("category", categoryFromScore(newScore)),
                        Updates.set("lastUpdated", now)
                    )
                )
                doc.getString("phoneNumber")?.let { RedisCache.invalidate(it) }
                decayed++
            }
        logger.info("Weekly: decayed spam scores for $decayed numbers")
    }

    private fun categoryFromScore(score: Int) = when {
        score <= 20 -> "SAFE"
        score <= 60 -> "SUSPECTED_SPAM"
        score <= 80 -> "SPAM"
        else -> "FRAUD"
    }
}
