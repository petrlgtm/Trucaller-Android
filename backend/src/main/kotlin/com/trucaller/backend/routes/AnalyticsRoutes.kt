package com.trucaller.backend.routes

import com.mongodb.client.model.Filters
import com.trucaller.backend.auth.userId
import com.trucaller.backend.data.Collections
import com.trucaller.backend.data.models.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// ── Response DTOs ────────────────────────────────────────────────────────────

@Serializable
data class WeeklyTrend(
    val blockedThisWeek: Long,
    val blockedLastWeek: Long,
    val reportedThisWeek: Long,
    val reportedLastWeek: Long
)

@Serializable
data class UserAnalyticsResponse(
    val callsBlocked: Long,
    val spamReported: Long,
    val smsSpamDetected: Long,
    val devicesProtected: Long,
    val stolenReportsCount: Long,
    val alarmsTriggered: Long,
    val trustLevel: String,
    val trustScore: Int,
    val memberSince: String,
    val lastActivity: String,
    val weeklyTrend: WeeklyTrend
)

@Serializable
data class MonthlyAnalytics(
    val month: String,
    val callsBlocked: Long,
    val spamReported: Long,
    val smsSpamDetected: Long
)

// ── Routes ───────────────────────────────────────────────────────────────────

/**
 * User analytics aggregation routes:
 *
 * - `GET /api/analytics/me`         — aggregated stats for the authenticated user
 * - `GET /api/analytics/me/history` — monthly breakdown for the last 6 months
 */
