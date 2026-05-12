package com.trucaller.backend.data

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.bson.Document

/**
 * Provides typed references to every MongoDB collection used by the
 * Trucaller backend, and ensures the required indexes exist.
 */
object Collections {

    // ── Collection references ────────────────────────────────────────────

    val users: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("users")

    val callerIds: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("callerIds")

    val contacts: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("contacts")

    val devices: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("devices")

    val ipLogs: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("ipLogs")

    val stolenReports: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("stolenReports")

    val alarmLogs: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("alarmLogs")

    val blockedNumbers: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("blockedNumbers")

    val adminUsers: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("adminUsers")

    val otpCodes: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("otpCodes")

    val smsSpamReports: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("smsSpamReports")

    val otpRateLimits: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("otpRateLimits")

    val geofences: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("geofences")

    val geofenceEvents: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("geofenceEvents")

    val spamVerifications: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("spamVerifications")

    val familyGroups: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("familyGroups")

    val familyMembers: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("familyMembers")

    val aliases: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("aliases")

    val searches: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("searches")

    val refreshTokens: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("refreshTokens")

    val loginAuditLog: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("loginAuditLog")

    val spamReports: MongoCollection<Document>
        get() = MongoDB.database.getCollection<Document>("spamReports")

    // ── Index creation ───────────────────────────────────────────────────

