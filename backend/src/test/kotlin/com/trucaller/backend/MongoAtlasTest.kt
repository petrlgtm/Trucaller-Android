package com.trucaller.backend

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.bson.types.ObjectId
import java.io.File
import java.time.Instant
import kotlin.test.*

/**
 * Live integration test against MongoDB Atlas.
 *
 * Reads MONGO_URI from backend/.env, connects to the real cluster,
 * and validates contact upload (upsert) + retrieval against the
 * test_contacts collection (separate from prod contacts).
 *
 * Run with:  ./gradlew :backend:test --tests "*.MongoAtlasTest"
 */
class MongoAtlasTest {

    private lateinit var client: MongoClient
    private val testUserId = "test-user-atlas-probe"
    private val testCollection = "test_contacts"

    companion object {
        fun loadEnv(): Map<String, String> {
            val envFile = File("backend/.env").takeIf { it.exists() }
                ?: File(".env").takeIf { it.exists() }
                ?: return emptyMap()
            return envFile.readLines()
                .filter { it.contains("=") && !it.startsWith("#") }
                .associate { line ->
                    val idx = line.indexOf('=')
                    line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }
        }
    }

    @BeforeTest
    fun setup() {
        val env = loadEnv()
        val uri = env["MONGO_URI"] ?: System.getenv("MONGO_URI")
        // Skip all Atlas tests in CI environments where MONGO_URI is not configured.
        org.junit.Assume.assumeNotNull(uri)
        client = MongoClient.create(uri!!)
    }

    @AfterTest
    fun teardown() = runBlocking {
        // Clean up test documents
        client.getDatabase("trucaller")
            .getCollection<Document>(testCollection)
            .deleteMany(Filters.eq("userId", testUserId))
        client.close()
    }

    // ── 1. Ping ──────────────────────────────────────────────────────────

    @Test
    fun `can ping Atlas cluster`() = runBlocking {
        val result = client.getDatabase("admin")
            .runCommand(Document("ping", 1))
        val ok = (result["ok"] as? Number)?.toDouble() ?: 0.0
        assertEquals(1.0, ok, "Ping should return ok=1")
    }

    // ── 2. Contact upload (upsert) ───────────────────────────────────────

    @Test
    fun `upload contacts inserts and upserts correctly`() = runBlocking {
        val col = client.getDatabase("trucaller")
            .getCollection<Document>(testCollection)
        val now = Instant.now().toString()

        val contacts = listOf(
            Triple("Alice Nakato", "+256700111001", "alice@example.com"),
            Triple("Bob Okello",   "+256700111002", null),
            Triple("Carol Apio",   "+256700111003", "carol@example.com")
        )

        val ops = contacts.map { (name, phone, email) ->
            UpdateOneModel<Document>(
                Filters.and(
                    Filters.eq("userId", testUserId),
                    Filters.eq("phoneNumber", phone)
                ),
                Document("\$set", Document()
                    .append("name", name)
                    .append("phoneNumber", phone)
                    .append("email", email)
                    .append("userId", testUserId)
                    .append("syncedAt", now)
                    .append("isBackedUp", true)
                ).append("\$setOnInsert", Document("_id", ObjectId().toString())),
                UpdateOptions().upsert(true)
            )
        }

        val result = col.bulkWrite(ops)
        assertEquals(3, result.upserts.size, "Expected 3 new insertions")
        assertEquals(0, result.modifiedCount, "No documents should be modified on first run")

        // Upsert again — same phones, updated name
        val updatedOps = contacts.map { (_, phone, _) ->
            UpdateOneModel<Document>(
                Filters.and(
                    Filters.eq("userId", testUserId),
                    Filters.eq("phoneNumber", phone)
                ),
                Document("\$set", Document()
                    .append("name", "Updated Name")
                    .append("phoneNumber", phone)
                    .append("userId", testUserId)
                    .append("syncedAt", Instant.now().toString())
                ).append("\$setOnInsert", Document("_id", ObjectId().toString())),
                UpdateOptions().upsert(true)
            )
        }
        val result2 = col.bulkWrite(updatedOps)
        assertEquals(0, result2.upserts.size, "No new inserts — all already exist")
        assertEquals(3, result2.modifiedCount, "All 3 should be updated")
    }

    // ── 3. Retrieve contacts ─────────────────────────────────────────────

    @Test
    fun `retrieve contacts returns all uploaded documents`() = runBlocking {
        val col = client.getDatabase("trucaller")
            .getCollection<Document>(testCollection)
        val now = Instant.now().toString()

        // Insert 2 contacts
        val ops = listOf(
            "+256701000101" to "David Wamala",
            "+256701000102" to "Eve Namutebi"
        ).map { (phone, name) ->
            UpdateOneModel<Document>(
                Filters.and(Filters.eq("userId", testUserId), Filters.eq("phoneNumber", phone)),
                Document("\$set", Document()
                    .append("name", name).append("phoneNumber", phone)
                    .append("userId", testUserId).append("syncedAt", now)
                ).append("\$setOnInsert", Document("_id", ObjectId().toString())),
                UpdateOptions().upsert(true)
            )
        }
        col.bulkWrite(ops)

        // Retrieve
        val docs = col.find(Filters.eq("userId", testUserId)).toList()
        assertTrue(docs.size >= 2, "Expected at least 2 contacts, got ${docs.size}")
        docs.forEach { doc ->
            assertNotNull(doc.getString("name"), "name should not be null")
            assertNotNull(doc.getString("phoneNumber"), "phoneNumber should not be null")
            assertEquals(testUserId, doc.getString("userId"))
        }
    }

    // ── 4. Sync query (since timestamp) ─────────────────────────────────

    @Test
    fun `sync query returns only contacts updated after given timestamp`() = runBlocking {
        val col = client.getDatabase("trucaller")
            .getCollection<Document>(testCollection)

        val before = Instant.now().toString()
        Thread.sleep(50) // ensure timestamps differ
        val afterTs = Instant.now().toString()

        // Insert contact with timestamp AFTER cutoff
        col.insertOne(Document()
            .append("_id", ObjectId().toString())
            .append("userId", testUserId)
            .append("name", "Frank Ssebagala")
            .append("phoneNumber", "+256701000200")
            .append("syncedAt", afterTs)
        )

        // Insert contact with timestamp BEFORE cutoff
        col.insertOne(Document()
            .append("_id", ObjectId().toString())
            .append("userId", testUserId)
            .append("name", "Grace Nalwoga")
            .append("phoneNumber", "+256701000201")
            .append("syncedAt", before)
        )

        // Query contacts synced AFTER `before`
        val synced = col.find(
            Filters.and(
                Filters.eq("userId", testUserId),
                Filters.gt("syncedAt", before)
            )
        ).toList()

        assertTrue(synced.any { it.getString("phoneNumber") == "+256701000200" },
            "Frank (after cutoff) should appear in sync results")
        assertFalse(synced.any { it.getString("phoneNumber") == "+256701000201" },
            "Grace (before cutoff) should NOT appear in sync results")
    }

    // ── 5. Data integrity ────────────────────────────────────────────────

    @Test
    fun `contacts collection has compound index on userId and phoneNumber`() = runBlocking {
        val col = client.getDatabase("trucaller")
            .getCollection<Document>("contacts")

        val indexes = col.listIndexes().toList()
        val hasCompound = indexes.any { idx ->
            val key = idx.get("key", Document::class.java)
            key?.containsKey("userId") == true && key.containsKey("phoneNumber")
        }
        assertTrue(hasCompound,
            "contacts collection must have compound index (userId, phoneNumber)")
    }
}
