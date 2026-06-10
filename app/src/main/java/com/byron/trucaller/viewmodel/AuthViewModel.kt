package com.byron.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.AdminUser
import com.byron.trucaller.data.model.AuthState
import com.byron.trucaller.data.model.Contact
import com.byron.trucaller.data.model.TrustLevel
import com.byron.trucaller.data.model.User
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.data.repository.ContactRepository
import com.byron.trucaller.data.repository.DeviceRepository
import com.byron.trucaller.data.repository.UserRepository
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.service.DeviceRegistrationService
import android.net.Uri
import com.byron.trucaller.util.copyImageToInternal
import com.byron.trucaller.util.hashPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AuthViewModel(
    application: Application,
    private val userRepository: UserRepository,
    private val preferences: UserPreferences,
    private val contactRepository: ContactRepository,
    private val deviceRegistrationService: DeviceRegistrationService
) : AndroidViewModel(application) {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _adminUser = MutableStateFlow<AdminUser?>(null)
    val adminUser: StateFlow<AdminUser?> = _adminUser.asStateFlow()

    private val _resetOtp = MutableStateFlow<String?>(null)
    private val _verifiedResetCode = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val userId = preferences.loggedInUserId.first()
            if (userId != null) {
                val user = userRepository.getUserById(userId)
                if (user != null) {
                    // Restore persisted auth token for backend calls
                    val savedToken = try {
                        preferences.authToken.first()
                    } catch (_: Exception) {
                        // Unit tests may not stub authToken; treat as missing.
                        null
                    }
                    if (savedToken != null) ApiClient.setAuthToken(savedToken)
                    // Restore refresh token so automatic JWT renewal works after restart
                    val savedRefresh = try { preferences.refreshToken.first() } catch (_: Exception) { null }
                    if (savedRefresh != null) ApiClient.setRefreshToken(savedRefresh)
                    _authState.value = AuthState(
                        user = user,
                        isAuthenticated = true,
                        token = savedToken ?: ""
                    )
                    // Update device info and log IP on app restart
                    try {
                        deviceRegistrationService.registerOrUpdateDevice(userId)
                    } catch (_: Exception) { }
                }
            }
            val adminId = preferences.adminId.first()
            if (adminId != null) {
                _adminUser.value = userRepository.getAdminById(adminId)
            }
        }
    }

    suspend fun login(phone: String, password: String): Boolean =
        loginWithError(phone, password) == null

    /**
     * Returns null on success, or a user-facing error string on failure.
     * Distinguishes network timeout (Render.com cold-start) from wrong credentials
     * so the UI can ask the user to retry rather than blaming their password.
     */
    suspend fun loginWithError(phone: String, password: String): String? {
        val fullPhone = "+256$phone"

        try {
            val apiResult = ApiClient.login(fullPhone, password)
            if (apiResult.success && apiResult.data != null) {
                val tokenData = apiResult.data
                ApiClient.setAuthToken(tokenData.token)
                preferences.setAuthToken(tokenData.token)
                tokenData.refreshToken?.let {
                    ApiClient.setRefreshToken(it)
                    preferences.setRefreshToken(it)
                }

                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                val existingUser = userRepository.getUserByPhone(fullPhone)
                val user = if (existingUser != null && existingUser.id == tokenData.userId) {
                    existingUser.copy(
                        fullName = tokenData.fullName.ifBlank { existingUser.fullName },
                        lastLogin = now
                    )
                } else {
                    if (existingUser != null) userRepository.deleteUser(existingUser)
                    User(
                        id = tokenData.userId,
                        fullName = tokenData.fullName.ifBlank { fullPhone },
                        phoneNumber = fullPhone,
                        passwordHash = hashPassword(password),
                        createdAt = now,
                        lastLogin = now,
                        isActive = true
                    )
                }
                if (userRepository.getUserById(user.id) != null) userRepository.updateUser(user)
                else userRepository.insertUser(user)
                preferences.setLoggedInUserId(user.id)

                _authState.value = AuthState(user = user, isAuthenticated = true, token = tokenData.token)

                viewModelScope.launch {
                    try { deviceRegistrationService.registerOrUpdateDevice(user.id) } catch (_: Exception) { }
                    syncTrustFromBackend(user.id)
                }
                return null
            }
            // Backend reachable but rejected (wrong credentials) — fall through to local Room check
        } catch (e: SocketTimeoutException) {
            // Server is reachable but took too long (likely Render.com cold-start).
            // Don't fall through to local auth — the user knows their credentials are correct;
            // asking them to retry with a clear message is more helpful than logging them in
            // locally without a JWT (which breaks every subsequent API call).
            return "Server is starting up. Please wait a moment and try again."
        } catch (_: Exception) {
            // No network — fall through to local Room login for offline access
        }

        // Fallback: local Room login (offline mode — no JWT, limited functionality)
        val user = userRepository.getUserByPhone(fullPhone)
            ?: return "Invalid phone number or password"
        if (user.passwordHash != hashPassword(password))
            return "Invalid phone number or password"

        val updated = user.copy(
            lastLogin = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        )
        userRepository.updateUser(updated)
        preferences.setLoggedInUserId(updated.id)
        _authState.value = AuthState(user = updated, isAuthenticated = true, token = "")
        return null
    }

    suspend fun adminLogin(phoneNumber: String, password: String): Boolean {
        // Try backend API login first
        try {
            val apiResult = ApiClient.adminLogin(phoneNumber, password)
            if (apiResult.success && apiResult.data != null) {
                val tokenData = apiResult.data
                ApiClient.setAuthToken(tokenData.token)
                viewModelScope.launch { preferences.setAuthToken(tokenData.token) }

                // Sync with local Room DB, or create a local record from the backend response
                var admin = userRepository.getAdminByPhoneCredentials(phoneNumber, password)
                if (admin == null) {
                    val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                    admin = AdminUser(
                        id = tokenData.userId,
                        name = tokenData.fullName.ifBlank { "Admin" },
                        phoneNumber = phoneNumber,
                        email = "",
                        password = hashPassword(password),
                        role = tokenData.role.ifBlank { "SUPER_ADMIN" },
                        lastLogin = now,
                        createdAt = now
                    )
                    userRepository.insertAdminUser(admin)
                } else {
                    // Sync name and role from backend in case they changed
                    val updated = admin.copy(
                        name = tokenData.fullName.ifBlank { admin.name },
                        role = tokenData.role.ifBlank { admin.role }
                    )
                    userRepository.updateAdmin(updated)
                    admin = updated
                }
                preferences.setAdminId(admin.id)
                preferences.setAdminProfile(admin.name, admin.phoneNumber)
                _adminUser.value = admin
                return true
            }
        } catch (_: Exception) { }

        // Fallback: local Room login by phone
        val admin = userRepository.getAdminByPhoneCredentials(phoneNumber, password) ?: return false
        preferences.setAdminId(admin.id)
        preferences.setAdminProfile(admin.name, admin.phoneNumber)
        _adminUser.value = admin
        return true
    }

    fun adminLogout() {
        viewModelScope.launch {
            preferences.setAdminId(null)
            _adminUser.value = null
        }
    }

    fun register(fullName: String, phone: String, email: String, password: String): Boolean {
        _authState.value = _authState.value.copy(
            pendingPhone = phone,
            pendingEmail = email.trim().lowercase(),
            pendingFullName = fullName,
            pendingPasswordHash = hashPassword(password),
            pendingPassword = password,
            generatedOtp = null
        )
        return true
    }

    fun getGeneratedOtp(): String? = _authState.value.generatedOtp

    /**
     * Sends an OTP to [email] via the backend's Gmail SMTP gateway.
     * Returns null on success, or an error message string on failure.
     */
    suspend fun sendEmailOtp(email: String, purpose: String = "registration"): String? {
        return try {
            val result = ApiClient.sendEmailOtp(email.trim().lowercase(), purpose)
            if (result.success) null else (result.error ?: "Failed to send verification email")
        } catch (e: Exception) {
            e.message ?: "Network error"
        }
    }

    /** Verifies [code] against the backend email OTP store. Does NOT create the account. */
    suspend fun verifyEmailOtpViaBackend(email: String, code: String): Boolean {
        return try {
            ApiClient.verifyEmailOtp(email.trim().lowercase(), code).success
        } catch (_: Exception) { false }
    }

    suspend fun verifyOtp(code: String, firebaseVerified: Boolean = false, backendVerified: Boolean = false): Boolean {
        val state = _authState.value

        if (!firebaseVerified && !backendVerified) {
            val expectedOtp = state.generatedOtp ?: return false
            if (code != expectedOtp) return false
        }

        val pendingPhone = state.pendingPhone ?: return false
        val fullName = state.pendingFullName ?: "New User"
        val passwordHash = state.pendingPasswordHash ?: return false
        val fullPhoneNumber = "+256$pendingPhone"

        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        var userId = "usr-${System.currentTimeMillis()}"
        var token = UUID.randomUUID().toString()

        val pendingEmail = state.pendingEmail ?: ""

        // Try backend registration
        try {
            val rawPassword = state.pendingPassword ?: pendingPhone
            val apiResult = ApiClient.register(fullName, fullPhoneNumber, rawPassword, pendingEmail)
            if (apiResult.success && apiResult.data != null) {
                userId = apiResult.data.userId
                token = apiResult.data.token
                ApiClient.setAuthToken(token)
                apiResult.data.refreshToken?.let {
                    ApiClient.setRefreshToken(it)
                    preferences.setRefreshToken(it)
                }
            }
        } catch (_: Exception) { }

        val newUser = User(
            id = userId,
            fullName = fullName,
            phoneNumber = fullPhoneNumber,
            email = pendingEmail.ifBlank { null },
            passwordHash = passwordHash,
            createdAt = now,
            lastLogin = now,
            isActive = true
        )
        userRepository.insertUser(newUser)
        preferences.setLoggedInUserId(newUser.id)
        preferences.setAuthToken(token)
        preferences.setConsentGiven(true)

        val selfContact = Contact(
            id = "cnt-self-${newUser.id}",
            userId = newUser.id,
            name = newUser.fullName,
            phoneNumber = newUser.phoneNumber,
            syncedAt = now,
            isBackedUp = true
        )
        contactRepository.insertContact(selfContact)

        _authState.value = AuthState(
            user = newUser,
            isAuthenticated = true,
            token = token,
            generatedOtp = null,
            pendingPhone = null,
            pendingEmail = null,
            pendingFullName = null,
            pendingPasswordHash = null,
            pendingPassword = null
        )

        viewModelScope.launch {
            try { deviceRegistrationService.registerOrUpdateDevice(newUser.id) } catch (_: Exception) { }
        }
        return true
    }

    suspend fun requestPasswordReset(phone: String): Boolean =
        requestPasswordResetWithError(phone) == null

    /**
     * Requests a password-reset OTP. Returns null on success, or a user-facing
     * error string on failure. Distinguishes "no account found", "no email linked",
     * and generic network failures so the UI can show the right message.
     */
    suspend fun requestPasswordResetWithError(phone: String): String? {
        val fullPhone = "+256$phone"
        return try {
            val result = ApiClient.sendOtp(fullPhone, "password_reset")
            when {
                result.success -> null
                result.error?.contains("No email address linked", ignoreCase = true) == true ->
                    "This account has no email linked. Please contact support to reset your password."
                result.error?.contains("not found", ignoreCase = true) == true ->
                    "No account found for that phone number."
                else -> {
                    // Service or network error — try local fallback so offline users can still reset
                    val user = userRepository.getUserByPhone(fullPhone)
                        ?: return "No account found for that phone number."
                    _resetOtp.value = (100000..999999).random().toString()
                    null
                }
            }
        } catch (_: Exception) {
            // Network unreachable — offline fallback
            val user = userRepository.getUserByPhone(fullPhone)
                ?: return "Unable to connect. Please check your internet and try again."
            _resetOtp.value = (100000..999999).random().toString()
            null
        }
    }

    suspend fun verifyResetOtp(phone: String, code: String): Boolean {
        val fullPhone = "+256$phone"
        return try {
            val result = ApiClient.verifyOtp(fullPhone, code)
            if (result.success) _verifiedResetCode.value = code
            result.success
        } catch (_: Exception) {
            // Offline fallback against locally-generated OTP
            val expected = _resetOtp.value ?: return false
            (code == expected).also { if (it) _verifiedResetCode.value = code }
        }
    }

    fun getResetOtp(): String? = _resetOtp.value

    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean {
        val user = _authState.value.user ?: return false
        if (user.passwordHash != hashPassword(currentPassword)) return false
        val updated = user.copy(passwordHash = hashPassword(newPassword))
        userRepository.updateUser(updated)
        _authState.value = _authState.value.copy(user = updated)
        return true
    }

    suspend fun resetPassword(phone: String, newPassword: String): Boolean {
        val fullPhone = "+256$phone"
        val code = _verifiedResetCode.value
        if (code != null) {
            try {
                val result = ApiClient.resetPassword(fullPhone, code, newPassword)
                if (result.success) {
                    _verifiedResetCode.value = null
                    _resetOtp.value = null
                    userRepository.getUserByPhone(fullPhone)?.let { user ->
                        userRepository.updateUser(user.copy(passwordHash = hashPassword(newPassword)))
                    }
                    return true
                }
            } catch (_: Exception) { }
        }
        // Local fallback (offline or backend error)
        val user = userRepository.getUserByPhone(fullPhone) ?: return false
        userRepository.updateUser(user.copy(passwordHash = hashPassword(newPassword)))
        return true
    }

    fun verifyPassword(password: String): Boolean {
        val user = _authState.value.user ?: return false
        return user.passwordHash == hashPassword(password)
    }

    suspend fun setSecurityPin(pin: String): Boolean {
        val user = _authState.value.user ?: return false
        val updated = user.copy(securityPin = hashPassword(pin))
        userRepository.updateUser(updated)
        _authState.value = _authState.value.copy(user = updated)
        return true
    }

    fun verifySecurityPin(pin: String): Boolean {
        val user = _authState.value.user ?: return false
        val storedPin = user.securityPin ?: return false
        return storedPin == hashPassword(pin)
    }

    fun hasSecurityPin(): Boolean {
        return _authState.value.user?.securityPin != null
    }

    suspend fun isDeviceProtectionPromptShown(): Boolean {
        return preferences.deviceProtectionPromptShown.first()
    }

    suspend fun setDeviceProtectionPromptShown(shown: Boolean) {
        preferences.setDeviceProtectionPromptShown(shown)
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            val user = _authState.value.user ?: return@launch
            try {
                val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    copyImageToInternal(getApplication(), uri, "avatar_${user.id}")
                }
                val updated = user.copy(avatarUrl = file.absolutePath)
                userRepository.updateUser(updated)
                _authState.value = _authState.value.copy(user = updated)
            } catch (_: Exception) {
                // Image copy failed — leave avatar unchanged
            }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            val user = _authState.value.user ?: return@launch
            user.avatarUrl?.let { path -> java.io.File(path).delete() }
            val updated = user.copy(avatarUrl = null)
            userRepository.updateUser(updated)
            _authState.value = _authState.value.copy(user = updated)
        }
    }

    fun updateFullName(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val user = _authState.value.user ?: return@launch
            val updated = user.copy(fullName = newName)
            userRepository.updateUser(updated)
            _authState.value = _authState.value.copy(user = updated)
            
            // Sync to backend
            try {
                ApiClient.updateProfile(newName)
            } catch (_: Exception) {}
        }
    }

    fun logout() {
        viewModelScope.launch {
            val refreshToken = ApiClient.getAuthToken()?.let { preferences.refreshToken.first() }
            try { ApiClient.logout(refreshToken) } catch (_: Exception) { }
            preferences.setLoggedInUserId(null)
            preferences.setAuthToken(null)
            preferences.setRefreshToken(null)
            ApiClient.setAuthToken(null)
            ApiClient.setRefreshToken(null)
            _authState.value = AuthState()
        }
    }

    /**
     * Permanently deletes the user's account on the backend and clears all local data.
     * Returns true if the account was successfully deleted (or backend was unreachable
     * and local cleanup proceeded), false if the user is not authenticated.
     */
    suspend fun deleteAccount(): Boolean {
        val user = _authState.value.user ?: return false

        // Attempt backend deletion
        try {
            val result = ApiClient.deleteAccount()
            if (!result.success) {
                // If backend explicitly rejects (not a network error), abort
                if (result.error != null && !result.error.contains("Network error")) {
                    return false
                }
            }
        } catch (_: Exception) {
            // Network failure — proceed with local cleanup anyway
        }

        // Clear local data
        userRepository.deleteUser(user)
        preferences.setLoggedInUserId(null)
        _authState.value = AuthState()
        return true
    }

    /** Syncs trust score/level from the backend and updates local state. */
    private suspend fun syncTrustFromBackend(userId: String) {
        try {
            val result = ApiClient.getUserTrust(userId)
            if (result.success && result.data != null) {
                val trustScore = (result.data["trustScore"] as? Number)?.toInt() ?: return
                val trustLevelStr = result.data["trustLevel"]?.toString() ?: return
                val trustLevel = try {
                    TrustLevel.valueOf(trustLevelStr)
                } catch (_: IllegalArgumentException) {
                    TrustLevel.NEW
                }

                val currentUser = _authState.value.user ?: return
                if (currentUser.trustScore != trustScore || currentUser.trustLevel != trustLevel) {
                    val updated = currentUser.copy(trustScore = trustScore, trustLevel = trustLevel)
                    userRepository.updateUser(updated)
                    _authState.value = _authState.value.copy(user = updated)
                }
            }
        } catch (_: Exception) {
            // Silently ignore — trust sync is best-effort
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                val deviceRegService = DeviceRegistrationService(app, app.container.deviceRepository)
                AuthViewModel(
                    app,
                    app.container.userRepository,
                    app.container.userPreferences,
                    app.container.contactRepository,
                    deviceRegService
                )
            }
        }
    }
}
