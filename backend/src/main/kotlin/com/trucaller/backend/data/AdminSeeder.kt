package com.trucaller.backend.data

import at.favre.lib.crypto.bcrypt.BCrypt
import com.mongodb.client.model.Filters
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.Document
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Seeds the two authorised admin accounts if they don't yet exist in MongoDB.
 *
 * Authorised phones: +256787959715 (primary) and +256751159472 (secondary).
 * Default password for a freshly seeded account: Trucaller@Admin2024!
 * Admins should change their password on first login via Settings.
 */
object AdminSeeder {

    private val logger = LoggerFactory.getLogger(AdminSeeder::class.java)

    private data class AdminSeed(
        val phoneNumber: String,
        val name: String,
        val role: String = "SUPER_ADMIN"
    )

    private val DEFAULT_PASSWORD = "Trucaller@Admin2024!"

    private val ADMIN_SEEDS = listOf(
        AdminSeed(phoneNumber = "+256787959715", name = "Admin Byron"),
        AdminSeed(phoneNumber = "+256751159472", name = "Admin Two")
    )

    suspend fun seed() {
        val now = Instant.now().toString()

        for (admin in ADMIN_SEEDS) {
            val existing = Collections.adminUsers
                .find(Filters.eq("phoneNumber", admin.phoneNumber))
                .firstOrNull()

            if (existing == null) {
                val hash = BCrypt.withDefaults()
                    .hashToString(12, DEFAULT_PASSWORD.toCharArray())

                val doc = Document()
                    .append("_id", ObjectId().toString())
                    .append("phoneNumber", admin.phoneNumber)
                    .append("name", admin.name)
                    .append("email", "")
                    .append("passwordHash", hash)
                    .append("role", admin.role)
                    .append("createdAt", now)

                Collections.adminUsers.insertOne(doc)
                logger.info("Seeded admin account for ${admin.phoneNumber} (${admin.name})")
            } else {
                logger.info("Admin account for ${admin.phoneNumber} already exists — skipping seed")
            }
        }

        // Remove any device records that were accidentally created under admin
        // user IDs. This happens when an admin JWT is active while the device
        // heartbeat fires: the backend reads userId from the JWT instead of the
        // request body, registering the device under the admin's userId.
        val adminIds = Collections.adminUsers
            .find()
            .toList()
            .mapNotNull { it.getString("_id") }
        if (adminIds.isNotEmpty()) {
            val deleted = Collections.devices
                .deleteMany(Filters.`in`("userId", adminIds))
                .deletedCount
            if (deleted > 0) {
                logger.info("Cleaned up $deleted device record(s) incorrectly registered under admin user IDs")
            }
        }
    }
}
