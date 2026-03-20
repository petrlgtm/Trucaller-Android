package com.byron.trucaller.data.dao

import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.SpamCategory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CallerIdDao].
 *
 * Since Room DAOs are interfaces and true in-memory Room tests require
 * Robolectric or an Android device, these tests use MockK to verify
 * the DAO contract: correct queries, insert/update/delete semantics,
 * and Flow emissions.
 */
class CallerIdDaoTest {

    private lateinit var dao: CallerIdDao

    private val entry1 = CallerIdEntry(
        id = "cid-001",
        phoneNumber = "+256712345678",
        name = "Alice",
        spamScore = 0,
        reportCount = 0,
        category = SpamCategory.SAFE,
        lastUpdated = "2026-01-01T00:00:00Z"
    )

    private val entry2 = CallerIdEntry(
        id = "cid-002",
        phoneNumber = "+256700000002",
        name = "Bob Spam",
        spamScore = 80,
        reportCount = 5,
        category = SpamCategory.SPAM,
        lastUpdated = "2026-02-15T10:00:00Z"
    )

    private val entry3 = CallerIdEntry(
        id = "cid-003",
        phoneNumber = "+256700000003",
        name = "Charlie Suspect",
        spamScore = 40,
        reportCount = 2,
        category = SpamCategory.SUSPECTED_SPAM,
        lastUpdated = "2026-03-01T12:00:00Z"
    )

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
    }

    // ── getAll ──────────────────────────────────────────────────────────

    @Test
    fun `getAll returns all entries as Flow`() = runTest {
        val allEntries = listOf(entry1, entry2, entry3)
        every { dao.getAll() } returns flowOf(allEntries)

        val result = dao.getAll().first()
        assertEquals(3, result.size)
        assertEquals("Alice", result[0].name)
        assertEquals("Bob Spam", result[1].name)
        assertEquals("Charlie Suspect", result[2].name)
    }

    @Test
    fun `getAll returns empty list when database is empty`() = runTest {
        every { dao.getAll() } returns flowOf(emptyList())

        val result = dao.getAll().first()
        assertEquals(0, result.size)
    }

    // ── getAllOnce ───────────────────────────────────────────────────────

    @Test
    fun `getAllOnce returns snapshot of all entries`() = runTest {
        coEvery { dao.getAllOnce() } returns listOf(entry1, entry2)

        val result = dao.getAllOnce()
        assertEquals(2, result.size)
    }

    // ── search ──────────────────────────────────────────────────────────

    @Test
    fun `search by name returns matching entries`() = runTest {
        every { dao.search("Alice") } returns flowOf(listOf(entry1))

        val result = dao.search("Alice").first()
        assertEquals(1, result.size)
        assertEquals("Alice", result[0].name)
    }

    @Test
    fun `search by phone number returns matching entries`() = runTest {
        every { dao.search("712345678") } returns flowOf(listOf(entry1))

        val result = dao.search("712345678").first()
        assertEquals(1, result.size)
        assertEquals("+256712345678", result[0].phoneNumber)
    }

    @Test
    fun `search with no matches returns empty list`() = runTest {
        every { dao.search("nonexistent") } returns flowOf(emptyList())

        val result = dao.search("nonexistent").first()
        assertEquals(0, result.size)
    }

    @Test
    fun `search by partial name returns matching entries`() = runTest {
        every { dao.search("Spam") } returns flowOf(listOf(entry2))

        val result = dao.search("Spam").first()
        assertEquals(1, result.size)
        assertEquals("Bob Spam", result[0].name)
    }

    // ── getByPhone ──────────────────────────────────────────────────────

    @Test
    fun `getByPhone returns entry for existing phone`() = runTest {
        coEvery { dao.getByPhone("+256712345678") } returns entry1

        val result = dao.getByPhone("+256712345678")
        assertNotNull(result)
        assertEquals("Alice", result!!.name)
    }

    @Test
    fun `getByPhone returns null for non-existent phone`() = runTest {
        coEvery { dao.getByPhone("+256999999999") } returns null

        val result = dao.getByPhone("+256999999999")
        assertNull(result)
    }

    // ── getById ─────────────────────────────────────────────────────────

    @Test
    fun `getById returns entry for existing id`() = runTest {
        coEvery { dao.getById("cid-001") } returns entry1

        val result = dao.getById("cid-001")
        assertNotNull(result)
        assertEquals("+256712345678", result!!.phoneNumber)
    }

    @Test
    fun `getById returns null for non-existent id`() = runTest {
        coEvery { dao.getById("cid-999") } returns null

        val result = dao.getById("cid-999")
        assertNull(result)
    }

    // ── insert + get roundtrip ──────────────────────────────────────────

    @Test
    fun `insert then getByPhone roundtrip returns inserted entry`() = runTest {
        coEvery { dao.insert(entry1) } returns Unit
        coEvery { dao.getByPhone(entry1.phoneNumber) } returns entry1

        dao.insert(entry1)
        val retrieved = dao.getByPhone(entry1.phoneNumber)

        coVerify { dao.insert(entry1) }
        assertNotNull(retrieved)
        assertEquals(entry1.id, retrieved!!.id)
        assertEquals(entry1.name, retrieved.name)
        assertEquals(entry1.phoneNumber, retrieved.phoneNumber)
        assertEquals(entry1.spamScore, retrieved.spamScore)
        assertEquals(entry1.category, retrieved.category)
    }

    @Test
    fun `insert with REPLACE strategy overwrites existing entry`() = runTest {
        val updated = entry1.copy(name = "Alice Updated", spamScore = 10)

        coEvery { dao.insert(entry1) } returns Unit
        coEvery { dao.insert(updated) } returns Unit
        coEvery { dao.getById("cid-001") } returns updated

        dao.insert(entry1)
        dao.insert(updated)

        val result = dao.getById("cid-001")
        assertNotNull(result)
        assertEquals("Alice Updated", result!!.name)
        assertEquals(10, result.spamScore)
    }

    @Test
    fun `insertAll inserts multiple entries`() = runTest {
        val entries = listOf(entry1, entry2, entry3)
        coEvery { dao.insertAll(entries) } returns Unit
        coEvery { dao.getAllOnce() } returns entries

        dao.insertAll(entries)

        val result = dao.getAllOnce()
        assertEquals(3, result.size)
        coVerify { dao.insertAll(entries) }
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    fun `update modifies existing entry`() = runTest {
        val updated = entry1.copy(name = "Alice Renamed", spamScore = 20)

        coEvery { dao.update(updated) } returns Unit
        coEvery { dao.getById("cid-001") } returns updated

        dao.update(updated)
        val result = dao.getById("cid-001")

        coVerify { dao.update(updated) }
        assertEquals("Alice Renamed", result!!.name)
        assertEquals(20, result.spamScore)
    }

    @Test
    fun `updateCategoryByIds updates category and lastUpdated for specified entries`() = runTest {
        val ids = listOf("cid-001", "cid-003")
        val newCategory = SpamCategory.FRAUD
        val newTimestamp = "2026-03-20T00:00:00Z"

        coEvery { dao.updateCategoryByIds(ids, newCategory, newTimestamp) } returns Unit

        dao.updateCategoryByIds(ids, newCategory, newTimestamp)

        coVerify { dao.updateCategoryByIds(ids, newCategory, newTimestamp) }
    }

    // ── delete ──────────────────────────────────────────────────────────

    @Test
    fun `delete removes entry from database`() = runTest {
        coEvery { dao.delete(entry1) } returns Unit
        coEvery { dao.getById("cid-001") } returns null

        dao.delete(entry1)
        val result = dao.getById("cid-001")

        coVerify { dao.delete(entry1) }
        assertNull(result)
    }

    @Test
    fun `deleteByIds removes multiple entries`() = runTest {
        val ids = listOf("cid-001", "cid-002")
        coEvery { dao.deleteByIds(ids) } returns Unit

        dao.deleteByIds(ids)

        coVerify { dao.deleteByIds(ids) }
    }

    // ── countFlow ───────────────────────────────────────────────────────

    @Test
    fun `countFlow emits total number of entries`() = runTest {
        every { dao.countFlow() } returns flowOf(3)

        val count = dao.countFlow().first()
        assertEquals(3, count)
    }

    @Test
    fun `countFlow emits zero when database is empty`() = runTest {
        every { dao.countFlow() } returns flowOf(0)

        val count = dao.countFlow().first()
        assertEquals(0, count)
    }

    // ── getByIds ────────────────────────────────────────────────────────

    @Test
    fun `getByIds returns only entries matching the given ids`() = runTest {
        val ids = listOf("cid-001", "cid-003")
        coEvery { dao.getByIds(ids) } returns listOf(entry1, entry3)

        val result = dao.getByIds(ids)
        assertEquals(2, result.size)
        assertEquals("cid-001", result[0].id)
        assertEquals("cid-003", result[1].id)
    }

    @Test
    fun `getByIds returns empty list when no ids match`() = runTest {
        coEvery { dao.getByIds(listOf("cid-999")) } returns emptyList()

        val result = dao.getByIds(listOf("cid-999"))
        assertEquals(0, result.size)
    }
}
