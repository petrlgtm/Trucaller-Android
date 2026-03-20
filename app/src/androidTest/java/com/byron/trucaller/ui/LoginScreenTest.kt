package com.byron.trucaller.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byron.trucaller.ui.auth.LoginScreen
import com.byron.trucaller.viewmodel.AuthViewModel
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for the Login screen.
 *
 * Tests cover:
 * - Presence of required UI elements (phone input, password input, sign in button)
 * - "Welcome Back" heading is displayed
 * - Navigation links to Register and Admin Login are present
 * - Forgot Password link is present
 *
 * Requires an Android device or emulator to run.
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockAuthViewModel = mockk<AuthViewModel>(relaxed = true)

    private fun setUpScreen() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            LoginScreen(
                navController = navController,
                authViewModel = mockAuthViewModel
            )
        }
        // Wait for entrance animations to complete
        composeTestRule.waitForIdle()
    }

    @Test
    fun loginScreen_displaysWelcomeBackHeading() {
        setUpScreen()
        composeTestRule.onNodeWithText("Welcome Back").assertIsDisplayed()
    }

    @Test
    fun loginScreen_displaysSignInSubtitle() {
        setUpScreen()
        composeTestRule.onNodeWithText("Sign in to your account").assertIsDisplayed()
    }

    @Test
    fun loginScreen_displaysPhoneNumberLabel() {
        setUpScreen()
        composeTestRule.onNodeWithText("Phone Number").assertIsDisplayed()
    }

    @Test
    fun loginScreen_displaysPasswordLabel() {
        setUpScreen()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun loginScreen_displaysSignInButton() {
        setUpScreen()
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun loginScreen_displaysForgotPasswordLink() {
        setUpScreen()
        composeTestRule.onNodeWithText("Forgot Password?").assertIsDisplayed()
    }

    @Test
    fun loginScreen_displaysRegisterLink() {
        setUpScreen()
        composeTestRule.onNodeWithText("Register").assertIsDisplayed()
    }

    @Test
    fun loginScreen_displaysAdminLoginLink() {
        setUpScreen()
        composeTestRule.onNodeWithText("Login as Admin").assertIsDisplayed()
    }

    @Test
    fun loginScreen_phoneInputAcceptsText() {
        setUpScreen()
        composeTestRule.onNodeWithTag("login_phone_input").performTextInput("700123456")
        composeTestRule.waitForIdle()
    }

    @Test
    fun loginScreen_passwordInputAcceptsText() {
        setUpScreen()
        composeTestRule.onNodeWithTag("login_password_input").performTextInput("password123")
        composeTestRule.waitForIdle()
    }

    @Test
    fun loginScreen_displaysCountryPrefix() {
        setUpScreen()
        composeTestRule.onNodeWithText("+256 ", substring = false).assertIsDisplayed()
    }
}
