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
import com.byron.trucaller.service.FirebasePhoneAuthManager
import android.net.Uri
import com.byron.trucaller.util.copyImageToInternal
import com.byron.trucaller.util.hashPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    val phoneAuthManager = FirebasePhoneAuthManager()

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _adminUser = MutableStateFlow<AdminUser?>(null)
    val adminUser: StateFlow<AdminUser?> = _adminUser.asStateFlow()

    private val _resetOtp = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val userId = preferences.loggedInUserId.first()
            if (userId != null) {
                val user = userRepository.getUserById(userId)
                if (user != null) {
                    _authState.value = AuthState(
                        user = user,
                        isAuthenticated = true,
                        token = UUID.randomUUID().toString()
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

    suspend fun login(phone: String, password: String): Boolean {
        val fullPhone = "+256$phone"

        // Try backend API login first
        try {
            val apiResult = ApiClient.login(fullPhone, password)
            if (apiResult.success && apiResult.data != null) {
                val tokenData = apiResult.data
                ApiClient.setAuthToken(tokenData.token)

                // Store or update user locally
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                val existingUser = userRepository.getUserByPhone(fullPhone)
                val user = existingUser?.copy(lastLogin = now) ?: User(
                    id = tokenData.userId,
                    fullName = fullPhone,
                    phoneNumber = fullPhone,
                    passwordHash = hashPassword(password),
                    createdAt = now,
                    lastLogin = now,
                    isActive = true
                )
                if (existingUser != null) userRepository.updateUser(user) else userRepository.insertUser(user)
                preferences.setLoggedInUserId(user.id)

                _authState.value = AuthState(
                    user = user,
                    isAuthenticated = true,
                    token = tokenData.token
                )

                viewModelScope.launch {
                    try { deviceRegistrationService.registerOrUpdateDevice(user.id) } catch (_: Exception) { }
                    // Sync trust data from backend
                    syncTrustFromBackend(user.id)
                }
                return true
            }
        } catch (_: Exception) { }

        // Fallback: local Room login
        val user = userRepository.getUserByPhone(fullPhone) ?: return false
        if (user.passwordHash != hashPassword(password)) return false

        val updated = user.copy(
            lastLogin = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        )
        userRepository.updateUser(updated)
        preferences.setLoggedInUserId(updated.id)

        _authState.value = AuthState(
            user = updated,
            isAuthenticated = true,
            token = UUID.randomUUID().toString()
        )

        viewModelScope.launch {
            try { deviceRegistrationService.registerOrUpdateDevice(updated.id) } catch (_: Exception) { }
        }
        return true
    }

    suspend fun adminLogin(email: String, password: String): Boolean {
        // Try backend API login first
        try {
            val apiResult = ApiClient.adminLogin(email, password)
            if (apiResult.success && apiResult.data != null) {
                val tokenData = apiResult.data
                ApiClient.setAuthToken(tokenData.token)

                // Also sync with local Room DB
                val admin = userRepository.getAdminByCredentials(email, password)
                if (admin != null) {
                    preferences.setAdminId(admin.id)
                    preferences.setAdminProfile(admin.name, admin.email)
                    _adminUser.value = admin
                }
                return true
            }
        } catch (_: Exception) { }

        // Fallback: local Room login
        val admin = userRepository.getAdminByCredentials(email, password) ?: return false
        preferences.setAdminId(admin.id)
        preferences.setAdminProfile(admin.name, admin.email)
        _adminUser.value = admin
        return true
    }

    fun adminLogout() {
        viewModelScope.launch {
            preferences.setAdminId(null)
        }
        _adminUser.value = null
    }

    fun register(fullName: String, phone: String, password: String): Boolean {
        val otp = (100000..999999).random().toString()
        _authState.value = _authState.value.copy(
            pendingPhone = phone,
            pendingFullName = fullName,
            pendingPasswordHash = hashPassword(password),
            pendingPassword = password,
            generatedOtp = otp
        )
        return true
    }

    fun getGeneratedOtp(): String? = _authState.value.generatedOtp

    fun isFirebaseAvailable(): Boolean = phoneAuthManager.isFirebaseAvailable()

    suspend fun verifyOtp(code: String, firebaseVerified: Boolean = false): Boolean {
        val state = _authState.value

        if (!firebaseVerified) {
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

        // Try backend registration
        try {
            val rawPassword = state.pendingPassword ?: pendingPhone
            val apiResult = ApiClient.register(fullName, fullPhoneNumber, rawPassword)
            if (apiResult.success && apiResult.data != null) {
                userId = apiResult.data.userId
                token = apiResult.data.token
                ApiClient.setAuthToken(token)
            }
        } catch (_: Exception) { }

        val newUser = User(
            id = userId,
            fullName = fullName,
            phoneNumber = fullPhoneNumber,
            passwordHash = passwordHash,
            createdAt = now,
            lastLogin = now,
            isActive = true
        )
        userRepository.insertUser(newUser)
        preferences.setLoggedInUserId(newUser.id)
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
            pendingFullName = null,
            pendingPasswordHash = null,
            pendingPassword = null
        )

        viewModelScope.launch {
            try { deviceRegistrationService.registerOrUpdateDevice(newUser.id) } catch (_: Exception) { }
        }
        return true
    }

    suspend fun requestPasswordReset(phone: String): Boolean {
        val fullPhone = "+256$phone"
        val user = userRepository.getUserByPhone(fullPhone) ?: return false
        _resetOtp.value = (100000..999999).random().toString()
        return true
    }

    fun verifyResetOtp(code: String): Boolean {
        val expected = _resetOtp.value ?: return false
        return code == expected
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
        val user = userRepository.getUserByPhone(fullPhone) ?: return false
        val updated = user.copy(passwordHash = hashPassword(newPassword))
        userRepository.updateUser(updated)
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
            val file = copyImageToInternal(getApplication(), uri, "avatar_${user.id}")
            val updated = user.copy(avatarUrl = file.absolutePath)
            userRepository.updateUser(updated)
            _authState.value = _authState.value.copy(user = updated)
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

    fun logout() {
        viewModelScope.launch {
            preferences.setLoggedInUserId(null)
        }
        phoneAuthManager.signOut()
        _authState.value = AuthState()
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
        phoneAuthManager.signOut()
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
