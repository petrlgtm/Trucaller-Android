package com.byron.trucaller.viewmodel

import android.app.Application
import com.byron.trucaller.MainDispatcherRule
import com.byron.trucaller.data.model.BlockedNumber
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.data.repository.BlockedNumberRepository
import com.byron.trucaller.data.repository.CallerIdRepository
import com.byron.trucaller.data.repository.LookupResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [CallerIdViewModel].
 *
 * Tests lookup, block, report, and clear operations using mocked
 * repositories and the MainDispatcherRule for coroutine testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallerIdViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var callerIdRepository: CallerIdRepository
    private lateinit var blockedNumberRepository: BlockedNumberRepository
    private lateinit var preferences: UserPreferences
    private lateinit var viewModel: CallerIdViewModel

    private val safeEntry = CallerIdEntry(
        id = "cid-001",
        phoneNumber = "+256712345678",
        name = "Alice",
        spamScore = 0,
        reportCount = 0,
        category = SpamCategory.SAFE,
        lastUpdated = "2026-01-01T00:00:00Z"
    )

    private val spamEntry = CallerIdEntry(
        id = "cid-002",
        phoneNumber = "+256700000002",
        name = "Spam Bob",
        spamScore = 80,
        reportCount = 5,
        category = SpamCategory.SPAM,
        lastUpdated = "2026-02-01T00:00:00Z"
    )

    @Before
    fun setup() {
        // Mock android.util.Log to prevent RuntimeException in unit tests
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0

        // Mock ApiClient object to prevent real network calls in fire-and-forget syncs
        mockkObject(com.byron.trucaller.service.ApiClient)
        coEvery { com.byron.trucaller.service.ApiClient.deleteAdminCallerId(any()) } returns
                com.byron.trucaller.service.ApiResult(success = true, data = Unit)
        coEvery { com.byron.trucaller.service.ApiClient.updateAdminCallerId(any(), any(), any(), any()) } returns
                com.byron.trucaller.service.ApiResult(success = true, data = Unit)
        coEvery { com.byron.trucaller.service.ApiClient.createAdminCallerId(any(), any(), any(), any(), any(), any(), any()) } returns
                com.byron.trucaller.service.ApiResult(success = true, data = Unit)
        coEvery { com.byron.trucaller.service.ApiClient.reportSpamCall(any(), any()) } returns
                com.byron.trucaller.service.ApiResult(success = true, data = Unit)
        coEvery { com.byron.trucaller.service.ApiClient.getSpamVerification(any()) } returns
                com.byron.trucaller.service.ApiResult(success = false, data = null)
        coEvery { com.byron.trucaller.service.ApiClient.verifySpam(any(), any()) } returns
                com.byron.trucaller.service.ApiResult(success = false, data = null)

        application = mockk(relaxed = true)
        callerIdRepository = mockk(relaxed = true)
        blockedNumberRepository = mockk(relaxed = true)
        preferences = mockk(relaxed = true)

        // Default stubs for init block
        every { preferences.recentLookups } returns flowOf(emptyList())
        every { preferences.loggedInUserId } returns flowOf(null)
        every { callerIdRepository.getAllEntries() } returns flowOf(emptyList())

        viewModel = CallerIdViewModel(
            application,
            callerIdRepository,
            preferences,
            blockedNumberRepository
        )
    }

    // ── lookup ──────────────────────────────────────────────────────────

    @Test
    fun `lookup with found entry updates lookupResult and clears notFound`() = runTest {
        val lookupResult = LookupResult(callerIdEntry = safeEntry, contactMatch = null, source = "caller_id_db")
        coEvery { callerIdRepository.lookupNumber("+256712345678", null) } returns lookupResult
        coEvery { callerIdRepository.getById("cid-001") } returns safeEntry

        viewModel.lookup("+256712345678")
        advanceUntilIdle()

        val result = viewModel.lookupResult.value
        assertNotNull(result)
        assertEquals("Alice", result!!.callerIdEntry!!.name)
        assertFalse(viewModel.notFound.value)
    }

    @Test
    fun `lookup with not found entry sets notFound to true`() = runTest {
        val lookupResult = LookupResult(callerIdEntry = null, contactMatch = null, source = "not_found")
        coEvery { callerIdRepository.lookupNumber("+256799999999") } returns lookupResult

        viewModel.lookup("+256799999999")
        advanceUntilIdle()

        assertNull(viewModel.lookupResult.value)
        assertTrue(viewModel.notFound.value)
    }

    @Test
    fun `lookup with blank query does nothing`() = runTest {
        viewModel.lookup("")
        advanceUntilIdle()

        assertNull(viewModel.lookupResult.value)
        assertFalse(viewModel.notFound.value)
        coVerify(exactly = 0) { callerIdRepository.lookupNumber(any(), null) }
    }

    @Test
    fun `lookup with whitespace-only query does nothing`() = runTest {
        viewModel.lookup("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { callerIdRepository.lookupNumber(any(), null) }
    }

    @Test
    fun `lookup adds entry to recent lookups`() = runTest {
        val lookupResult = LookupResult(callerIdEntry = safeEntry, contactMatch = null, source = "caller_id_db")
        coEvery { callerIdRepository.lookupNumber("+256712345678", null) } returns lookupResult
        coEvery { callerIdRepository.getById("cid-001") } returns safeEntry

        viewModel.lookup("+256712345678")
        advanceUntilIdle()

        coVerify { preferences.addRecentLookup("cid-001") }
    }

    // ── blockNumber ─────────────────────────────────────────────────────

    @Test
    fun `blockNumber creates blocked entry and updates caller ID to spam`() = runTest {
        val lookupResult = LookupResult(callerIdEntry = safeEntry, contactMatch = null, source = "caller_id_db")
        coEvery { callerIdRepository.lookupNumber("+256712345678") } returns lookupResult
        coEvery { callerIdRepository.getById("cid-001") } returns safeEntry

        // First do a lookup to set the state
        viewModel.lookup("+256712345678")
        advanceUntilIdle()

        viewModel.blockNumber(safeEntry, "user-001")
        advanceUntilIdle()

        // Verify blocked number was inserted
        coVerify { blockedNumberRepository.blockNumber(match<BlockedNumber> {
            it.phoneNumber == "+256712345678" &&
            it.userId == "user-001" &&
            it.name == "Alice"
        }) }

        // Verify caller ID entry was updated to SPAM category
        coVerify { callerIdRepository.updateEntry(match {
            it.category == SpamCategory.SPAM &&
            it.spamScore >= 70
        }) }

        // Verify isNumberBlocked was set
        assertTrue(viewModel.isNumberBlocked.value)

        // Verify action message
        assertEquals("Alice has been blocked", viewModel.actionMessage.value)
    }

    @Test
    fun `unblockNumber calls repository and updates state`() = runTest {
        viewModel.unblockNumber("+256712345678", "user-001", "Alice")
        advanceUntilIdle()

        coVerify { blockedNumberRepository.unblockNumber("user-001", "+256712345678") }
        assertFalse(viewModel.isNumberBlocked.value)
        assertEquals("Alice has been unblocked", viewModel.actionMessage.value)
    }

    // ── checkIfBlocked ──────────────────────────────────────────────────

    @Test
    fun `checkIfBlocked sets isNumberBlocked to true when blocked`() = runTest {
        coEvery { blockedNumberRepository.isBlocked("user-001", "+256712345678") } returns true

        viewModel.checkIfBlocked("+256712345678", "user-001")
        advanceUntilIdle()

        assertTrue(viewModel.isNumberBlocked.value)
    }

    @Test
    fun `checkIfBlocked sets isNumberBlocked to false when not blocked`() = runTest {
        coEvery { blockedNumberRepository.isBlocked("user-001", "+256799999999") } returns false

        viewModel.checkIfBlocked("+256799999999", "user-001")
        advanceUntilIdle()

        assertFalse(viewModel.isNumberBlocked.value)
    }

    // ── reportNumber ────────────────────────────────────────────────────

    @Test
    fun `reportNumber increments report count and spam score`() = runTest {
        val lookupResult = LookupResult(callerIdEntry = safeEntry, contactMatch = null, source = "caller_id_db")
        coEvery { callerIdRepository.lookupNumber("+256712345678") } returns lookupResult
        coEvery { callerIdRepository.getById("cid-001") } returns safeEntry

        viewModel.lookup("+256712345678")
        advanceUntilIdle()

        viewModel.reportNumber(safeEntry)
        advanceUntilIdle()

        coVerify { callerIdRepository.updateEntry(match {
            it.reportCount == 1 &&
            it.spamScore == 5 // 0 + 5
        }) }
    }

    @Test
    fun `reportNumber with high initial score caps at 100`() = runTest {
        val highScoreEntry = safeEntry.copy(spamScore = 97, reportCount = 20)

        viewModel.reportNumber(highScoreEntry)
        advanceUntilIdle()

        coVerify { callerIdRepository.updateEntry(match {
            it.spamScore == 100 // min(97 + 5, 100)
        }) }
    }

    @Test
    fun `reportNumber updates category based on new score`() = runTest {
        // Score goes from 55 to 60 -> should be SPAM (>= 60)
        val moderateEntry = safeEntry.copy(spamScore = 55, reportCount = 5)

        viewModel.reportNumber(moderateEntry)
        advanceUntilIdle()

        coVerify { callerIdRepository.updateEntry(match {
            it.category == SpamCategory.SPAM // score 60 >= 60
        }) }
    }

    @Test
    fun `reportNumber sets FRAUD category for very high scores`() = runTest {
        // Score goes from 78 to 83 -> should be FRAUD (>= 80)
        val highEntry = safeEntry.copy(spamScore = 78, reportCount = 10)

        viewModel.reportNumber(highEntry)
        advanceUntilIdle()

        coVerify { callerIdRepository.updateEntry(match {
            it.category == SpamCategory.FRAUD // score 83 >= 80
        }) }
    }

    @Test
    fun `reportNumber sets SUSPECTED_SPAM for moderate scores`() = runTest {
        // Score goes from 25 to 30 -> should be SUSPECTED_SPAM (>= 30)
        val lowEntry = safeEntry.copy(spamScore = 25, reportCount = 2)

        viewModel.reportNumber(lowEntry)
        advanceUntilIdle()

        coVerify { callerIdRepository.updateEntry(match {
            it.category == SpamCategory.SUSPECTED_SPAM // score 30 >= 30
        }) }
    }

    @Test
    fun `reportNumber sets actionMessage with report count`() = runTest {
        viewModel.reportNumber(safeEntry)
        advanceUntilIdle()

        val message = viewModel.actionMessage.value
        assertNotNull(message)
        assertTrue(message!!.contains("Reported Alice"))
        assertTrue(message.contains("Reports: 1"))
    }

    // ── clearLookup ─────────────────────────────────────────────────────

    @Test
    fun `clearLookup resets lookupResult and notFound`() = runTest {
        // First do a lookup
        val lookupResult = LookupResult(callerIdEntry = safeEntry, contactMatch = null, source = "caller_id_db")
        coEvery { callerIdRepository.lookupNumber("+256712345678", null) } returns lookupResult
        coEvery { callerIdRepository.getById("cid-001") } returns safeEntry

        viewModel.lookup("+256712345678")
        advanceUntilIdle()
        assertNotNull(viewModel.lookupResult.value)

        // Clear
        viewModel.clearLookup()

        assertNull(viewModel.lookupResult.value)
        assertFalse(viewModel.notFound.value)
    }

    // ── clearActionMessage ──────────────────────────────────────────────

    @Test
    fun `clearActionMessage sets actionMessage to null`() = runTest {
        // Set an action message via report
        viewModel.reportNumber(safeEntry)
        advanceUntilIdle()
        assertNotNull(viewModel.actionMessage.value)

        viewModel.clearActionMessage()

        assertNull(viewModel.actionMessage.value)
    }

    // ── selectRecentLookup ──────────────────────────────────────────────

    @Test
    fun `selectRecentLookup updates lookupResult with recent source`() {
        viewModel.selectRecentLookup(safeEntry)

        val result = viewModel.lookupResult.value
        assertNotNull(result)
        assertEquals("recent", result!!.source)
        assertEquals("Alice", result.callerIdEntry!!.name)
        assertFalse(viewModel.notFound.value)
    }

    // ── addEntry ────────────────────────────────────────────────────────

    @Test
    fun `addEntry inserts entry via repository`() = runTest {
        viewModel.addEntry(safeEntry)
        advanceUntilIdle()

        coVerify { callerIdRepository.insertEntry(safeEntry) }
    }

    // ── updateEntry ─────────────────────────────────────────────────────

    @Test
    fun `updateEntry delegates to repository`() = runTest {
        viewModel.updateEntry(safeEntry)
        advanceUntilIdle()

        coVerify { callerIdRepository.updateEntry(safeEntry) }
    }

    // ── deleteEntry ─────────────────────────────────────────────────────

    @Test
    fun `deleteEntry removes entry via repository`() = runTest {
        viewModel.deleteEntry(safeEntry)
        advanceUntilIdle()

        coVerify { callerIdRepository.deleteEntry(safeEntry) }
    }

    // ── bulkDeleteEntries ───────────────────────────────────────────────

    @Test
    fun `bulkDeleteEntries calls repository deleteEntriesByIds`() = runTest {
        val ids = setOf("cid-001", "cid-002")

        viewModel.bulkDeleteEntries(ids)
        advanceUntilIdle()

        coVerify { callerIdRepository.deleteEntriesByIds(ids.toList()) }
    }

    @Test
    fun `bulkDeleteEntries resets isBulkOperationInProgress after completion`() = runTest {
        viewModel.bulkDeleteEntries(setOf("cid-001"))
        advanceUntilIdle()

        // After completion, should be false regardless of API sync outcome
        assertFalse(viewModel.isBulkOperationInProgress.value)
    }

    @Test
    fun `bulkDeleteEntries sets action message on success`() = runTest {
        // Mock ApiClient to prevent real network calls
        mockkStatic("com.byron.trucaller.service.ApiClient")
        val ids = setOf("cid-001", "cid-002")

        viewModel.bulkDeleteEntries(ids)
        advanceUntilIdle()

        // Action message is set (may be success or sync error depending on ApiClient mock)
        val msg = viewModel.actionMessage.value
        assertNotNull(msg)
    }

    // ── bulkUpdateCategory ──────────────────────────────────────────────

    @Test
    fun `bulkUpdateCategory calls repository updateCategoryByIds`() = runTest {
        val ids = setOf("cid-001", "cid-002")

        viewModel.bulkUpdateCategory(ids, SpamCategory.FRAUD)
        advanceUntilIdle()

        coVerify { callerIdRepository.updateCategoryByIds(ids.toList(), SpamCategory.FRAUD, any()) }
    }

    @Test
    fun `bulkUpdateCategory sets action message on success`() = runTest {
        mockkStatic("com.byron.trucaller.service.ApiClient")
        val ids = setOf("cid-001", "cid-002")

        viewModel.bulkUpdateCategory(ids, SpamCategory.FRAUD)
        advanceUntilIdle()

        val msg = viewModel.actionMessage.value
        assertNotNull(msg)
    }

    // ── StateFlow initial values ────────────────────────────────────────

    @Test
    fun `initial lookupResult is null`() {
        assertNull(viewModel.lookupResult.value)
    }

    @Test
    fun `initial notFound is false`() {
        assertFalse(viewModel.notFound.value)
    }

    @Test
    fun `initial isNumberBlocked is false`() {
        assertFalse(viewModel.isNumberBlocked.value)
    }

    @Test
    fun `initial actionMessage is null`() {
        assertNull(viewModel.actionMessage.value)
    }

    @Test
    fun `initial recentLookups is empty`() {
        assertTrue(viewModel.recentLookups.value.isEmpty())
    }

    @Test
    fun `initial isBulkOperationInProgress is false`() {
        assertFalse(viewModel.isBulkOperationInProgress.value)
    }

    // ── getBlockedNumbers ───────────────────────────────────────────────

    @Test
    fun `getBlockedNumbers delegates to blockedNumberRepository`() = runTest {
        val blockedList = listOf(
            BlockedNumber("blk-1", "user-001", "+256712345678", "Spam", "Spam", "2026-01-01T00:00:00Z")
        )
        every { blockedNumberRepository.getBlockedByUser("user-001") } returns flowOf(blockedList)

        val result = viewModel.getBlockedNumbers("user-001").first()

        assertEquals(1, result.size)
        assertEquals("+256712345678", result[0].phoneNumber)
    }

    // ── updateEntryName ─────────────────────────────────────────────────

    @Test
    fun `updateEntryName updates name and action message`() = runTest {
        val lookupResult = LookupResult(callerIdEntry = safeEntry, contactMatch = null, source = "caller_id_db")
        coEvery { callerIdRepository.lookupNumber("+256712345678") } returns lookupResult
        coEvery { callerIdRepository.getById("cid-001") } returns safeEntry

        viewModel.lookup("+256712345678")
        advanceUntilIdle()

        viewModel.updateEntryName(safeEntry, "Alice New Name")
        advanceUntilIdle()

        coVerify { callerIdRepository.updateEntry(match { it.name == "Alice New Name" }) }
        assertEquals("Updated name to Alice New Name", viewModel.actionMessage.value)
    }
}