    /**
     * Creates all required indexes if they do not already exist.
     *
     * - `users.phoneNumber`      – unique
     * - `callerIds.phoneNumber`  – unique
     * - `contacts.(userId, phoneNumber)` – compound
     * - `devices.userId`         – ascending
     * - `otpCodes.expiresAt`     – TTL (auto-delete expired OTPs)
     */
    suspend fun ensureIndexes(database: MongoDatabase) {
        val uniqueOption = IndexOptions().unique(true)

        // Unique index on phoneNumber for users
        database.getCollection<Document>("users")
            .createIndex(Indexes.ascending("phoneNumber"), uniqueOption)

        // Unique index on phoneNumber for callerIds
        database.getCollection<Document>("callerIds")
            .createIndex(Indexes.ascending("phoneNumber"), uniqueOption)

        // Compound index (userId, phoneNumber) for contacts
        database.getCollection<Document>("contacts")
            .createIndex(Indexes.compoundIndex(
                Indexes.ascending("userId"),
                Indexes.ascending("phoneNumber")
            ))

        // Index on userId for devices
        database.getCollection<Document>("devices")
            .createIndex(Indexes.ascending("userId"))

        // TTL index: auto-delete OTP documents 10 minutes after expiresAt
        database.getCollection<Document>("otpCodes")
            .createIndex(
                Indexes.ascending("expiresAt"),
                IndexOptions().expireAfter(0, java.util.concurrent.TimeUnit.SECONDS)
            )

        // Compound index on (phoneNumber, purpose) for OTP lookups
        database.getCollection<Document>("otpCodes")
            .createIndex(Indexes.compoundIndex(
                Indexes.ascending("phoneNumber"),
                Indexes.ascending("purpose")
            ))

        // Compound index on (userId, phoneNumber) for blocked numbers
        database.getCollection<Document>("blockedNumbers")
            .createIndex(Indexes.compoundIndex(
                Indexes.ascending("userId"),
                Indexes.ascending("phoneNumber")
            ))

        // TTL index: auto-delete OTP rate-limit entries after 1 hour
        database.getCollection<Document>("otpRateLimits")
            .createIndex(
                Indexes.ascending("requestedAt"),
                IndexOptions().expireAfter(3600, java.util.concurrent.TimeUnit.SECONDS)
            )

        // Index on phoneNumber for OTP rate-limit lookups
        database.getCollection<Document>("otpRateLimits")
            .createIndex(Indexes.ascending("phoneNumber"))

        // Index on senderNumber for SMS spam reports
        database.getCollection<Document>("smsSpamReports")
            .createIndex(Indexes.ascending("senderNumber"))

        // Index on userId for SMS spam reports
        database.getCollection<Document>("smsSpamReports")
            .createIndex(Indexes.ascending("userId"))

        // Index on deviceId for geofences
        database.getCollection<Document>("geofences")
            .createIndex(Indexes.ascending("deviceId"))

        // Index on deviceId for geofence events
        database.getCollection<Document>("geofenceEvents")
            .createIndex(Indexes.ascending("deviceId"))

        // Index on geofenceId for geofence events
        database.getCollection<Document>("geofenceEvents")
            .createIndex(Indexes.ascending("geofenceId"))

        // Compound index on (phoneNumber, userId) for spam verifications (one vote per user per number)
        database.getCollection<Document>("spamVerifications")
            .createIndex(Indexes.compoundIndex(
                Indexes.ascending("phoneNumber"),
                Indexes.ascending("userId")
            ))

        // Index on phoneNumber for spam verification aggregation
        database.getCollection<Document>("spamVerifications")
            .createIndex(Indexes.ascending("phoneNumber"))

        // Unique index on inviteCode for family groups
        database.getCollection<Document>("familyGroups")
            .createIndex(Indexes.ascending("inviteCode"), uniqueOption)

        // Index on ownerId for family groups
        database.getCollection<Document>("familyGroups")
            .createIndex(Indexes.ascending("ownerId"))

        // Compound index on (groupId, userId) for family members
        database.getCollection<Document>("familyMembers")
            .createIndex(Indexes.compoundIndex(
                Indexes.ascending("groupId"),
                Indexes.ascending("userId")
            ))

        // Index on userId for family members (lookup groups by user)
        database.getCollection<Document>("familyMembers")
            .createIndex(Indexes.ascending("userId"))

        // Compound unique index on (phone, label) for alias deduplication
        database.getCollection<Document>("aliases")
            .createIndex(
                Indexes.compoundIndex(Indexes.ascending("phone"), Indexes.ascending("label")),
                IndexOptions().unique(true)
            )

        // Index on phone for alias lookups / ranking
        database.getCollection<Document>("aliases")
            .createIndex(Indexes.ascending("phone"))

        // Index on searchedBy for per-user analytics
        database.getCollection<Document>("searches")
            .createIndex(Indexes.ascending("searchedBy"))

        // TTL index: auto-delete search logs 90 days after creation
        database.getCollection<Document>("searches")
            .createIndex(
                Indexes.ascending("createdAt"),
                IndexOptions().expireAfter(90L * 24 * 3600, java.util.concurrent.TimeUnit.SECONDS)
            )

        // ── Task 3.3: Missing indexes ───────────────────────────────────────

        // Compound index for weekly spam-decay job: callerIds where spamScore > 0 and lastUpdated < cutoff
        database.getCollection<Document>("callerIds")
            .createIndex(Indexes.compoundIndex(
                Indexes.descending("spamScore"),
                Indexes.ascending("lastUpdated")
            ))

        // Index on aliases.updatedAt for hourly bestName refresh job
        database.getCollection<Document>("aliases")
            .createIndex(Indexes.ascending("updatedAt"))

        // Compound index on contacts.(userId, phoneNumber) for privacy-scoped lookups
        database.getCollection<Document>("contacts")
            .createIndex(Indexes.compoundIndex(
                Indexes.ascending("userId"),
                Indexes.ascending("phoneNumber")
            ))

        // ── Task 2.1: Refresh tokens ────────────────────────────────────────

        // Unique index on refreshTokens.token for O(1) lookup
        database.getCollection<Document>("refreshTokens")
            .createIndex(Indexes.ascending("token"), IndexOptions().unique(true))

        // Index on refreshTokens.userId to revoke all sessions for a user
        database.getCollection<Document>("refreshTokens")
            .createIndex(Indexes.ascending("userId"))

        // TTL index: auto-delete expired refresh tokens (30 days)
        database.getCollection<Document>("refreshTokens")
            .createIndex(
                Indexes.ascending("expiresAt"),
                IndexOptions().expireAfter(0, java.util.concurrent.TimeUnit.SECONDS)
            )

        // ── Task 5.2: Login audit log ───────────────────────────────────────

        // Index on loginAuditLog.phone for per-user audit queries
        database.getCollection<Document>("loginAuditLog")
            .createIndex(Indexes.ascending("phone"))

        // TTL index: auto-delete login audit entries after 90 days
        database.getCollection<Document>("loginAuditLog")
            .createIndex(
                Indexes.ascending("timestamp"),
                IndexOptions().expireAfter(90L * 24 * 3600, java.util.concurrent.TimeUnit.SECONDS)
            )

        // ── Task 3.4: Spam report deduplication ────────────────────────────

        // Unique compound index preventing a user from reporting the same number twice
        database.getCollection<Document>("spamReports")
            .createIndex(
                Indexes.compoundIndex(Indexes.ascending("userId"), Indexes.ascending("phoneNumber")),
                IndexOptions().unique(true)
            )
    }
}
