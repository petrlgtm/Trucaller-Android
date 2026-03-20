package com.byron.trucaller.data.repository

import com.byron.trucaller.data.dao.BlockedNumberDao
import com.byron.trucaller.data.model.BlockedNumber
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BlockedNumberRepository].
 *
 * Verifies block/unblock/isBlocked operations, phone normalization before
 * DAO calls, and pass-through Flow queries.
 */
class BlockedNumberRepositoryTest {

    private lateinit var dao: BlockedNumberDao
    private lateinit var repository: BlockedNumberRepository

    private val userId = "user-001"

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

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repository = BlockedNumberRepository(dao)
    }

    // ── blockNumber ─────────────────────────────────────────────────────

    @Test
    fun `blockNumber inserts normalized blocked number into DAO`() = runTest {
        val captured = slot<BlockedNumber>()
        coEvery { dao.insert(capture(captured)) } returns Unit

        repository.blockNumber(blocked1)

        coVerify { dao.insert(any()) }
        assertEquals("+256712345678", captured.captured.phoneNumber)
    }

    @Test
    fun `blockNumber normalizes local phone format before insert`() = runTest {
        val localFormatBlocked = blocked1.copy(phoneNumber = "0712345678")
        val captured = slot<BlockedNumber>()
        coEvery { dao.insert(capture(captured)) } returns Unit

        repository.blockNumber(localFormatBlocked)

        // "0712345678" should be normalized to "+256712345678"
        assertEquals("+256712345678", captured.captured.phoneNumber)
    }

    @Test
    fun `blockNumber normalizes bare subscriber number`() = runTest {
        val bareNumber = blocked1.copy(phoneNumber = "712345678")
        val captured = slot<BlockedNumber>()
        coEvery { dao.insert(capture(captured)) } returns Unit

        repository.blockNumber(bareNumber)

        assertEquals("+256712345678", captured.captured.phoneNumber)
    }

    @Test
    fun `blockNumber preserves all other fields after normalization`() = runTest {
        val captured = slot<BlockedNumber>()
        coEvery { dao.insert(capture(captured)) } returns Unit

        repository.blockNumber(blocked1)

        val inserted = captured.captured
        assertEquals("blk-001", inserted.id)
        assertEquals(userId, inserted.userId)
        assertEquals("Spam Caller", inserted.name)
        assertEquals("Spam calls", inserted.reason)
        assertEquals("2026-01-15T10:00:00Z", inserted.blockedAt)
    }

    // ── unblockNumber ───────────────────────────────────────────────────

    @Test
    fun `unblockNumber calls DAO unblock with normalized phone`() = runTest {
        coEvery { dao.unblock(userId, "+256712345678") } returns Unit

        repository.unblockNumber(userId, "+256712345678")

        coVerify { dao.unblock(userId, "+256712345678") }
    }

    @Test
    fun `unblockNumber normalizes local phone format`() = runTest {
        coEvery { dao.unblock(userId, "+256712345678") } returns Unit

        repository.unblockNumber(userId, "0712345678")

        coVerify { dao.unblock(userId, "+256712345678") }
    }

    @Test
    fun `unblockNumber normalizes bare subscriber number`() = runTest {
        coEvery { dao.unblock(userId, "+256712345678") } returns Unit

        repository.unblockNumber(userId, "712345678")

        coVerify { dao.unblock(userId, "+256712345678") }
    }

    // ── isBlocked ───────────────────────────────────────────────────────

    @Test
    fun `isBlocked returns true when number is blocked for user`() = runTest {
        coEvery { dao.isBlocked(userId, "+256712345678") } returns true

        val result = repository.isBlocked(userId, "+256712345678")

        assertTrue(result)
    }

    @Test
    fun `isBlocked returns false when number is not blocked`() = runTest {
        coEvery { dao.isBlocked(userId, "+256799999999") } returns false

        val result = repository.isBlocked(userId, "+256799999999")

        assertFalse(result)
    }

    @Test
    fun `isBlocked normalizes phone before querying DAO`() = runTest {
        coEvery { dao.isBlocked(userId, "+256712345678") } returns true

        val result = repository.isBlocked(userId, "0712345678")

        assertTrue(result)
        coVerify { dao.isBlocked(userId, "+256712345678") }
    }

    @Test
    fun `isBlocked normalizes number without plus prefix`() = runTest {
        coEvery { dao.isBlocked(userId, "+256712345678") } returns true

        val result = repository.isBlocked(userId, "256712345678")

        assertTrue(result)
        coVerify { dao.isBlocked(userId, "+256712345678") }
    }

    // ── getBlockedByUser ────────────────────────────────────────────────

    @Test
    fun `getBlockedByUser returns Flow of blocked numbers for user`() = runTest {
        every { dao.getByUserId(userId) } returns flowOf(listOf(blocked1, blocked2))

        val result = repository.getBlockedByUser(userId).first()

        assertEquals(2, result.size)
        assertEquals("blk-001", result[0].id)
        assertEquals("blk-002", result[1].id)
    }

    @Test
    fun `getBlockedByUser returns empty Flow for user with no blocks`() = runTest {
        every { dao.getByUserId("user-999") } returns flowOf(emptyList())

        val result = repository.getBlockedByUser("user-999").first()

        assertTrue(result.isEmpty())
    }

    // ── getBlockedCount ─────────────────────────────────────────────────

    @Test
    fun `getBlockedCount returns count Flow for user`() = runTest {
        every { dao.getCountByUser(userId) } returns flowOf(2)

        val count = repository.getBlockedCount(userId).first()

        assertEquals(2, count)
    }

    @Test
    fun `getBlockedCount returns zero for user with no blocks`() = runTest {
        every { dao.getCountByUser("user-999") } returns flowOf(0)

        val count = repository.getBlockedCount("user-999").first()

        assertEquals(0, count)
    }
}
