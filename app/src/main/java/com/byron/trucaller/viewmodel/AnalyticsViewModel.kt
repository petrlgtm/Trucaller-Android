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
import com.byron.trucaller.data.repository.AlarmRepository
import com.byron.trucaller.data.repository.BlockedNumberRepository
import com.byron.trucaller.data.repository.CallerIdRepository
import com.byron.trucaller.data.repository.ContactRepository
import com.byron.trucaller.data.repository.SmsRepository
import com.byron.trucaller.data.repository.StolenReportRepository
import com.byron.trucaller.service.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class AnalyticsStats(
    val callsBlocked: Int = 0,
    val smsBlocked: Int = 0,
    val spamIdentified: Int = 0,
    val numbersLookedUp: Int = 0,
    val contactsSynced: Int = 0,
    val stolenReports: Int = 0,
    val weeklyBlockedDelta: Int = 0
)

data class MonthlyDataPoint(
    val month: String,
    val callsBlocked: Int = 0,
    val smsBlocked: Int = 0
)

data class AnalyticsUiState(
    val stats: AnalyticsStats = AnalyticsStats(),
    val history: List<MonthlyDataPoint> = emptyList(),
    val insights: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class AnalyticsViewModel(
    application: Application,
    private val blockedNumberRepository: BlockedNumberRepository,
    private val callerIdRepository: CallerIdRepository,
    private val contactRepository: ContactRepository,
    private val smsRepository: SmsRepository,
    private val stolenReportRepository: StolenReportRepository,
    private val alarmRepository: AlarmRepository,
    private val userPreferences: UserPreferences
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AnalyticsVM"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TruCallerApplication
                AnalyticsViewModel(
                    app,
                    app.container.blockedNumberRepository,
                    app.container.callerIdRepository,
                    app.container.contactRepository,
                    app.container.smsRepository,
                    app.container.stolenReportRepository,
                    app.container.alarmRepository,
                    app.container.userPreferences
                )
            }
        }
    }

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    fun refresh() {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Always compute accurate local stats first
            val localStats = computeLocalStats()

            // Try API for monthly history (optional enrichment)
            var history = emptyList<MonthlyDataPoint>()
            try {
                val historyResult = ApiClient.getUserAnalyticsHistory()
                if (historyResult.success && historyResult.data != null) {
                    history = historyResult.data.map { item ->
                        MonthlyDataPoint(
                            month = item["month"] as? String ?: "",
                            callsBlocked = (item["callsBlocked"] as? Number)?.toInt() ?: 0,
                            smsBlocked = (item["smsBlocked"] as? Number)?.toInt() ?: 0
                        )
                    }
                }
            } catch (_: Exception) {
                Log.d(TAG, "API history unavailable, using local stats only")
            }

            _uiState.value = AnalyticsUiState(
                stats = localStats,
                history = history,
                insights = generateInsights(localStats, history),
                isLoading = false
            )
        }
    }

    /**
     * Computes analytics stats entirely from local Room DB — always accurate.
     */
    private suspend fun computeLocalStats(): AnalyticsStats {
        val userId = userPreferences.loggedInUserId.first()

        // Blocked numbers count (for this user)
        val blockedCount = if (userId != null) {
            blockedNumberRepository.getBlockedCount(userId).firstOrNull() ?: 0
        } else 0

        // Caller ID entries (spam numbers identified in the database)
        val callerIdCount = callerIdRepository.getEntryCount().firstOrNull() ?: 0

        // SMS spam reports filed by this user
        val smsSpamCount = if (userId != null) {
            smsRepository.getSpamReportCount(userId).firstOrNull() ?: 0
        } else 0

        // Contacts synced for this user
        val contactCount = if (userId != null) {
            contactRepository.getContactCountByUser(userId).firstOrNull() ?: 0
        } else {
            contactRepository.getTotalContactCount().firstOrNull() ?: 0
        }

        // Stolen reports filed by this user
        val stolenCount = if (userId != null) {
            stolenReportRepository.getReportCountByUser(userId).firstOrNull() ?: 0
        } else 0

        // Alarm/remote action logs
        val alarmCount = alarmRepository.getLogCount().firstOrNull() ?: 0

        return AnalyticsStats(
            callsBlocked = blockedCount,
            smsBlocked = smsSpamCount,
            spamIdentified = callerIdCount,
            numbersLookedUp = callerIdCount,
            contactsSynced = contactCount,
            stolenReports = stolenCount,
            weeklyBlockedDelta = 0
        )
    }

    private fun generateInsights(stats: AnalyticsStats, history: List<MonthlyDataPoint>): List<String> {
        val insights = mutableListOf<String>()

        if (stats.callsBlocked > 0) {
            insights.add("You've blocked ${stats.callsBlocked} unwanted numbers. Your phone is protected.")
        }

        if (stats.smsBlocked > 0) {
            insights.add("${stats.smsBlocked} SMS spam reports filed, helping protect the community.")
        }

        if (stats.spamIdentified > 0) {
            insights.add("${stats.spamIdentified} caller IDs in your database for instant identification.")
        }

        if (stats.contactsSynced > 0) {
            insights.add("${stats.contactsSynced} contacts synced and protected with caller ID.")
        }

        if (stats.stolenReports > 0) {
            insights.add("${stats.stolenReports} stolen device report(s) on file. Your devices are monitored.")
        }

        if (history.size >= 2) {
            val recent = history.last()
            val previous = history[history.size - 2]
            val recentTotal = recent.callsBlocked + recent.smsBlocked
            val previousTotal = previous.callsBlocked + previous.smsBlocked
            if (recentTotal > previousTotal) {
                insights.add("${recent.month} saw more blocked activity than ${previous.month}. Stay vigilant.")
            } else if (recentTotal < previousTotal && previousTotal > 0) {
                insights.add("Spam activity dropped in ${recent.month}. Keep up the good habits!")
            }
        }

        if (insights.isEmpty()) {
            insights.add("Start blocking spam callers and reporting numbers to see insights here.")
        }

        return insights
    }
}
