package com.byron.trucaller.viewmodel

import android.app.Application
import android.content.ContentResolver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.BlockedNumber
import android.util.Log
import com.byron.trucaller.data.model.SmsCategory
import com.byron.trucaller.data.model.SmsConversation
import com.byron.trucaller.data.model.SmsMessage
import com.byron.trucaller.data.model.SmsSpamReport
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.data.repository.BlockedNumberRepository
import com.byron.trucaller.data.repository.CallerIdRepository
import com.byron.trucaller.data.repository.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsViewModel(
    application: Application,
    private val smsRepository: SmsRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
    private val callerIdRepository: CallerIdRepository
) : AndroidViewModel(application) {

    private val _conversations = MutableStateFlow<List<SmsConversation>>(emptyList())
    val conversations: StateFlow<List<SmsConversation>> = _conversations.asStateFlow()

    private val _spamConversations = MutableStateFlow<List<SmsConversation>>(emptyList())
    val spamConversations: StateFlow<List<SmsConversation>> = _spamConversations.asStateFlow()

    private val _currentMessages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val currentMessages: StateFlow<List<SmsMessage>> = _currentMessages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _selectedFilter = MutableStateFlow(SmsFilter.ALL)
    val selectedFilter: StateFlow<SmsFilter> = _selectedFilter.asStateFlow()

    fun loadConversations(contentResolver: ContentResolver, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val all = smsRepository.getConversations(contentResolver, userId)
                _conversations.value = all.take(MAX_LIST_SIZE)
                _spamConversations.value = all.filter {
                    it.category == SmsCategory.SPAM || it.category == SmsCategory.PROMOTIONAL
                }.take(MAX_LIST_SIZE)
            } catch (e: Exception) {
                _actionMessage.value = "Failed to load messages: ${e.message}"
            } finally {
                _isLoading.value = false
            }

            // Sync any pending unsynced SMS spam reports to backend
            syncUnsyncedReports()
        }
    }

    fun loadConversation(contentResolver: ContentResolver, address: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _currentMessages.value = smsRepository.getConversation(contentResolver, address, userId)
            } catch (e: Exception) {
                _actionMessage.value = "Failed to load conversation: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setFilter(filter: SmsFilter) {
        _selectedFilter.value = filter
    }

    fun getFilteredConversations(): List<SmsConversation> {
        val all = _conversations.value
        return when (_selectedFilter.value) {
            SmsFilter.ALL -> all.filter { it.category != SmsCategory.SPAM }
            SmsFilter.PERSONAL -> all.filter { it.category == SmsCategory.PERSONAL }
            SmsFilter.TRANSACTIONAL -> all.filter { it.category == SmsCategory.TRANSACTIONAL }
            SmsFilter.PROMOTIONAL -> all.filter { it.category == SmsCategory.PROMOTIONAL }
            SmsFilter.SPAM -> all.filter { it.category == SmsCategory.SPAM }
        }
    }

    fun reportAsSpam(
        address: String,
        messageBody: String,
        userId: String,
        contentResolver: ContentResolver
    ) {
        viewModelScope.launch {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

            // Save spam report locally
            val report = SmsSpamReport(
                id = "sms-rpt-${System.currentTimeMillis()}",
                userId = userId,
                senderNumber = address,
                messageBody = messageBody.take(200),
                reason = "Reported by user",
                reportedAt = now
            )
            smsRepository.reportSpam(report)

            // Also update caller ID entry if it exists, or create one
            val lookup = callerIdRepository.lookupNumber(address)
            if (lookup.callerIdEntry != null) {
                val entry = lookup.callerIdEntry
                val newScore = minOf(entry.spamScore + 10, 100)
                val newCategory = when {
                    newScore >= 80 -> SpamCategory.FRAUD
                    newScore >= 60 -> SpamCategory.SPAM
                    newScore >= 30 -> SpamCategory.SUSPECTED_SPAM
                    else -> SpamCategory.SAFE
                }
                callerIdRepository.updateEntry(
                    entry.copy(
                        spamScore = newScore,
                        reportCount = entry.reportCount + 1,
                        category = newCategory,
                        lastUpdated = now
                    )
                )
            } else {
                // Create a new caller ID entry for this spam sender
                callerIdRepository.insertEntry(
                    com.byron.trucaller.data.model.CallerIdEntry(
                        id = "cid-sms-${System.currentTimeMillis()}",
                        phoneNumber = address,
                        name = "Spam SMS Sender",
                        spamScore = 30,
                        reportCount = 1,
                        category = SpamCategory.SUSPECTED_SPAM,
                        lastUpdated = now
                    )
                )
            }

            _actionMessage.value = "Reported as spam"

            // Sync the new report (and any previously unsynced) to backend
            syncUnsyncedReports()

            // Refresh conversations
            loadConversations(contentResolver, userId)
        }
    }

    fun blockSmsNumber(address: String, userId: String, contentResolver: ContentResolver) {
        viewModelScope.launch {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            blockedNumberRepository.blockNumber(
                BlockedNumber(
                    id = "blk-sms-${System.currentTimeMillis()}",
                    userId = userId,
                    phoneNumber = address,
                    name = "SMS Sender",
                    reason = "Blocked from SMS",
                    blockedAt = now
                )
            )
            _actionMessage.value = "Number blocked"
            loadConversations(contentResolver, userId)
        }
    }

    fun unblockSmsNumber(address: String, userId: String, contentResolver: ContentResolver) {
        viewModelScope.launch {
            blockedNumberRepository.unblockNumber(userId, address)
            _actionMessage.value = "Number unblocked"
            loadConversations(contentResolver, userId)
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    /**
     * Sync all unsynced SMS spam reports to the backend.
     * Failures are silently logged so the UI is never disrupted.
     */
    private fun syncUnsyncedReports() {
        viewModelScope.launch {
            try {
                smsRepository.syncUnsyncedReports()
            } catch (e: Exception) {
                Log.e(TAG, "Background SMS spam report sync failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "SmsViewModel"
        /** Maximum number of items held in memory to prevent OOM on large SMS inboxes. */
        private const val MAX_LIST_SIZE = 500
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                SmsViewModel(
                    app,
                    app.container.smsRepository,
                    app.container.blockedNumberRepository,
                    app.container.callerIdRepository
                )
            }
        }
    }
}

enum class SmsFilter {
    ALL, PERSONAL, TRANSACTIONAL, PROMOTIONAL, SPAM
}
