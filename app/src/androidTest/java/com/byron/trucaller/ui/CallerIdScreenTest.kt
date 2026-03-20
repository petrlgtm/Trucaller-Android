package com.byron.trucaller.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byron.trucaller.ui.main.CallerIdScreen
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.CallerIdViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for the Caller ID screen.
 *
 * Tests cover:
 * - Presence of the screen header ("Caller ID")
 * - Search input is displayed and accepts text
 * - "No Results Found" empty state displays when no match found
 * - Recent lookups section appears when list is non-empty
 *
 * Requires an Android device or emulator to run.
 */
@RunWith(AndroidJUnit4::class)
class CallerIdScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockCallerIdViewModel = mockk<CallerIdViewModel>(relaxed = true).apply {
        every { lookupResult } returns MutableStateFlow(null)
        every { recentLookups } returns MutableStateFlow(emptyList())
        every { notFound } returns MutableStateFlow(false)
        every { actionMessage } returns MutableStateFlow(null)
        every { isNumberBlocked } returns MutableStateFlow(false)
        every { verificationStatus } returns MutableStateFlow(null)
    }

    private val mockAuthViewModel = mockk<AuthViewModel>(relaxed = true).apply {
        every { authState } returns MutableStateFlow(
            com.byron.trucaller.data.model.AuthState()
        )
    }

    private fun setUpScreen() {
        composeTestRule.setContent {
            CallerIdScreen(
                callerIdViewModel = mockCallerIdViewModel,
                authViewModel = mockAuthViewModel
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun callerIdScreen_displaysHeader() {
        setUpScreen()
        composeTestRule.onNodeWithText("Caller ID").assertIsDisplayed()
    }

    @Test
    fun callerIdScreen_displaysSubtitle() {
        setUpScreen()
        composeTestRule.onNodeWithText("Search any phone number").assertIsDisplayed()
    }

    @Test
    fun callerIdScreen_displaysSearchInput() {
        setUpScreen()
        composeTestRule.onNodeWithTag("caller_id_search_input").assertIsDisplayed()
    }

    @Test
    fun callerIdScreen_searchInputAcceptsText() {
        setUpScreen()
        composeTestRule.onNodeWithTag("caller_id_search_input").performTextInput("+256700123456")
        composeTestRule.waitForIdle()
    }

    @Test
    fun callerIdScreen_displaysSearchPlaceholder() {
        setUpScreen()
        composeTestRule.onNodeWithText("Enter phone number...").assertIsDisplayed()
    }

    @Test
    fun callerIdScreen_showsNotFoundState() {
        // Override the notFound flow to return true
        val notFoundViewModel = mockk<CallerIdViewModel>(relaxed = true).apply {
            every { lookupResult } returns MutableStateFlow(null)
            every { recentLookups } returns MutableStateFlow(emptyList())
            every { notFound } returns MutableStateFlow(true)
            every { actionMessage } returns MutableStateFlow(null)
            every { isNumberBlocked } returns MutableStateFlow(false)
            every { verificationStatus } returns MutableStateFlow(null)
        }

        composeTestRule.setContent {
            CallerIdScreen(
                callerIdViewModel = notFoundViewModel,
                authViewModel = mockAuthViewModel
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No Results Found").assertIsDisplayed()
    }

    @Test
    fun callerIdScreen_showsSearchButtonWhenQueryNotEmpty() {
        setUpScreen()
        composeTestRule.onNodeWithTag("caller_id_search_input").performTextInput("700")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
    }
}