fun Route.analyticsRoutes() {

    route("/api/analytics") {

        authenticate("auth-jwt") {

            // ── GET /api/analytics/me ────────────────────────────────────
            get("/me") {
                val userId = call.userId()

                // Fetch user document for trust info and memberSince
                val userDoc = Collections.users
                    .find(Filters.eq("_id", userId))
                    .firstOrNull()

                if (userDoc == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(success = false, error = "User not found.")
                    )
                    return@get
                }

                val trustScore = userDoc.getInteger("trustScore", 0)
                val trustLevel = userDoc.getString("trustLevel") ?: trustLevelFromScore(trustScore)
                val memberSince = userDoc.getString("createdAt") ?: ""

                // Total counts across all time
                val callsBlocked = Collections.blockedNumbers
                    .countDocuments(Filters.eq("userId", userId))

                val spamReported = Collections.spamVerifications
                    .countDocuments(Filters.eq("userId", userId))

                val smsSpamDetected = Collections.smsSpamReports
                    .countDocuments(Filters.eq("userId", userId))

                val devicesProtected = Collections.devices
                    .countDocuments(Filters.eq("userId", userId))

                val stolenReportsCount = Collections.stolenReports
                    .countDocuments(Filters.eq("reportedBy", userId))

                val alarmsTriggered = Collections.alarmLogs
                    .countDocuments(Filters.eq("triggeredBy", userId))

                // Determine lastActivity from the most recent timestamp across collections
                val lastActivity = resolveLastActivity(userId, memberSince)

                // Weekly trend calculations
                val now = Instant.now()
                val startOfThisWeek = now.minus(7, ChronoUnit.DAYS).toString()
                val startOfLastWeek = now.minus(14, ChronoUnit.DAYS).toString()

                val blockedThisWeek = Collections.blockedNumbers
                    .countDocuments(
                        Filters.and(
                            Filters.eq("userId", userId),
                            Filters.gte("blockedAt", startOfThisWeek)
                        )
                    )

                val blockedLastWeek = Collections.blockedNumbers
                    .countDocuments(
                        Filters.and(
                            Filters.eq("userId", userId),
                            Filters.gte("blockedAt", startOfLastWeek),
                            Filters.lt("blockedAt", startOfThisWeek)
                        )
                    )

                val reportedThisWeek = Collections.spamVerifications
                    .countDocuments(
                        Filters.and(
                            Filters.eq("userId", userId),
                            Filters.gte("createdAt", startOfThisWeek)
                        )
                    )

                val reportedLastWeek = Collections.spamVerifications
                    .countDocuments(
                        Filters.and(
                            Filters.eq("userId", userId),
                            Filters.gte("createdAt", startOfLastWeek),
                            Filters.lt("createdAt", startOfThisWeek)
                        )
                    )

                val analytics = UserAnalyticsResponse(
                    callsBlocked = callsBlocked,
                    spamReported = spamReported,
                    smsSpamDetected = smsSpamDetected,
                    devicesProtected = devicesProtected,
                    stolenReportsCount = stolenReportsCount,
                    alarmsTriggered = alarmsTriggered,
                    trustLevel = trustLevel,
                    trustScore = trustScore,
                    memberSince = memberSince,
                    lastActivity = lastActivity,
                    weeklyTrend = WeeklyTrend(
                        blockedThisWeek = blockedThisWeek,
                        blockedLastWeek = blockedLastWeek,
                        reportedThisWeek = reportedThisWeek,
                        reportedLastWeek = reportedLastWeek
                    )
                )

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(success = true, data = analytics)
                )
            }

            // ── GET /api/analytics/me/history ────────────────────────────
            get("/me/history") {
                val userId = call.userId()

                val now = Instant.now()
                val history = mutableListOf<MonthlyAnalytics>()

                // Generate monthly buckets for the last 6 months
                for (i in 5 downTo 0) {
                    val monthStart = now
                        .atZone(ZoneOffset.UTC)
                        .minusMonths(i.toLong())
                        .withDayOfMonth(1)
                        .toLocalDate()
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)

                    val monthEnd = monthStart
                        .atZone(ZoneOffset.UTC)
                        .plusMonths(1)
                        .toInstant()

                    val monthLabel = monthStart
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .let { "${it.year}-${it.monthValue.toString().padStart(2, '0')}" }

                    val monthStartStr = monthStart.toString()
                    val monthEndStr = monthEnd.toString()

                    val blocked = Collections.blockedNumbers
                        .countDocuments(
                            Filters.and(
                                Filters.eq("userId", userId),
                                Filters.gte("blockedAt", monthStartStr),
                                Filters.lt("blockedAt", monthEndStr)
                            )
                        )

                    val spam = Collections.spamVerifications
                        .countDocuments(
                            Filters.and(
                                Filters.eq("userId", userId),
                                Filters.gte("createdAt", monthStartStr),
                                Filters.lt("createdAt", monthEndStr)
                            )
                        )

                    val sms = Collections.smsSpamReports
                        .countDocuments(
                            Filters.and(
                                Filters.eq("userId", userId),
                                Filters.gte("reportedAt", monthStartStr),
                                Filters.lt("reportedAt", monthEndStr)
                            )
                        )

                    history.add(
                        MonthlyAnalytics(
                            month = monthLabel,
                            callsBlocked = blocked,
                            spamReported = spam,
                            smsSpamDetected = sms
                        )
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(success = true, data = history)
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Derives a trust level label from the numeric trust score.
 */
private fun trustLevelFromScore(score: Int): String = when {
    score >= 100 -> "AUTHORITY"
    score >= 80 -> "VERIFIED"
    score >= 50 -> "TRUSTED"
    score >= 20 -> "BASIC"
    else -> "NEW"
}

/**
 * Determines the user's most recent activity timestamp by checking
 * the latest entry across key collections. Falls back to [fallback]
 * (typically user createdAt) if no activity is found.
 */
private suspend fun resolveLastActivity(userId: String, fallback: String): String {
    val candidates = mutableListOf<String>()
    candidates.add(fallback)

    // Most recent blocked number
    Collections.blockedNumbers
        .find(Filters.eq("userId", userId))
        .sort(org.bson.Document("blockedAt", -1))
        .limit(1)
        .firstOrNull()
        ?.getString("blockedAt")
        ?.let { candidates.add(it) }

    // Most recent spam verification
    Collections.spamVerifications
        .find(Filters.eq("userId", userId))
        .sort(org.bson.Document("createdAt", -1))
        .limit(1)
        .firstOrNull()
        ?.getString("createdAt")
        ?.let { candidates.add(it) }

    // Most recent SMS spam report
    Collections.smsSpamReports
        .find(Filters.eq("userId", userId))
        .sort(org.bson.Document("reportedAt", -1))
        .limit(1)
        .firstOrNull()
        ?.getString("reportedAt")
        ?.let { candidates.add(it) }

    // Most recent alarm trigger
    Collections.alarmLogs
        .find(Filters.eq("triggeredBy", userId))
        .sort(org.bson.Document("triggeredAt", -1))
        .limit(1)
        .firstOrNull()
        ?.getString("triggeredAt")
        ?.let { candidates.add(it) }

    // Most recent stolen report
    Collections.stolenReports
        .find(Filters.eq("reportedBy", userId))
        .sort(org.bson.Document("reportedAt", -1))
        .limit(1)
        .firstOrNull()
        ?.getString("reportedAt")
        ?.let { candidates.add(it) }

    return candidates.filter { it.isNotBlank() }.maxOrNull() ?: fallback
}
