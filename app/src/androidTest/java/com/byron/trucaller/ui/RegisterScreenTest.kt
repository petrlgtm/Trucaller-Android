package com.byron.trucaller.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byron.trucaller.ui.auth.RegisterScreen
import com.byron.trucaller.viewmodel.AuthViewModel
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for the Register screen.
 *
 * Tests cover:
 * - Presence of required UI elements (name, phone, password, confirm password inputs)
 * - "Create Account" heading and button displayed
 * - Terms of service consent checkbox present
 * - Input fields accept text
 *
 * Requires an Android device or emulator to run.
 */
@RunWith(AndroidJUnit4::class)
class RegisterScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockAuthViewModel = mockk<AuthViewModel>(relaxed = true)

    private fun setUpScreen() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            RegisterScreen(
                navController = navController,
                authViewModel = mockAuthViewModel
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun registerScreen_displaysCreateAccountTitle() {
        setUpScreen()
        composeTestRule.onNodeWithText("Create Account").assertIsDisplayed()
    }

    @Test
    fun registerScreen_displaysFullNameInput() {
        setUpScreen()
        composeTestRule.onNodeWithText("Full Name").assertIsDisplayed()
    }

    @Test
    fun registerScreen_displaysPhoneNumberInput() {
        setUpScreen()
        composeTestRule.onNodeWithText("Phone Number").assertIsDisplayed()
    }

    @Test
    fun registerScreen_displaysPasswordInput() {
        setUpScreen()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun registerScreen_displaysConfirmPasswordInput() {
        setUpScreen()
        composeTestRule.onNodeWithText("Confirm Password").assertIsDisplayed()
    }

    @Test
    fun registerScreen_displaysTermsOfServiceCheckbox() {
        setUpScreen()
        composeTestRule.onNodeWithText("I agree to the Terms of Service").assertIsDisplayed()
    }

    @Test
    fun registerScreen_displaysConsentText() {
        setUpScreen()
        composeTestRule.onNodeWithText(
            "By creating an account",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun registerScreen_nameInputAcceptsText() {
        setUpScreen()
        composeTestRule.onNodeWithTag("register_name_input").performTextInput("John Doe")
        composeTestRule.waitForIdle()
    }

    @Test
    fun registerScreen_phoneInputAcceptsText() {
        setUpScreen()
        composeTestRule.onNodeWithTag("register_phone_input").performTextInput("700123456")
        composeTestRule.waitForIdle()
    }

    @Test
    fun registerScreen_passwordInputAcceptsText() {
        setUpScreen()
        composeTestRule.onNodeWithTag("register_password_input").performTextInput("password123")
        composeTestRule.waitForIdle()
    }

    @Test
    fun registerScreen_confirmPasswordInputAcceptsText() {
        setUpScreen()
        composeTestRule.onNodeWithTag("register_confirm_password_input").performTextInput("password123")
        composeTestRule.waitForIdle()
    }

    @Test
    fun registerScreen_displaysCountryPrefix() {
        setUpScreen()
        composeTestRule.onNodeWithText("+256 ", substring = false).assertIsDisplayed()
    }

    @Test
    fun registerScreen_displaysBackNavigationIcon() {
        setUpScreen()
        composeTestRule.onNodeWithText("Back", useUnmergedTree = true).assertExists()
    }
}
