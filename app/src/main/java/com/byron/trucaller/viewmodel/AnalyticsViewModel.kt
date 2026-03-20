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
    private val alarmRepository: AlarmRepository
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
                    app.container.alarmRepository
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

            try {
                val result = ApiClient.getUserAnalytics()
                val historyResult = ApiClient.getUserAnalyticsHistory()

                if (result.success && result.data != null) {
                    val data = result.data
                    val stats = AnalyticsStats(
                        callsBlocked = (data["callsBlocked"] as? Number)?.toInt() ?: 0,
                        smsBlocked = (data["smsBlocked"] as? Number)?.toInt() ?: 0,
                        spamIdentified = (data["spamIdentified"] as? Number)?.toInt() ?: 0,
                        numbersLookedUp = (data["numbersLookedUp"] as? Number)?.toInt() ?: 0,
                        contactsSynced = (data["contactsSynced"] as? Number)?.toInt() ?: 0,
                        stolenReports = (data["stolenReports"] as? Number)?.toInt() ?: 0,
                        weeklyBlockedDelta = (data["weeklyBlockedDelta"] as? Number)?.toInt() ?: 0
                    )

                    val history = if (historyResult.success && historyResult.data != null) {
                        historyResult.data.map { item ->
                            MonthlyDataPoint(
                                month = item["month"] as? String ?: "",
                                callsBlocked = (item["callsBlocked"] as? Number)?.toInt() ?: 0,
                                smsBlocked = (item["smsBlocked"] as? Number)?.toInt() ?: 0
                            )
                        }
                    } else emptyList()

                    _uiState.value = AnalyticsUiState(
                        stats = stats,
                        history = history,
                        insights = generateInsights(stats, history),
                        isLoading = false
                    )
                    return@launch
                }

                Log.w(TAG, "API fetch failed: ${result.error}, falling back to local data")
                fallbackToLocal()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch analytics from API", e)
                fallbackToLocal()
            }
        }
    }

    private suspend fun fallbackToLocal() {
        try {
            val blocked = callerIdRepository.getEntryCount().firstOrNull() ?: 0
            val smsSpam = smsRepository.getTotalSpamReportCount().firstOrNull() ?: 0
            val reports = stolenReportRepository.getReportCount().firstOrNull() ?: 0
            val alarms = alarmRepository.getLogCount().firstOrNull() ?: 0

            val stats = AnalyticsStats(
                callsBlocked = blocked,
                smsBlocked = smsSpam,
                spamIdentified = blocked + smsSpam,
                numbersLookedUp = blocked,
                contactsSynced = 0,
                stolenReports = reports,
                weeklyBlockedDelta = 0
            )

            // Generate sample monthly history from local counts
            val months = listOf("Oct", "Nov", "Dec", "Jan", "Feb", "Mar")
            val history = months.mapIndexed { index, month ->
                val factor = (index + 1).toFloat() / months.size
                MonthlyDataPoint(
                    month = month,
                    callsBlocked = (stats.callsBlocked * factor * 0.3f).toInt().coerceAtLeast(0),
                    smsBlocked = (stats.smsBlocked * factor * 0.25f).toInt().coerceAtLeast(0)
                )
            }

            _uiState.value = AnalyticsUiState(
                stats = stats,
                history = history,
                insights = generateInsights(stats, history),
                isLoading = false,
                error = "Showing local data (offline)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Local fallback also failed", e)
            _uiState.value = AnalyticsUiState(
                isLoading = false,
                error = "Unable to load analytics"
            )
        }
    }

    private fun generateInsights(stats: AnalyticsStats, history: List<MonthlyDataPoint>): List<String> {
        val insights = mutableListOf<String>()

        val totalBlocked = stats.callsBlocked + stats.smsBlocked
        if (totalBlocked > 0) {
            insights.add("You've blocked a total of $totalBlocked unwanted calls and messages. Your phone is well-protected.")
        }

        if (stats.spamIdentified > 0) {
            insights.add("${stats.spamIdentified} spam numbers have been identified, helping protect you and the community.")
        }

        if (stats.weeklyBlockedDelta > 0) {
            insights.add("Blocking activity increased by ${stats.weeklyBlockedDelta} this week compared to last week.")
        } else if (stats.weeklyBlockedDelta < 0) {
            insights.add("Blocking activity decreased by ${-stats.weeklyBlockedDelta} this week. Spam attempts may be slowing down.")
        }

        if (history.size >= 2) {
            val recent = history.last()
            val previous = history[history.size - 2]
            val recentTotal = recent.callsBlocked + recent.smsBlocked
            val previousTotal = previous.callsBlocked + previous.smsBlocked
            if (recentTotal > previousTotal) {
                insights.add("${recent.month} saw more blocked activity than ${previous.month}. Stay vigilant against spam.")
            } else if (recentTotal < previousTotal && previousTotal > 0) {
                insights.add("Spam activity dropped in ${recent.month} compared to ${previous.month}. Keep up the good habits!")
            }
        }

        if (stats.stolenReports > 0) {
            insights.add("You have ${stats.stolenReports} stolen device report(s) on file. Your devices are being monitored.")
        }

        if (stats.contactsSynced > 0) {
            insights.add("${stats.contactsSynced} contacts have been synced and protected with caller ID.")
        }

        if (insights.isEmpty()) {
            insights.add("Start using TruCaller's blocking features to see personalized insights here.")
        }

        return insights
    }
}
