package com.byron.trucaller.viewmodel

import android.app.Application
import com.byron.trucaller.MainDispatcherRule
import com.byron.trucaller.data.model.AuthState
import com.byron.trucaller.data.model.TrustLevel
import com.byron.trucaller.data.model.User
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.data.repository.ContactRepository
import com.byron.trucaller.data.repository.UserRepository
import com.byron.trucaller.service.DeviceRegistrationService
import com.byron.trucaller.util.hashPassword
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Unit tests for [AuthViewModel].
 *
 * Tests login states, registration flow, password management,
 * OTP verification, and validation logic using mocked repositories.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var userRepository: UserRepository
    private lateinit var preferences: UserPreferences
    private lateinit var contactRepository: ContactRepository
    private lateinit var deviceRegistrationService: DeviceRegistrationService
    private lateinit var viewModel: AuthViewModel

    private val testUser = User(
        id = "user-001",
        fullName = "Test User",
        phoneNumber = "+256712345678",
        email = "test@example.com",
        passwordHash = hashPassword("password123"),
        createdAt = "2026-01-01T00:00:00Z",
        lastLogin = "2026-03-01T10:00:00Z",
        isActive = true,
        trustScore = 50,
        trustLevel = TrustLevel.TRUSTED
    )

    @Before
    fun setup() {
        application = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        contactRepository = mockk(relaxed = true)
        deviceRegistrationService = mockk(relaxed = true)

        // Stubs for init block
        every { preferences.loggedInUserId } returns flowOf(null)
        every { preferences.adminId } returns flowOf(null)

        viewModel = AuthViewModel(
            application,
            userRepository,
            preferences,
            contactRepository,
            deviceRegistrationService
        )
    }

    // ── Initial state ───────────────────────────────────────────────────

    @Test
    fun `initial authState is unauthenticated`() {
        val state = viewModel.authState.value
        assertFalse(state.isAuthenticated)
        assertNull(state.user)
        assertNull(state.token)
        assertFalse(state.isLoading)
    }

    @Test
    fun `initial adminUser is null`() {
        assertNull(viewModel.adminUser.value)
    }

    // ── Init with saved session ─────────────────────────────────────────

    @Test
    fun `init restores session when userId is saved in preferences`() = runTest {
        every { preferences.loggedInUserId } returns flowOf("user-001")
        every { preferences.adminId } returns flowOf(null)
        coEvery { userRepository.getUserById("user-001") } returns testUser

        // Recreate ViewModel to trigger init with saved preferences
        viewModel = AuthViewModel(
            application, userRepository, preferences, contactRepository, deviceRegistrationService
        )
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertTrue(state.isAuthenticated)
        assertNotNull(state.user)
        assertEquals("Test User", state.user!!.fullName)
    }

    @Test
    fun `init does not authenticate when saved userId has no matching user`() = runTest {
        every { preferences.loggedInUserId } returns flowOf("user-missing")
        every { preferences.adminId } returns flowOf(null)
        coEvery { userRepository.getUserById("user-missing") } returns null

        viewModel = AuthViewModel(
            application, userRepository, preferences, contactRepository, deviceRegistrationService
        )
        advanceUntilIdle()

        assertFalse(viewModel.authState.value.isAuthenticated)
    }

    // ── Login (local fallback) ──────────────────────────────────────────

    @Test
    fun `login succeeds with correct credentials via local fallback`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser

        val result = viewModel.login("712345678", "password123")

        assertTrue(result)
        assertTrue(viewModel.authState.value.isAuthenticated)
        assertEquals("Test User", viewModel.authState.value.user!!.fullName)
    }

    @Test
    fun `login fails with wrong password`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser

        val result = viewModel.login("712345678", "wrongpassword")

        assertFalse(result)
        assertFalse(viewModel.authState.value.isAuthenticated)
    }

    @Test
    fun `login fails with non-existent phone number`() = runTest {
        coEvery { userRepository.getUserByPhone("+256799999999") } returns null

        val result = viewModel.login("799999999", "password123")

        assertFalse(result)
        assertFalse(viewModel.authState.value.isAuthenticated)
    }

    @Test
    fun `login updates lastLogin and saves userId to preferences`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser

        viewModel.login("712345678", "password123")

        coVerify { userRepository.updateUser(match { it.lastLogin != testUser.lastLogin }) }
        coVerify { preferences.setLoggedInUserId("user-001") }
    }

    @Test
    fun `login prepends +256 to phone number`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser

        viewModel.login("712345678", "password123")

        coVerify { userRepository.getUserByPhone("+256712345678") }
    }

    // ── Registration ────────────────────────────────────────────────────

    @Test
    fun `register stores pending data and generates OTP`() {
        val result = viewModel.register("New User", "712345678", "securepass")

        assertTrue(result)

        val state = viewModel.authState.value
        assertEquals("712345678", state.pendingPhone)
        assertEquals("New User", state.pendingFullName)
        assertNotNull(state.pendingPasswordHash)
        assertEquals(hashPassword("securepass"), state.pendingPasswordHash)
    }

    @Test
    fun `register generates 6-digit OTP`() {
        viewModel.register("New User", "712345678", "securepass")

        val otp = viewModel.getGeneratedOtp()
        assertNotNull(otp)
        assertEquals(6, otp!!.length)
        assertTrue(otp.all { it.isDigit() })
    }

    @Test
    fun `getGeneratedOtp returns OTP from register`() {
        viewModel.register("User", "712345678", "pass123")

        val otp = viewModel.getGeneratedOtp()
        assertNotNull(otp)
    }

    // ── OTP Verification ────────────────────────────────────────────────

    @Test
    fun `verifyOtp succeeds with correct OTP code`() = runTest {
        viewModel.register("New User", "712345678", "securepass")
        val otp = viewModel.getGeneratedOtp()!!

        val result = viewModel.verifyOtp(otp)

        assertTrue(result)
        assertTrue(viewModel.authState.value.isAuthenticated)
        assertEquals("New User", viewModel.authState.value.user!!.fullName)
    }

    @Test
    fun `verifyOtp fails with wrong OTP code`() = runTest {
        viewModel.register("New User", "712345678", "securepass")

        val result = viewModel.verifyOtp("000000")

        assertFalse(result)
        assertFalse(viewModel.authState.value.isAuthenticated)
    }

    @Test
    fun `verifyOtp fails when no registration is pending`() = runTest {
        val result = viewModel.verifyOtp("123456")

        assertFalse(result)
    }

    @Test
    fun `verifyOtp creates user in repository`() = runTest {
        viewModel.register("New User", "712345678", "securepass")
        val otp = viewModel.getGeneratedOtp()!!

        viewModel.verifyOtp(otp)

        coVerify { userRepository.insertUser(match {
            it.fullName == "New User" &&
            it.phoneNumber == "+256712345678" &&
            it.isActive
        }) }
    }

    @Test
    fun `verifyOtp creates self-contact for new user`() = runTest {
        viewModel.register("New User", "712345678", "securepass")
        val otp = viewModel.getGeneratedOtp()!!

        viewModel.verifyOtp(otp)

        coVerify { contactRepository.insertContact(match {
            it.name == "New User" &&
            it.phoneNumber == "+256712345678" &&
            it.isBackedUp
        }) }
    }

    @Test
    fun `verifyOtp clears pending state after success`() = runTest {
        viewModel.register("New User", "712345678", "securepass")
        val otp = viewModel.getGeneratedOtp()!!

        viewModel.verifyOtp(otp)

        val state = viewModel.authState.value
        assertNull(state.pendingPhone)
        assertNull(state.pendingFullName)
        assertNull(state.pendingPasswordHash)
        assertNull(state.generatedOtp)
    }

    @Test
    fun `verifyOtp with firebaseVerified bypasses OTP check`() = runTest {
        viewModel.register("New User", "712345678", "securepass")

        val result = viewModel.verifyOtp("any-code", firebaseVerified = true)

        assertTrue(result)
        assertTrue(viewModel.authState.value.isAuthenticated)
    }

    // ── Password management ─────────────────────────────────────────────

    @Test
    fun `changePassword succeeds with correct current password`() = runTest {
        // Login first to set current user
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser
        viewModel.login("712345678", "password123")

        val result = viewModel.changePassword("password123", "newpassword")

        assertTrue(result)
        coVerify { userRepository.updateUser(match {
            it.passwordHash == hashPassword("newpassword")
        }) }
    }

    @Test
    fun `changePassword fails with wrong current password`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser
        viewModel.login("712345678", "password123")

        val result = viewModel.changePassword("wrongcurrent", "newpassword")

        assertFalse(result)
    }

    @Test
    fun `changePassword fails when no user is logged in`() = runTest {
        val result = viewModel.changePassword("current", "newpass")

        assertFalse(result)
    }

    @Test
    fun `resetPassword updates password for existing user`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser

        val result = viewModel.resetPassword("712345678", "newpassword")

        assertTrue(result)
        coVerify { userRepository.updateUser(match {
            it.passwordHash == hashPassword("newpassword")
        }) }
    }

    @Test
    fun `resetPassword fails for non-existent phone`() = runTest {
        coEvery { userRepository.getUserByPhone("+256799999999") } returns null

        val result = viewModel.resetPassword("799999999", "newpassword")

        assertFalse(result)
    }

    // ── Password verification ───────────────────────────────────────────

    @Test
    fun `verifyPassword returns true for correct password`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser
        viewModel.login("712345678", "password123")

        assertTrue(viewModel.verifyPassword("password123"))
    }

    @Test
    fun `verifyPassword returns false for wrong password`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser
        viewModel.login("712345678", "password123")

        assertFalse(viewModel.verifyPassword("wrongpassword"))
    }

    @Test
    fun `verifyPassword returns false when no user is logged in`() {
        assertFalse(viewModel.verifyPassword("anypassword"))
    }

    // ── Security PIN ────────────────────────────────────────────────────

    @Test
    fun `setSecurityPin stores hashed pin`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser
        viewModel.login("712345678", "password123")

        val result = viewModel.setSecurityPin("1234")

        assertTrue(result)
        coVerify { userRepository.updateUser(match {
            it.securityPin == hashPassword("1234")
        }) }
    }

    @Test
    fun `setSecurityPin fails when no user is logged in`() = runTest {
        val result = viewModel.setSecurityPin("1234")

        assertFalse(result)
    }

    @Test
    fun `verifySecurityPin returns true for correct pin`() = runTest {
        val userWithPin = testUser.copy(securityPin = hashPassword("1234"))
        coEvery { userRepository.getUserByPhone("+256712345678") } returns userWithPin
        viewModel.login("712345678", "password123")

        // After login, the user in authState has the pin from the update
        // We need to set the pin first via setSecurityPin
        viewModel.setSecurityPin("1234")
        advanceUntilIdle()

        assertTrue(viewModel.verifySecurityPin("1234"))
    }

    @Test
    fun `verifySecurityPin returns false for wrong pin`() = runTest {
        val userWithPin = testUser.copy(securityPin = hashPassword("1234"))
        coEvery { userRepository.getUserByPhone("+256712345678") } returns userWithPin
        viewModel.login("712345678", "password123")
        viewModel.setSecurityPin("1234")
        advanceUntilIdle()

        assertFalse(viewModel.verifySecurityPin("5678"))
    }

    @Test
    fun `hasSecurityPin returns false when no pin is set`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser
        viewModel.login("712345678", "password123")

        assertFalse(viewModel.hasSecurityPin())
    }

    @Test
    fun `hasSecurityPin returns true after setting pin`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser
        viewModel.login("712345678", "password123")
        viewModel.setSecurityPin("1234")
        advanceUntilIdle()

        assertTrue(viewModel.hasSecurityPin())
    }

    // ── Password reset OTP ──────────────────────────────────────────────

    @Test
    fun `requestPasswordReset succeeds for existing user`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser

        val result = viewModel.requestPasswordReset("712345678")

        assertTrue(result)
        assertNotNull(viewModel.getResetOtp())
    }

    @Test
    fun `requestPasswordReset fails for non-existent user`() = runTest {
        coEvery { userRepository.getUserByPhone("+256799999999") } returns null

        val result = viewModel.requestPasswordReset("799999999")

        assertFalse(result)
    }

    @Test
    fun `verifyResetOtp succeeds with correct OTP`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser
        viewModel.requestPasswordReset("712345678")

        val otp = viewModel.getResetOtp()!!
        assertTrue(viewModel.verifyResetOtp(otp))
    }

    @Test
    fun `verifyResetOtp fails with wrong OTP`() = runTest {
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser
        viewModel.requestPasswordReset("712345678")

        assertFalse(viewModel.verifyResetOtp("000000"))
    }

    @Test
    fun `verifyResetOtp fails when no reset was requested`() {
        assertFalse(viewModel.verifyResetOtp("123456"))
    }

    // ── Logout ──────────────────────────────────────────────────────────

    @Test
    fun `logout clears auth state and preferences`() = runTest {
        // Login first
        coEvery { userRepository.getUserByPhone("+256712345678") } returns testUser
        viewModel.login("712345678", "password123")
        assertTrue(viewModel.authState.value.isAuthenticated)

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.authState.value.isAuthenticated)
        assertNull(viewModel.authState.value.user)
        coVerify { preferences.setLoggedInUserId(null) }
    }

    // ── Admin login ─────────────────────────────────────────────────────

    @Test
    fun `adminLogout clears admin state`() = runTest {
        viewModel.adminLogout()
        advanceUntilIdle()

        assertNull(viewModel.adminUser.value)
        coVerify { preferences.setAdminId(null) }
    }
}
