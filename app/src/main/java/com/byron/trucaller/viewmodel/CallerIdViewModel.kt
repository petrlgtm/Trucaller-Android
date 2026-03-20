package com.byron.trucaller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.BlockedNumber
import com.byron.trucaller.data.model.CallerIdEntry
import com.byron.trucaller.data.model.SpamCategory
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.data.repository.BlockedNumberRepository
import com.byron.trucaller.data.repository.CallerIdRepository
import com.byron.trucaller.data.repository.LookupResult
import com.byron.trucaller.service.ApiClient
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallerIdViewModel(
    application: Application,
    private val callerIdRepository: CallerIdRepository,
    private val preferences: UserPreferences,
    private val blockedNumberRepository: BlockedNumberRepository
) : AndroidViewModel(application) {

    val allEntries: Flow<List<CallerIdEntry>> = callerIdRepository.getAllEntries()

    private val _lookupResult = MutableStateFlow<LookupResult?>(null)
    val lookupResult: StateFlow<LookupResult?> = _lookupResult.asStateFlow()

    private val _recentLookups = MutableStateFlow<List<CallerIdEntry>>(emptyList())
    val recentLookups: StateFlow<List<CallerIdEntry>> = _recentLookups.asStateFlow()

    private val _notFound = MutableStateFlow(false)
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isNumberBlocked = MutableStateFlow(false)
    val isNumberBlocked: StateFlow<Boolean> = _isNumberBlocked.asStateFlow()

    // Community verification state
    private val _verificationStatus = MutableStateFlow<VerificationUiState?>(null)
    val verificationStatus: StateFlow<VerificationUiState?> = _verificationStatus.asStateFlow()

    init {
        loadRecentLookups()
    }

    private fun loadRecentLookups() {
        viewModelScope.launch {
            preferences.recentLookups.collect { ids ->
                val entries = ids.mapNotNull { id -> callerIdRepository.getById(id) }
                _recentLookups.value = entries
            }
        }
    }

    fun lookup(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val result = callerIdRepository.lookupNumber(query)
            if (result.callerIdEntry != null) {
                _lookupResult.value = result
                _notFound.value = false
                preferences.addRecentLookup(result.callerIdEntry.id)
                // Reload recent lookups
                val ids = preferences.recentLookups.first()
                _recentLookups.value = ids.mapNotNull { id -> callerIdRepository.getById(id) }
            } else {
                _lookupResult.value = null
                _notFound.value = true
            }
        }
    }

    fun checkIfBlocked(phoneNumber: String, userId: String) {
        viewModelScope.launch {
            _isNumberBlocked.value = blockedNumberRepository.isBlocked(userId, phoneNumber)
        }
    }

    fun blockNumber(entry: CallerIdEntry, userId: String) {
        viewModelScope.launch {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

            // Save to blocked numbers table
            val blocked = BlockedNumber(
                id = "blk-${System.currentTimeMillis()}",
                userId = userId,
                phoneNumber = entry.phoneNumber,
                name = entry.name,
                reason = "Blocked by user",
                blockedAt = now
            )
            blockedNumberRepository.blockNumber(blocked)

            // Update caller ID entry spam score
            val updated = entry.copy(
                category = SpamCategory.SPAM,
                spamScore = maxOf(entry.spamScore, 70),
                lastUpdated = now
            )
            callerIdRepository.updateEntry(updated)
            _lookupResult.value = _lookupResult.value?.copy(callerIdEntry = updated)
            _isNumberBlocked.value = true
            _actionMessage.value = "${entry.name} has been blocked"
        }
    }

    fun unblockNumber(phoneNumber: String, userId: String, name: String) {
        viewModelScope.launch {
            blockedNumberRepository.unblockNumber(userId, phoneNumber)
            _isNumberBlocked.value = false
            _actionMessage.value = "$name has been unblocked"
        }
    }

    fun reportNumber(entry: CallerIdEntry) {
        viewModelScope.launch {
            val newScore = minOf(entry.spamScore + 5, 100)
            val newCategory = when {
                newScore >= 80 -> SpamCategory.FRAUD
                newScore >= 60 -> SpamCategory.SPAM
                newScore >= 30 -> SpamCategory.SUSPECTED_SPAM
                else -> SpamCategory.SAFE
            }
            val updated = entry.copy(
                reportCount = entry.reportCount + 1,
                spamScore = newScore,
                category = newCategory,
                lastUpdated = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            )
            callerIdRepository.updateEntry(updated)
            _lookupResult.value = _lookupResult.value?.copy(callerIdEntry = updated)
            _actionMessage.value = "Reported ${entry.name}. Reports: ${updated.reportCount}"

            // Fire-and-forget: sync spam report to backend
            try {
                ApiClient.reportSpamCall(
                    phoneNumber = entry.phoneNumber,
                    reason = "User report #${updated.reportCount}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync spam report to backend for ${entry.phoneNumber}", e)
            }
        }
    }

    fun saveToContacts(entry: CallerIdEntry, userId: String) {
        viewModelScope.launch {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            val contact = com.byron.trucaller.data.model.Contact(
                id = "cnt-${System.currentTimeMillis()}",
                userId = userId,
                name = entry.name,
                phoneNumber = entry.phoneNumber,
                syncedAt = now,
                isBackedUp = true
            )
            (getApplication<TruCallerApplication>()).container.contactRepository.insertContact(contact)
            _actionMessage.value = "${entry.name} saved to contacts"
        }
    }

    fun updateEntryName(entry: CallerIdEntry, newName: String) {
        viewModelScope.launch {
            val updated = entry.copy(
                name = newName,
                lastUpdated = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            )
            callerIdRepository.updateEntry(updated)
            _lookupResult.value = _lookupResult.value?.copy(callerIdEntry = updated)
            _actionMessage.value = "Updated name to $newName"
        }
    }

    fun addEntry(entry: CallerIdEntry) {
        viewModelScope.launch {
            callerIdRepository.insertEntry(entry)
            // Fire-and-forget: sync new entry to backend
            try {
                val result = ApiClient.createAdminCallerId(
                    id = entry.id,
                    phoneNumber = entry.phoneNumber,
                    name = entry.name,
                    spamScore = entry.spamScore,
                    reportCount = entry.reportCount,
                    category = entry.category.name,
                    lastUpdated = entry.lastUpdated
                )
                if (result.success) {
                    _actionMessage.value = "Entry added and synced to server"
                } else {
                    _actionMessage.value = "Entry added locally (sync failed: ${result.error ?: "unknown"})"
                    Log.w(TAG, "Backend sync failed for add: ${result.error}")
                }
            } catch (e: Exception) {
                _actionMessage.value = "Entry added locally (sync error)"
                Log.e(TAG, "Failed to sync new caller ID entry to backend", e)
            }
        }
    }

    fun updateEntry(entry: CallerIdEntry) {
        viewModelScope.launch {
            callerIdRepository.updateEntry(entry)
            // Fire-and-forget: sync update to backend
            try {
                val result = ApiClient.updateAdminCallerId(
                    entryId = entry.id,
                    spamScore = entry.spamScore,
                    category = entry.category.name,
                    name = entry.name
                )
                if (result.success) {
                    _actionMessage.value = "Entry updated and synced to server"
                } else {
                    _actionMessage.value = "Entry updated locally (sync failed: ${result.error ?: "unknown"})"
                    Log.w(TAG, "Backend sync failed for update: ${result.error}")
                }
            } catch (e: Exception) {
                _actionMessage.value = "Entry updated locally (sync error)"
                Log.e(TAG, "Failed to sync caller ID update to backend", e)
            }
        }
    }

    fun deleteEntry(entry: CallerIdEntry) {
        viewModelScope.launch {
            callerIdRepository.deleteEntry(entry)
            // Fire-and-forget: sync delete to backend
            try {
                val result = ApiClient.deleteAdminCallerId(entryId = entry.id)
                if (result.success) {
                    _actionMessage.value = "${entry.name} deleted and synced to server"
                } else {
                    _actionMessage.value = "${entry.name} deleted locally (sync failed: ${result.error ?: "unknown"})"
                    Log.w(TAG, "Backend sync failed for delete: ${result.error}")
                }
            } catch (e: Exception) {
                _actionMessage.value = "${entry.name} deleted locally (sync error)"
                Log.e(TAG, "Failed to sync caller ID delete to backend", e)
            }
        }
    }

    // ── Bulk Operations ─────────────────────────────────────────────────

    private val _isBulkOperationInProgress = MutableStateFlow(false)
    val isBulkOperationInProgress: StateFlow<Boolean> = _isBulkOperationInProgress.asStateFlow()

    fun bulkDeleteEntries(ids: Set<String>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isBulkOperationInProgress.value = true
            try {
                callerIdRepository.deleteEntriesByIds(ids.toList())
                // Fire-and-forget: sync deletions to backend
                ids.forEach { id ->
                    try {
                        ApiClient.deleteAdminCallerId(id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync bulk delete for $id to backend", e)
                    }
                }
                _actionMessage.value = "Deleted ${ids.size} entries"
            } catch (e: Exception) {
                Log.e(TAG, "Bulk delete failed", e)
                _actionMessage.value = "Bulk delete failed: ${e.message}"
            } finally {
                _isBulkOperationInProgress.value = false
                onComplete()
            }
        }
    }

    fun bulkUpdateCategory(ids: Set<String>, category: SpamCategory, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isBulkOperationInProgress.value = true
            try {
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                callerIdRepository.updateCategoryByIds(ids.toList(), category, now)
                // Fire-and-forget: sync category updates to backend
                ids.forEach { id ->
                    try {
                        ApiClient.updateAdminCallerId(entryId = id, category = category.name)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to sync bulk category update for $id to backend", e)
                    }
                }
                _actionMessage.value = "Updated ${ids.size} entries to ${category.name}"
            } catch (e: Exception) {
                Log.e(TAG, "Bulk category update failed", e)
                _actionMessage.value = "Bulk category update failed: ${e.message}"
            } finally {
                _isBulkOperationInProgress.value = false
                onComplete()
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun clearLookup() {
        _lookupResult.value = null
        _notFound.value = false
        _verificationStatus.value = null
    }

    /** Load community verification status for the currently displayed phone number. */
    fun loadVerificationStatus(phoneNumber: String) {
        viewModelScope.launch {
            try {
                val result = ApiClient.getSpamVerification(phoneNumber)
                if (result.success && result.data != null) {
                    val data = result.data
                    _verificationStatus.value = VerificationUiState(
                        phoneNumber = data["phoneNumber"]?.toString() ?: phoneNumber,
                        confirmCount = (data["confirmCount"] as? Number)?.toInt() ?: 0,
                        disputeCount = (data["disputeCount"] as? Number)?.toInt() ?: 0,
                        communityVerified = data["communityVerified"] as? Boolean ?: false,
                        userVote = data["userVote"]?.toString()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load verification status for $phoneNumber", e)
            }
        }
    }

    /** Submit a confirm or dispute vote for a phone number. */
    fun verifySpam(phoneNumber: String, vote: String) {
        viewModelScope.launch {
            try {
                val result = ApiClient.verifySpam(phoneNumber, vote)
                if (result.success && result.data != null) {
                    val data = result.data
                    _verificationStatus.value = VerificationUiState(
                        phoneNumber = data["phoneNumber"]?.toString() ?: phoneNumber,
                        confirmCount = (data["confirmCount"] as? Number)?.toInt() ?: 0,
                        disputeCount = (data["disputeCount"] as? Number)?.toInt() ?: 0,
                        communityVerified = data["communityVerified"] as? Boolean ?: false,
                        userVote = data["userVote"]?.toString()
                    )
                    _actionMessage.value = if (vote == "confirm") "Spam confirmed" else "Spam disputed"
                } else {
                    _actionMessage.value = result.error ?: "Verification failed"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to verify spam for $phoneNumber", e)
                _actionMessage.value = "Verification failed: network error"
            }
        }
    }

    fun selectRecentLookup(entry: CallerIdEntry) {
        _lookupResult.value = LookupResult(callerIdEntry = entry, contactMatch = null, source = "recent")
        _notFound.value = false
    }

    fun getBlockedNumbers(userId: String): Flow<List<BlockedNumber>> =
        blockedNumberRepository.getBlockedByUser(userId)

    companion object {
        private const val TAG = "CallerIdViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                CallerIdViewModel(
                    app,
                    app.container.callerIdRepository,
                    app.container.userPreferences,
                    app.container.blockedNumberRepository
                )
            }
        }
    }
}

data class VerificationUiState(
    val phoneNumber: String,
    val confirmCount: Int = 0,
    val disputeCount: Int = 0,
    val communityVerified: Boolean = false,
    val userVote: String? = null
)
