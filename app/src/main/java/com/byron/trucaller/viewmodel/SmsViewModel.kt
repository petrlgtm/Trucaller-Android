package com.byron.trucaller.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
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
import android.telephony.SmsManager
import android.content.ContentValues
import android.provider.Telephony
import com.byron.trucaller.data.model.SmsType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
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

    val filteredConversations: StateFlow<List<SmsConversation>> = combine(
        _conversations, _selectedFilter
    ) { all, filter ->
        when (filter) {
            SmsFilter.ALL -> all.filter { it.category != SmsCategory.SPAM }
            SmsFilter.PERSONAL -> all.filter { it.category == SmsCategory.PERSONAL }
            SmsFilter.TRANSACTIONAL -> all.filter { it.category == SmsCategory.TRANSACTIONAL }
            SmsFilter.PROMOTIONAL -> all.filter { it.category == SmsCategory.PROMOTIONAL }
            SmsFilter.SPAM -> all.filter { it.category == SmsCategory.SPAM }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var _lastActiveAddress: String? = null

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val authApp = getApplication<TruCallerApplication>()
            viewModelScope.launch {
                val userId = authApp.container.userPreferences.loggedInUserId.firstOrNull() ?: "unknown"
                loadConversations(authApp.contentResolver, userId)
                
                _lastActiveAddress?.let { address ->
                    loadConversation(authApp.contentResolver, address, userId)
                }
            }
        }
    }

    init {
        application.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver
        )
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(smsObserver)
    }

    fun loadConversations(contentResolver: ContentResolver, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Show raw conversations IMMEDIATELY (no enrichment — instant)
                val rawMessages = com.byron.trucaller.util.SmsReader.readAllMessages(contentResolver, MAX_LIST_SIZE)
                val rawConversations = smsRepository.buildConversationsRaw(rawMessages)
                _conversations.value = rawConversations.take(MAX_LIST_SIZE)
                _isLoading.value = false

                // Then enrich in background (local-only lookups, fast)
                val enriched = smsRepository.getConversations(contentResolver, userId)
                _conversations.value = enriched.take(MAX_LIST_SIZE)
                _spamConversations.value = enriched.filter {
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
        _lastActiveAddress = address
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

    fun getFilteredConversations(): List<SmsConversation> = filteredConversations.value

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
            _actionMessage.value = "Number rejected"
            loadConversations(contentResolver, userId)
        }
    }

    fun unblockSmsNumber(address: String, userId: String, contentResolver: ContentResolver) {
        viewModelScope.launch {
            blockedNumberRepository.unblockNumber(userId, address)
            _actionMessage.value = "Number unrejected"
            loadConversations(contentResolver, userId)
        }
    }

    fun removeFromSpam(address: String, userId: String, contentResolver: ContentResolver) {
        viewModelScope.launch {
            // Remove CallerIdEntry spam score
            val existing = callerIdRepository.lookupNumberLocal(address)
            if (existing.callerIdEntry != null && existing.source == "caller_id_db") {
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                callerIdRepository.updateEntry(existing.callerIdEntry.copy(
                    spamScore = 0,
                    category = com.byron.trucaller.data.model.SpamCategory.SAFE,
                    lastUpdated = now
                ))
            }
            _actionMessage.value = "Removed from spam"
            loadConversations(contentResolver, userId)
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun sendSms(address: String, body: String, contentResolver: ContentResolver) {
        if (body.isBlank()) return

        viewModelScope.launch {
            try {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    getApplication<Application>().getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                if (smsManager == null) {
                    _actionMessage.value = "SMS service unavailable"
                    return@launch
                }

                val sentAt = System.currentTimeMillis()
                // Optimistic UI update: add a local version of the message immediately
                val optimisticMsg = SmsMessage(
                    id = -sentAt,
                    address = address,
                    body = body,
                    date = sentAt,
                    type = SmsType.SENT,
                    read = true,
                    category = SmsCategory.PERSONAL
                )
                _currentMessages.value = _currentMessages.value + optimisticMsg

                val parts = smsManager.divideMessage(body)
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)

                // Try to save to sent folder (only works when app is the default SMS app)
                var savedToProvider = false
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, address)
                        put(Telephony.Sms.BODY, body)
                        put(Telephony.Sms.DATE, sentAt)
                        put(Telephony.Sms.READ, 1)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    }
                    val uri = contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                    savedToProvider = uri != null
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to save to provider — app is not default SMS app", e)
                }

                _actionMessage.value = "Message sent"

                // Allow system provider a moment to index the new message
                delay(600)

                val authApp = getApplication<TruCallerApplication>()
                val userId = authApp.container.userPreferences.loggedInUserId.firstOrNull() ?: "unknown"
                val reloaded = smsRepository.getConversation(contentResolver, address, userId)

                // If the sent message is now in the provider, use real data.
                // If not (not default app), keep the optimistic message so it stays visible.
                val sentFoundInProvider = reloaded.any { msg ->
                    msg.body == body && msg.type == SmsType.SENT
                }
                _currentMessages.value = if (sentFoundInProvider) {
                    reloaded
                } else {
                    reloaded.filter { it.id > 0 } + optimisticMsg
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS", e)
                _actionMessage.value = "Failed to send: ${e.message}"
                // Remove the optimistic message on failure
                _currentMessages.value = _currentMessages.value.filter { it.body != body || it.id > 0 }
            }
        }
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
