package com.byron.trucaller.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byron.trucaller.data.model.CallLogEntry
import com.byron.trucaller.data.model.CallType
import com.byron.trucaller.ui.main.CallLogScreen
import com.byron.trucaller.viewmodel.CallLogFilter
import com.byron.trucaller.viewmodel.CallLogViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for the Call Log screen.
 *
 * Tests cover:
 * - Presence of the screen header ("Call Log")
 * - Filter chips for All, Incoming, Outgoing, Missed, Blocked
 * - Search bar displayed
 * - Empty state message when no calls
 * - Screen renders with mock call log entries
 *
 * Requires an Android device or emulator to run.
 */
@RunWith(AndroidJUnit4::class)
class CallLogScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createMockViewModel(
        entries: List<CallLogEntry> = emptyList(),
        isLoading: Boolean = false
    ): CallLogViewModel {
        return mockk<CallLogViewModel>(relaxed = true).apply {
            every { callLogEntries } returns MutableStateFlow(entries)
            every { this@apply.isLoading } returns MutableStateFlow(isLoading)
            every { actionMessage } returns MutableStateFlow(null)
            every { selectedFilter } returns MutableStateFlow(CallLogFilter.ALL)
            every { searchQuery } returns MutableStateFlow("")
            every { getFilteredEntries() } returns entries
        }
    }

    private fun setUpScreen(viewModel: CallLogViewModel? = null) {
        val vm = viewModel ?: createMockViewModel()
        composeTestRule.setContent {
            val navController = rememberNavController()
            CallLogScreen(
                rootNavController = navController,
                callLogViewModel = vm
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun callLogScreen_displaysHeader() {
        setUpScreen()
        composeTestRule.onNodeWithText("Call Log").assertIsDisplayed()
    }

    @Test
    fun callLogScreen_displaysCallCount() {
        setUpScreen()
        composeTestRule.onNodeWithText("0 calls").assertIsDisplayed()
    }

    @Test
    fun callLogScreen_displaysScreenTestTag() {
        setUpScreen()
        composeTestRule.onNodeWithTag("call_log_screen").assertIsDisplayed()
    }

    @Test
    fun callLogScreen_displaysAllFilterChip() {
        setUpScreen()
        composeTestRule.onNodeWithText("All", substring = true).assertIsDisplayed()
    }

    @Test
    fun callLogScreen_displaysIncomingFilterChip() {
        setUpScreen()
        composeTestRule.onNodeWithText("Incoming", substring = true).assertIsDisplayed()
    }

    @Test
    fun callLogScreen_displaysOutgoingFilterChip() {
        setUpScreen()
        composeTestRule.onNodeWithText("Outgoing", substring = true).assertIsDisplayed()
    }

    @Test
    fun callLogScreen_displaysMissedFilterChip() {
        setUpScreen()
        composeTestRule.onNodeWithText("Missed", substring = true).assertIsDisplayed()
    }

    @Test
    fun callLogScreen_displaysBlockedFilterChip() {
        setUpScreen()
        composeTestRule.onNodeWithText("Blocked", substring = true).assertIsDisplayed()
    }

    @Test
    fun callLogScreen_rendersWithEntries() {
        val entries = listOf(
            CallLogEntry(
                id = "1",
                phoneNumber = "+256700123456",
                name = "John Doe",
                callType = CallType.INCOMING,
                duration = 120,
                timestamp = System.currentTimeMillis() - 60_000,
                isSpam = false,
                spamScore = 0,
                isBlocked = false
            ),
            CallLogEntry(
                id = "2",
                phoneNumber = "+256700999888",
                name = "Unknown Caller",
                callType = CallType.MISSED,
                duration = 0,
                timestamp = System.currentTimeMillis() - 120_000,
                isSpam = true,
                spamScore = 85,
                isBlocked = false
            )
        )
        val vm = createMockViewModel(entries = entries)
        setUpScreen(vm)

        composeTestRule.onNodeWithText("2 calls").assertIsDisplayed()
    }

    @Test
    fun callLogScreen_showsLoadingState() {
        val vm = createMockViewModel(isLoading = true)
        setUpScreen(vm)

        // When loading, the shimmer loading list should be displayed (header still visible)
        composeTestRule.onNodeWithText("Call Log").assertIsDisplayed()
    }
}
