package com.trucaller.backend.data

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.slf4j.LoggerFactory

/**
 * Singleton object that manages the MongoDB Atlas connection.
 *
 * Reads the connection URI from:
 *   1. Environment variable MONGO_URI, or
 *   2. The `mongo.uri` property in application.conf
 *
 * Database name: "trucaller"
 */
object MongoDB {

    private val logger = LoggerFactory.getLogger(MongoDB::class.java)

    private const val DATABASE_NAME = "trucaller"

    private lateinit var client: MongoClient
    lateinit var database: MongoDatabase
        private set

    /**
     * Initialises the MongoDB client and database connection, then ensures
     * all required indexes exist on the collections.
     *
     * Should be called once during application startup.
     *
     * @param uri Optional connection URI override. When `null`, the value is
     *            resolved from the `MONGO_URI` environment variable.
     */
    suspend fun connect(uri: String? = null) {
        val connectionUri = uri
            ?: System.getenv("MONGO_URI")
            ?: error("MongoDB connection URI not found. Set MONGO_URI environment variable or pass it explicitly.")

        client = MongoClient.create(connectionUri)
        database = client.getDatabase(DATABASE_NAME)

        logger.info("Connected to MongoDB Atlas")

        Collections.ensureIndexes(database)
        logger.info("Indexes ensured")
    }

    /**
     * Closes the underlying MongoClient. Call during graceful shutdown.
     */
    fun close() {
        if (::client.isInitialized) {
            client.close()
            logger.info("MongoDB connection closed")
        }
    }
}
