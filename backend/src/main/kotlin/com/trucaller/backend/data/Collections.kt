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
    }
}
