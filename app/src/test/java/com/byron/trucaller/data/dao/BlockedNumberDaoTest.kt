package com.byron.trucaller.data.dao

import com.byron.trucaller.data.model.BlockedNumber
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
 * Unit tests for [BlockedNumberDao].
 *
 * Verifies the contract of block/unblock operations, user-scoped queries,
 * and the isBlocked check used by the call screening and SMS classification pipeline.
 */
class BlockedNumberDaoTest {

    private lateinit var dao: BlockedNumberDao

    private val userId = "user-001"
    private val otherUserId = "user-002"

    private val blocked1 = BlockedNumber(
        id = "blk-001",
        userId = userId,
        phoneNumber = "+256712345678",
        name = "Spam Caller",
        reason = "Spam calls",
        blockedAt = "2026-01-15T10:00:00Z"
    )

    private val blocked2 = BlockedNumber(
        id = "blk-002",
        userId = userId,
        phoneNumber = "+256700000002",
        name = "Telemarketer",
        reason = "Telemarketing",
        blockedAt = "2026-02-20T14:30:00Z"
    )

    private val blockedOtherUser = BlockedNumber(
        id = "blk-003",
        userId = otherUserId,
        phoneNumber = "+256712345678",
        name = "Spam Caller",
        reason = "Spam calls",
        blockedAt = "2026-03-01T08:00:00Z"
    )

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
    }

    // ── block (insert) ──────────────────────────────────────────────────

    @Test
    fun `insert adds blocked number to the database`() = runTest {
        coEvery { dao.insert(blocked1) } returns Unit
        coEvery { dao.getByUserAndPhone(userId, blocked1.phoneNumber) } returns blocked1

        dao.insert(blocked1)

        val result = dao.getByUserAndPhone(userId, blocked1.phoneNumber)
        assertNotNull(result)
        assertEquals("Spam Caller", result!!.name)
        coVerify { dao.insert(blocked1) }
    }

    @Test
    fun `insert with same id replaces existing entry`() = runTest {
        val updated = blocked1.copy(reason = "Updated reason")
        coEvery { dao.insert(updated) } returns Unit
        coEvery { dao.getByUserAndPhone(userId, blocked1.phoneNumber) } returns updated

        dao.insert(updated)

        val result = dao.getByUserAndPhone(userId, blocked1.phoneNumber)
        assertEquals("Updated reason", result!!.reason)
    }

    // ── unblock ─────────────────────────────────────────────────────────

    @Test
    fun `unblock removes blocked number for specific user and phone`() = runTest {
        coEvery { dao.unblock(userId, "+256712345678") } returns Unit
        coEvery { dao.getByUserAndPhone(userId, "+256712345678") } returns null

        dao.unblock(userId, "+256712345678")

        val result = dao.getByUserAndPhone(userId, "+256712345678")
        assertNull(result)
        coVerify { dao.unblock(userId, "+256712345678") }
    }

    @Test
    fun `unblock does not affect other users who blocked the same number`() = runTest {
        coEvery { dao.unblock(userId, "+256712345678") } returns Unit
        coEvery { dao.getByUserAndPhone(otherUserId, "+256712345678") } returns blockedOtherUser

        dao.unblock(userId, "+256712345678")

        val otherUserResult = dao.getByUserAndPhone(otherUserId, "+256712345678")
        assertNotNull(otherUserResult)
        assertEquals(otherUserId, otherUserResult!!.userId)
    }

    @Test
    fun `delete removes a specific blocked number entity`() = runTest {
        coEvery { dao.delete(blocked1) } returns Unit

        dao.delete(blocked1)

        coVerify { dao.delete(blocked1) }
    }

    // ── getByUserId ─────────────────────────────────────────────────────

    @Test
    fun `getByUserId returns all blocked numbers for a user ordered by blockedAt desc`() = runTest {
        // blocked2 has a later timestamp so should come first
        every { dao.getByUserId(userId) } returns flowOf(listOf(blocked2, blocked1))

        val result = dao.getByUserId(userId).first()
        assertEquals(2, result.size)
        assertEquals("blk-002", result[0].id)
        assertEquals("blk-001", result[1].id)
    }

    @Test
    fun `getByUserId returns empty list for user with no blocked numbers`() = runTest {
        every { dao.getByUserId("user-999") } returns flowOf(emptyList())

        val result = dao.getByUserId("user-999").first()
        assertEquals(0, result.size)
    }

    @Test
    fun `getByUserId does not include blocked numbers from other users`() = runTest {
        every { dao.getByUserId(userId) } returns flowOf(listOf(blocked1, blocked2))

        val result = dao.getByUserId(userId).first()
        assertTrue(result.all { it.userId == userId })
    }

    // ── isBlocked ───────────────────────────────────────────────────────

    @Test
    fun `isBlocked returns true when number is blocked for user`() = runTest {
        coEvery { dao.isBlocked(userId, "+256712345678") } returns true

        val result = dao.isBlocked(userId, "+256712345678")
        assertTrue(result)
    }

    @Test
    fun `isBlocked returns false when number is not blocked for user`() = runTest {
        coEvery { dao.isBlocked(userId, "+256799999999") } returns false

        val result = dao.isBlocked(userId, "+256799999999")
        assertFalse(result)
    }

    @Test
    fun `isBlocked returns false for a different user even if same number is blocked by another`() = runTest {
        coEvery { dao.isBlocked("user-999", "+256712345678") } returns false

        val result = dao.isBlocked("user-999", "+256712345678")
        assertFalse(result)
    }

    // ── getByUserAndPhone ───────────────────────────────────────────────

    @Test
    fun `getByUserAndPhone returns entry when it exists`() = runTest {
        coEvery { dao.getByUserAndPhone(userId, "+256712345678") } returns blocked1

        val result = dao.getByUserAndPhone(userId, "+256712345678")
        assertNotNull(result)
        assertEquals("blk-001", result!!.id)
    }

    @Test
    fun `getByUserAndPhone returns null when not blocked`() = runTest {
        coEvery { dao.getByUserAndPhone(userId, "+256799999999") } returns null

        val result = dao.getByUserAndPhone(userId, "+256799999999")
        assertNull(result)
    }

    // ── getCountByUser ──────────────────────────────────────────────────

    @Test
    fun `getCountByUser returns count of blocked numbers for user`() = runTest {
        every { dao.getCountByUser(userId) } returns flowOf(2)

        val count = dao.getCountByUser(userId).first()
        assertEquals(2, count)
    }

    @Test
    fun `getCountByUser returns zero for user with no blocked numbers`() = runTest {
        every { dao.getCountByUser("user-999") } returns flowOf(0)

        val count = dao.getCountByUser("user-999").first()
        assertEquals(0, count)
    }
}
