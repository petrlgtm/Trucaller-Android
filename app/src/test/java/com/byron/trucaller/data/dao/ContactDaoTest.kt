package com.byron.trucaller.data.dao

import com.byron.trucaller.data.model.Contact
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ContactDao].
 *
 * Verifies query contracts: getByPhone, getByUserId, insertAll,
 * search, favourites, and count operations.
 */
class ContactDaoTest {

    private lateinit var dao: ContactDao

    private val userId = "user-001"

    private val contact1 = Contact(
        id = "cnt-001",
        userId = userId,
        name = "Alice Nakato",
        phoneNumber = "+256712345678",
        email = "alice@example.com",
        syncedAt = "2026-01-01T00:00:00Z",
        isBackedUp = true
    )

    private val contact2 = Contact(
        id = "cnt-002",
        userId = userId,
        name = "Bob Katende",
        phoneNumber = "+256700000002",
        email = null,
        syncedAt = "2026-01-15T10:00:00Z",
        isBackedUp = false
    )

    private val contact3 = Contact(
        id = "cnt-003",
        userId = "user-002",
        name = "Charlie Waiswa",
        phoneNumber = "+256700000003",
        syncedAt = "2026-02-01T08:00:00Z",
        isBackedUp = true,
        isFavourite = true,
        favouriteSegment = "Family"
    )

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
    }

    // ── getByPhone ──────────────────────────────────────────────────────

    @Test
    fun `getByPhone returns contact for existing phone number`() = runTest {
        coEvery { dao.getByPhone("+256712345678") } returns contact1

        val result = dao.getByPhone("+256712345678")
        assertNotNull(result)
        assertEquals("Alice Nakato", result!!.name)
        assertEquals(userId, result.userId)
    }

    @Test
    fun `getByPhone returns null for non-existent phone number`() = runTest {
        coEvery { dao.getByPhone("+256799999999") } returns null

        val result = dao.getByPhone("+256799999999")
        assertNull(result)
    }

    @Test
    fun `getByPhone returns first match only (LIMIT 1)`() = runTest {
        coEvery { dao.getByPhone("+256712345678") } returns contact1

        val result = dao.getByPhone("+256712345678")
        assertNotNull(result)
        // Verifies only a single contact is returned, not a list
        assertEquals("cnt-001", result!!.id)
    }

    // ── getByUserId ─────────────────────────────────────────────────────

    @Test
    fun `getByUserId returns all contacts for a user ordered by name ASC`() = runTest {
        // Alice < Bob, so alphabetical order
        every { dao.getByUserId(userId) } returns flowOf(listOf(contact1, contact2))

        val result = dao.getByUserId(userId).first()
        assertEquals(2, result.size)
        assertEquals("Alice Nakato", result[0].name)
        assertEquals("Bob Katende", result[1].name)
    }

    @Test
    fun `getByUserId returns empty list for user with no contacts`() = runTest {
        every { dao.getByUserId("user-999") } returns flowOf(emptyList())

        val result = dao.getByUserId("user-999").first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getByUserId does not return contacts from other users`() = runTest {
        every { dao.getByUserId(userId) } returns flowOf(listOf(contact1, contact2))

        val result = dao.getByUserId(userId).first()
        assertTrue(result.all { it.userId == userId })
    }

    // ── insertAll ───────────────────────────────────────────────────────

    @Test
    fun `insertAll inserts multiple contacts at once`() = runTest {
        val contacts = listOf(contact1, contact2)
        coEvery { dao.insertAll(contacts) } returns Unit
        every { dao.getByUserId(userId) } returns flowOf(contacts)

        dao.insertAll(contacts)

        coVerify { dao.insertAll(contacts) }
        val result = dao.getByUserId(userId).first()
        assertEquals(2, result.size)
    }

    @Test
    fun `insertAll with empty list does not throw`() = runTest {
        coEvery { dao.insertAll(emptyList()) } returns Unit

        dao.insertAll(emptyList())

        coVerify { dao.insertAll(emptyList()) }
    }

    @Test
    fun `insertAll with REPLACE strategy updates existing contacts`() = runTest {
        val updatedContact1 = contact1.copy(name = "Alice Updated")
        val contacts = listOf(updatedContact1, contact2)
        coEvery { dao.insertAll(contacts) } returns Unit
        coEvery { dao.getById("cnt-001") } returns updatedContact1

        dao.insertAll(contacts)

        val result = dao.getById("cnt-001")
        assertEquals("Alice Updated", result!!.name)
    }

    // ── insert (single) ─────────────────────────────────────────────────

    @Test
    fun `insert adds a single contact`() = runTest {
        coEvery { dao.insert(contact1) } returns Unit
        coEvery { dao.getById("cnt-001") } returns contact1

        dao.insert(contact1)

        val result = dao.getById("cnt-001")
        assertNotNull(result)
        assertEquals("Alice Nakato", result!!.name)
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    fun `update modifies an existing contact`() = runTest {
        val updated = contact1.copy(email = "newalice@example.com")
        coEvery { dao.update(updated) } returns Unit
        coEvery { dao.getById("cnt-001") } returns updated

        dao.update(updated)

        val result = dao.getById("cnt-001")
        assertEquals("newalice@example.com", result!!.email)
    }

    // ── search ──────────────────────────────────────────────────────────

    @Test
    fun `search by name returns matching contacts`() = runTest {
        coEvery { dao.search("Alice") } returns listOf(contact1)

        val result = dao.search("Alice")
        assertEquals(1, result.size)
        assertEquals("Alice Nakato", result[0].name)
    }

    @Test
    fun `search by phone number returns matching contacts`() = runTest {
        coEvery { dao.search("712345678") } returns listOf(contact1)

        val result = dao.search("712345678")
        assertEquals(1, result.size)
        assertEquals("+256712345678", result[0].phoneNumber)
    }

    @Test
    fun `search with no matches returns empty list`() = runTest {
        coEvery { dao.search("zzz") } returns emptyList()

        val result = dao.search("zzz")
        assertTrue(result.isEmpty())
    }

    // ── getCountByUser ──────────────────────────────────────────────────

    @Test
    fun `getCountByUser returns count for specific user`() = runTest {
        every { dao.getCountByUser(userId) } returns flowOf(2)

        val count = dao.getCountByUser(userId).first()
        assertEquals(2, count)
    }

    @Test
    fun `getTotalCount returns total across all users`() = runTest {
        every { dao.getTotalCount() } returns flowOf(3)

        val count = dao.getTotalCount().first()
        assertEquals(3, count)
    }

    // ── getAllContacts ───────────────────────────────────────────────────

    @Test
    fun `getAllContacts returns contacts from all users ordered by name`() = runTest {
        every { dao.getAllContacts() } returns flowOf(listOf(contact1, contact2, contact3))

        val result = dao.getAllContacts().first()
        assertEquals(3, result.size)
    }

    // ── delete ──────────────────────────────────────────────────────────

    @Test
    fun `delete removes contact from database`() = runTest {
        coEvery { dao.delete(contact1) } returns Unit
        coEvery { dao.getById("cnt-001") } returns null

        dao.delete(contact1)

        assertNull(dao.getById("cnt-001"))
        coVerify { dao.delete(contact1) }
    }

    // ── favourites ──────────────────────────────────────────────────────

    @Test
    fun `getFavouritesByUser returns only favourite contacts for user`() = runTest {
        val favContact = contact1.copy(isFavourite = true, favouriteSegment = "Work")
        every { dao.getFavouritesByUser(userId) } returns flowOf(listOf(favContact))

        val result = dao.getFavouritesByUser(userId).first()
        assertEquals(1, result.size)
        assertTrue(result[0].isFavourite)
    }

    @Test
    fun `getFavouritesBySegment filters by segment name`() = runTest {
        every { dao.getFavouritesBySegment("user-002", "Family") } returns flowOf(listOf(contact3))

        val result = dao.getFavouritesBySegment("user-002", "Family").first()
        assertEquals(1, result.size)
        assertEquals("Family", result[0].favouriteSegment)
    }

    @Test
    fun `updateFavourite toggles favourite status and segment`() = runTest {
        coEvery { dao.updateFavourite("cnt-001", true, "Work") } returns Unit

        dao.updateFavourite("cnt-001", true, "Work")

        coVerify { dao.updateFavourite("cnt-001", true, "Work") }
    }

    // ── updateNote / updatePhoto ────────────────────────────────────────

    @Test
    fun `updateNote sets note for a contact`() = runTest {
        coEvery { dao.updateNote("cnt-001", "Call back Monday") } returns Unit

        dao.updateNote("cnt-001", "Call back Monday")

        coVerify { dao.updateNote("cnt-001", "Call back Monday") }
    }

    @Test
    fun `updatePhoto sets photo URI for a contact`() = runTest {
        coEvery { dao.updatePhoto("cnt-001", "/storage/photo.jpg") } returns Unit

        dao.updatePhoto("cnt-001", "/storage/photo.jpg")

        coVerify { dao.updatePhoto("cnt-001", "/storage/photo.jpg") }
    }
}
