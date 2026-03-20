package com.byron.trucaller.data.dao

import com.byron.trucaller.data.model.TrustLevel
import com.byron.trucaller.data.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [UserDao].
 *
 * Verifies query contracts: getByPhone, insert/update roundtrips,
 * count, setActive, and Flow emissions.
 */
class UserDaoTest {

    private lateinit var dao: UserDao

    private val user1 = User(
        id = "user-001",
        fullName = "Alice Nakato",
        phoneNumber = "+256712345678",
        email = "alice@example.com",
        passwordHash = "hashed_pw_1",
        createdAt = "2026-01-01T00:00:00Z",
        lastLogin = "2026-03-01T10:00:00Z",
        isActive = true,
        trustScore = 50,
        trustLevel = TrustLevel.TRUSTED
    )

    private val user2 = User(
        id = "user-002",
        fullName = "Bob Katende",
        phoneNumber = "+256700000002",
        email = null,
        passwordHash = "hashed_pw_2",
        createdAt = "2026-02-01T00:00:00Z",
        lastLogin = null,
        isActive = true,
        trustScore = 10,
        trustLevel = TrustLevel.NEW
    )

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
    }

    // ── getByPhone ──────────────────────────────────────────────────────

    @Test
    fun `getByPhone returns user for existing phone number`() = runTest {
        coEvery { dao.getByPhone("+256712345678") } returns user1

        val result = dao.getByPhone("+256712345678")
        assertNotNull(result)
        assertEquals("Alice Nakato", result!!.fullName)
        assertEquals(TrustLevel.TRUSTED, result.trustLevel)
    }

    @Test
    fun `getByPhone returns null for non-existent phone number`() = runTest {
        coEvery { dao.getByPhone("+256799999999") } returns null

        val result = dao.getByPhone("+256799999999")
        assertNull(result)
    }

    @Test
    fun `getByPhone matches exact phone number only`() = runTest {
        coEvery { dao.getByPhone("+256712345678") } returns user1
        coEvery { dao.getByPhone("0712345678") } returns null

        val exact = dao.getByPhone("+256712345678")
        val local = dao.getByPhone("0712345678")

        assertNotNull(exact)
        assertNull(local)
    }

    // ── getById ─────────────────────────────────────────────────────────

    @Test
    fun `getById returns user for existing id`() = runTest {
        coEvery { dao.getById("user-001") } returns user1

        val result = dao.getById("user-001")
        assertNotNull(result)
        assertEquals("+256712345678", result!!.phoneNumber)
    }

    @Test
    fun `getById returns null for non-existent id`() = runTest {
        coEvery { dao.getById("user-999") } returns null

        val result = dao.getById("user-999")
        assertNull(result)
    }

    // ── insert + update roundtrip ───────────────────────────────────────

    @Test
    fun `insert then getById roundtrip returns inserted user`() = runTest {
        coEvery { dao.insert(user1) } returns Unit
        coEvery { dao.getById("user-001") } returns user1

        dao.insert(user1)
        val retrieved = dao.getById("user-001")

        coVerify { dao.insert(user1) }
        assertNotNull(retrieved)
        assertEquals(user1.fullName, retrieved!!.fullName)
        assertEquals(user1.phoneNumber, retrieved.phoneNumber)
        assertEquals(user1.passwordHash, retrieved.passwordHash)
        assertTrue(retrieved.isActive)
    }

    @Test
    fun `insert with REPLACE overwrites existing user with same id`() = runTest {
        val updated = user1.copy(fullName = "Alice Updated")
        coEvery { dao.insert(updated) } returns Unit
        coEvery { dao.getById("user-001") } returns updated

        dao.insert(updated)

        val result = dao.getById("user-001")
        assertEquals("Alice Updated", result!!.fullName)
    }

    @Test
    fun `update modifies existing user fields`() = runTest {
        val updated = user1.copy(
            lastLogin = "2026-03-20T12:00:00Z",
            trustScore = 80,
            trustLevel = TrustLevel.VERIFIED
        )
        coEvery { dao.update(updated) } returns Unit
        coEvery { dao.getById("user-001") } returns updated

        dao.update(updated)
        val result = dao.getById("user-001")

        coVerify { dao.update(updated) }
        assertEquals("2026-03-20T12:00:00Z", result!!.lastLogin)
        assertEquals(80, result.trustScore)
        assertEquals(TrustLevel.VERIFIED, result.trustLevel)
    }

    @Test
    fun `update preserves unchanged fields`() = runTest {
        val updated = user1.copy(lastLogin = "2026-03-20T00:00:00Z")
        coEvery { dao.update(updated) } returns Unit
        coEvery { dao.getById("user-001") } returns updated

        dao.update(updated)
        val result = dao.getById("user-001")

        // Original fields should be preserved
        assertEquals("Alice Nakato", result!!.fullName)
        assertEquals("+256712345678", result.phoneNumber)
        assertEquals("hashed_pw_1", result.passwordHash)
    }

    // ── getAll ──────────────────────────────────────────────────────────

    @Test
    fun `getAll returns all users as Flow`() = runTest {
        every { dao.getAll() } returns flowOf(listOf(user1, user2))

        val result = dao.getAll().first()
        assertEquals(2, result.size)
    }

    @Test
    fun `getAll returns empty list when no users exist`() = runTest {
        every { dao.getAll() } returns flowOf(emptyList())

        val result = dao.getAll().first()
        assertTrue(result.isEmpty())
    }

    // ── count ───────────────────────────────────────────────────────────

    @Test
    fun `count returns total number of users`() = runTest {
        coEvery { dao.count() } returns 2

        val result = dao.count()
        assertEquals(2, result)
    }

    @Test
    fun `count returns zero when no users exist`() = runTest {
        coEvery { dao.count() } returns 0

        val result = dao.count()
        assertEquals(0, result)
    }

    @Test
    fun `countFlow emits user count as Flow`() = runTest {
        every { dao.countFlow() } returns flowOf(2)

        val count = dao.countFlow().first()
        assertEquals(2, count)
    }

    // ── setActive ───────────────────────────────────────────────────────

    @Test
    fun `setActive deactivates a user`() = runTest {
        val deactivated = user1.copy(isActive = false)
        coEvery { dao.setActive("user-001", false) } returns Unit
        coEvery { dao.getById("user-001") } returns deactivated

        dao.setActive("user-001", false)

        val result = dao.getById("user-001")
        coVerify { dao.setActive("user-001", false) }
        assertFalse(result!!.isActive)
    }

    @Test
    fun `setActive reactivates a deactivated user`() = runTest {
        val reactivated = user1.copy(isActive = true)
        coEvery { dao.setActive("user-001", true) } returns Unit
        coEvery { dao.getById("user-001") } returns reactivated

        dao.setActive("user-001", true)

        val result = dao.getById("user-001")
        coVerify { dao.setActive("user-001", true) }
        assertTrue(result!!.isActive)
    }
}
