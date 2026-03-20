package com.byron.trucaller.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.data.repository.UserRepository
import com.byron.trucaller.service.ApiClient
import com.byron.trucaller.util.hashPassword
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Success(val message: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

class AdminSettingsViewModel(
    application: Application,
    private val userRepository: UserRepository,
    private val preferences: UserPreferences
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AdminSettingsVM"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                AdminSettingsViewModel(app, app.container.userRepository, app.container.userPreferences)
            }
        }
    }

    val adminName: Flow<String> = preferences.adminName
    val adminEmail: Flow<String> = preferences.adminEmail
    val stolenReportNotif: Flow<Boolean> = preferences.stolenReportNotif
    val alarmNotif: Flow<Boolean> = preferences.alarmNotif
    val dailyDigest: Flow<Boolean> = preferences.dailyDigest
    val weeklyReport: Flow<Boolean> = preferences.weeklyReport

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    fun saveProfile(name: String, email: String) {
        viewModelScope.launch {
            preferences.setAdminProfile(name, email)
            _saveMessage.value = "Profile saved locally"

            // Fire-and-forget: sync profile to backend
            _syncStatus.value = SyncStatus.Syncing
            try {
                val result = ApiClient.updateAdminProfile(name, email)
                if (result.success) {
                    _syncStatus.value = SyncStatus.Success("Profile synced to server")
                    _saveMessage.value = "Profile saved and synced"
                } else {
                    _syncStatus.value = SyncStatus.Error(result.error ?: "Sync failed")
                    Log.w(TAG, "Profile sync failed: ${result.error}")
                }
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error("Sync failed: ${e.message}")
                Log.w(TAG, "Profile sync error", e)
            }
        }
    }

    fun updatePassword(adminId: String, currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            // Try backend first for authoritative password verification
            _syncStatus.value = SyncStatus.Syncing
            try {
                val result = ApiClient.updateAdminPassword(currentPassword, newPassword)
                if (result.success) {
                    // Backend verified and updated — also update local copy
                    val admin = userRepository.getAdminById(adminId)
                    if (admin != null) {
                        userRepository.updateAdmin(admin.copy(password = hashPassword(newPassword)))
                    }
                    _syncStatus.value = SyncStatus.Success("Password synced to server")
                    _saveMessage.value = "Password updated successfully"
                } else {
                    _syncStatus.value = SyncStatus.Error(result.error ?: "Update failed")
                    _saveMessage.value = result.error ?: "Password update failed"
                }
            } catch (e: Exception) {
                // Backend unreachable — fall back to local verification
                Log.w(TAG, "Password sync error, falling back to local", e)
                val admin = userRepository.getAdminById(adminId)
                if (admin != null && admin.password == hashPassword(currentPassword)) {
                    userRepository.updateAdmin(admin.copy(password = hashPassword(newPassword)))
                    _syncStatus.value = SyncStatus.Error("Updated locally only (server unreachable)")
                    _saveMessage.value = "Password updated locally"
                } else {
                    _syncStatus.value = SyncStatus.Error("Offline update failed")
                    _saveMessage.value = "Current password is incorrect"
                }
            }
        }
    }

    fun clearSyncStatus() {
        _syncStatus.value = SyncStatus.Idle
    }

    fun setStolenReportNotif(enabled: Boolean) {
        viewModelScope.launch { preferences.setStolenReportNotif(enabled) }
    }

    fun setAlarmNotif(enabled: Boolean) {
        viewModelScope.launch { preferences.setAlarmNotif(enabled) }
    }

    fun setDailyDigest(enabled: Boolean) {
        viewModelScope.launch { preferences.setDailyDigest(enabled) }
    }

    fun setWeeklyReport(enabled: Boolean) {
        viewModelScope.launch { preferences.setWeeklyReport(enabled) }
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }
}
