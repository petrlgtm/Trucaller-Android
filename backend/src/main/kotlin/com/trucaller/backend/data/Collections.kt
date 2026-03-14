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

    // ── Index creation ───────────────────────────────────────────────────

    /**
     * Creates all required indexes if they do not already exist.
     *
     * - `users.phoneNumber`      – unique
     * - `callerIds.phoneNumber`  – unique
     * - `contacts.(userId, phoneNumber)` – compound
     * - `devices.userId`         – ascending
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
    }
}
