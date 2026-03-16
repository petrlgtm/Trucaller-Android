package com.byron.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.data.repository.UserRepository
import com.byron.trucaller.util.hashPassword
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminSettingsViewModel(
    application: Application,
    private val userRepository: UserRepository,
    private val preferences: UserPreferences
) : AndroidViewModel(application) {

    val adminName: Flow<String> = preferences.adminName
    val adminEmail: Flow<String> = preferences.adminEmail
    val stolenReportNotif: Flow<Boolean> = preferences.stolenReportNotif
    val alarmNotif: Flow<Boolean> = preferences.alarmNotif
    val dailyDigest: Flow<Boolean> = preferences.dailyDigest
    val weeklyReport: Flow<Boolean> = preferences.weeklyReport

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    fun saveProfile(name: String, email: String) {
        viewModelScope.launch {
            preferences.setAdminProfile(name, email)
            _saveMessage.value = "Profile saved successfully"
        }
    }

    fun updatePassword(adminId: String, currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            val admin = userRepository.getAdminById(adminId)
            if (admin != null && admin.password == hashPassword(currentPassword)) {
                userRepository.updateAdmin(admin.copy(password = hashPassword(newPassword)))
                _saveMessage.value = "Password updated successfully"
            } else {
                _saveMessage.value = "Current password is incorrect"
            }
        }
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                AdminSettingsViewModel(app, app.container.userRepository, app.container.userPreferences)
            }
        }
    }
}
