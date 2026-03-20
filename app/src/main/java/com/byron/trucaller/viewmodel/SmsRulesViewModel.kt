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
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsRule
import com.byron.trucaller.data.model.SmsRuleType
import com.byron.trucaller.data.repository.SmsRuleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsRulesViewModel(
    application: Application,
    private val smsRuleRepository: SmsRuleRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SmsRulesViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                SmsRulesViewModel(app, app.container.smsRuleRepository)
            }
        }
    }

    private val _rules = MutableStateFlow<List<SmsRule>>(emptyList())
    val rules: StateFlow<List<SmsRule>> = _rules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() { _statusMessage.value = null }

    fun loadRules(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                smsRuleRepository.getRulesByUser(userId).collect { list ->
                    _rules.value = list
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load SMS rules", e)
                _statusMessage.value = "Failed to load rules: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun addRule(
        userId: String,
        ruleType: SmsRuleType,
        pattern: String,
        targetCategory: SmsCategory,
        priority: Int = 5
    ) {
        viewModelScope.launch {
            try {
                // Validate regex patterns before saving
                if (ruleType == SmsRuleType.REGEX_MATCH) {
                    try {
                        Regex(pattern)
                    } catch (e: Exception) {
                        _statusMessage.value = "Invalid regex pattern: ${e.message}"
                        return@launch
                    }
                }

                val clampedPriority = priority.coerceIn(1, 10)
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                val rule = SmsRule(
                    id = "sms-rule-${System.currentTimeMillis()}",
                    userId = userId,
                    ruleType = ruleType,
                    pattern = pattern.trim(),
                    targetCategory = targetCategory,
                    priority = clampedPriority,
                    isActive = true,
                    createdAt = now
                )
                smsRuleRepository.insertRule(rule)
                _statusMessage.value = "Rule added"
                Log.d(TAG, "SMS rule created: ${rule.id} type=${ruleType} pattern=$pattern")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add SMS rule", e)
                _statusMessage.value = "Failed to add rule: ${e.message}"
            }
        }
    }

    fun toggleRule(ruleId: String, isActive: Boolean) {
        viewModelScope.launch {
            try {
                smsRuleRepository.toggleRuleActive(ruleId, isActive)
                _statusMessage.value = if (isActive) "Rule enabled" else "Rule disabled"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle SMS rule $ruleId", e)
                _statusMessage.value = "Failed to update rule: ${e.message}"
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            try {
                smsRuleRepository.deleteRuleById(ruleId)
                _statusMessage.value = "Rule deleted"
                Log.d(TAG, "SMS rule deleted: $ruleId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete SMS rule $ruleId", e)
                _statusMessage.value = "Failed to delete rule: ${e.message}"
            }
        }
    }
}
