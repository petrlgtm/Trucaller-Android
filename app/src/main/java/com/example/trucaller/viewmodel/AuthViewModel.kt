package com.example.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.trucaller.TruCallerApplication
import com.example.trucaller.data.model.AdminUser
import com.example.trucaller.data.model.AuthState
import com.example.trucaller.data.model.Contact
import com.example.trucaller.data.model.User
import com.example.trucaller.data.preferences.UserPreferences
import com.example.trucaller.data.repository.ContactRepository
import com.example.trucaller.data.repository.DeviceRepository
import com.example.trucaller.data.repository.UserRepository
import com.example.trucaller.service.DeviceRegistrationService
import com.example.trucaller.service.FirebasePhoneAuthManager
import com.example.trucaller.util.hashPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                        token = "session-$userId"
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
            token = "session-${updated.id}"
        )

        // Register/update device with real info and log IP
        viewModelScope.launch {
            try {
                deviceRegistrationService.registerOrUpdateDevice(updated.id)
            } catch (_: Exception) { }
        }
        return true
    }

    suspend fun adminLogin(email: String, password: String): Boolean {
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
            generatedOtp = otp
        )
        return true
    }

    fun getGeneratedOtp(): String? = _authState.value.generatedOtp

    fun isFirebaseAvailable(): Boolean = phoneAuthManager.isFirebaseAvailable()

    suspend fun verifyOtp(code: String, firebaseVerified: Boolean = false): Boolean {
        val state = _authState.value

        if (!firebaseVerified) {
            // Demo mode: check against generated OTP
            val expectedOtp = state.generatedOtp ?: return false
            if (code != expectedOtp) return false
        }
        // If firebaseVerified is true, Firebase already validated the code

        val pendingPhone = state.pendingPhone ?: return false
        val fullName = state.pendingFullName ?: "New User"
        val passwordHash = state.pendingPasswordHash ?: return false

        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val newUser = User(
            id = "usr-${System.currentTimeMillis()}",
            fullName = fullName,
            phoneNumber = "+256$pendingPhone",
            passwordHash = passwordHash,
            createdAt = now,
            lastLogin = now,
            isActive = true
        )
        userRepository.insertUser(newUser)
        preferences.setLoggedInUserId(newUser.id)
        preferences.setConsentGiven(true)

        // Auto-sync: add user's own number to central contacts drive
        // This ensures their true name is discoverable by other users
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
            token = "session-${newUser.id}"
        )

        // Register device with real info and log IP
        viewModelScope.launch {
            try {
                deviceRegistrationService.registerOrUpdateDevice(newUser.id)
            } catch (_: Exception) { }
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

    fun logout() {
        viewModelScope.launch {
            preferences.setLoggedInUserId(null)
        }
        _authState.value = AuthState()
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
