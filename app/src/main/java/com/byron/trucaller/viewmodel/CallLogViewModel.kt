package com.byron.trucaller.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.CallLogEntry
import com.byron.trucaller.data.model.CallType
import com.byron.trucaller.data.preferences.UserPreferences
import com.byron.trucaller.data.repository.BlockedNumberRepository
import com.byron.trucaller.data.repository.CallerIdRepository
import com.byron.trucaller.util.CallLogReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CallLogViewModel(
    application: Application,
    private val callerIdRepository: CallerIdRepository,
    private val blockedNumberRepository: BlockedNumberRepository,
    private val userPreferences: UserPreferences
) : AndroidViewModel(application) {

    private val _callLogEntries = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val callLogEntries: StateFlow<List<CallLogEntry>> = _callLogEntries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFilter = MutableStateFlow(CallLogFilter.ALL)
    val selectedFilter: StateFlow<CallLogFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun loadCallLog(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val rawEntries = CallLogReader.readCallLog(context, limit = 500)
                // Show raw entries IMMEDIATELY so the user sees data fast
                _callLogEntries.value = rawEntries.take(MAX_LIST_SIZE)
                _isLoading.value = false

                // Enrich in background — update entries as each lookup completes
                val enriched = rawEntries.take(MAX_LIST_SIZE).toMutableList()
                for (i in enriched.indices) {
                    try {
                        val updated = enrichWithCallerIdData(enriched[i])
                        if (updated != enriched[i]) {
                            enriched[i] = updated
                            _callLogEntries.value = enriched.toList()
                        }
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                _actionMessage.value = "Failed to load call log: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private suspend fun enrichWithCallerIdData(entry: CallLogEntry): CallLogEntry {
        return try {
            // Use fast local-only lookup — no network calls, instant results
            val lookup = callerIdRepository.lookupNumberLocal(entry.phoneNumber)

            // Check if the number is blocked by the current user
            val userId = userPreferences.loggedInUserId.first()
            val blocked = if (userId != null) {
                blockedNumberRepository.isBlocked(userId, entry.phoneNumber)
            } else {
                entry.callType == CallType.BLOCKED || entry.callType == CallType.REJECTED
            }

            if (lookup.callerIdEntry != null) {
                // High-trust sources: prefer the looked-up name over system cached name
                val highTrustSources = setOf("caller_id_db", "registered_user", "alias")
                val resolvedName = if (lookup.source in highTrustSources) {
                    lookup.callerIdEntry.name ?: entry.name
                } else {
                    entry.name ?: lookup.callerIdEntry.name
                }

                entry.copy(
                    name = resolvedName,
                    isSpam = lookup.callerIdEntry.spamScore > 30,
                    spamScore = lookup.callerIdEntry.spamScore,
                    isBlocked = blocked
                )
            } else {
                // source is "not_found" — keep system cached name as-is
                entry.copy(isBlocked = blocked)
            }
        } catch (_: Exception) {
            entry
        }
    }

    fun setFilter(filter: CallLogFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredEntries(): List<CallLogEntry> {
        val all = _callLogEntries.value
        val query = _searchQuery.value.lowercase()

        val filtered = when (_selectedFilter.value) {
            CallLogFilter.ALL -> all
            CallLogFilter.INCOMING -> all.filter { it.callType == CallType.INCOMING }
            CallLogFilter.OUTGOING -> all.filter { it.callType == CallType.OUTGOING }
            CallLogFilter.MISSED -> all.filter { it.callType == CallType.MISSED }
            CallLogFilter.BLOCKED -> all.filter {
                it.callType == CallType.BLOCKED || it.callType == CallType.REJECTED || it.isBlocked
            }
        }

        return if (query.isBlank()) {
            filtered
        } else {
            filtered.filter { entry ->
                (entry.name?.lowercase()?.contains(query) == true) ||
                    entry.phoneNumber.contains(query)
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    companion object {
        /** Maximum number of items held in memory to prevent OOM on large call logs. */
        private const val MAX_LIST_SIZE = 500

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                CallLogViewModel(
                    app,
                    app.container.callerIdRepository,
                    app.container.blockedNumberRepository,
                    app.container.userPreferences
                )
            }
        }
    }
}

enum class CallLogFilter {
    ALL, INCOMING, OUTGOING, MISSED, BLOCKED
}
