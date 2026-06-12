package com.byron.trucaller.service

import android.net.Uri
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.byron.trucaller.TruCallerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * System [CallScreeningService] that intercepts incoming calls before they ring.
 *
 * For each incoming call the service:
 * 1. Checks whether the number is on the user's block list — if so the call
 *    is silently rejected (no ring, no notification).
 * 2. Looks the number up in the central caller-ID / spam database so the
 *    result can be logged and surfaced in the UI overlay.
 *
 * The `onScreenCall` callback must call `respondToCall` exactly once and return
 * quickly. We launch a coroutine with a hard 1.5-second timeout and allow the
 * call through if the timeout fires — so a slow DB never silently drops a call.
 */
class TruCallerCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "TruCallerCallScreeningService"
        private const val SCREEN_TIMEOUT_MS = 1_500L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // The framework requires respondToCall to be invoked exactly once per call.
        // This guard makes every response path idempotent: e.g. if the block path
        // already responded but a follow-up disk read pushes it past the timeout,
        // the timeout fallback must not respond a second time (which would override
        // the block / throw). compareAndSet ensures only the first response wins.
        val responded = AtomicBoolean(false)
        fun respondOnce(response: CallResponse) {
            if (responded.compareAndSet(false, true)) {
                respondOnce(response)
            } else {
                Log.w(TAG, "respondToCall already invoked for this call — ignoring duplicate")
            }
        }

        val handle: Uri? = callDetails.handle
        val phoneNumber = handle?.schemeSpecificPart

        if (phoneNumber.isNullOrBlank()) {
            Log.d(TAG, "Incoming call with no number — allowing")
            respondOnce(CallResponse.Builder().build())
            return
        }

        Log.d(TAG, "Screening incoming call from: $phoneNumber")

        val app = application as? TruCallerApplication
        if (app == null) {
            Log.e(TAG, "Could not obtain TruCallerApplication — allowing call")
            respondOnce(CallResponse.Builder().build())
            return
        }

        val blockedRepo = app.container.blockedNumberRepository
        val callerIdRepo = app.container.callerIdRepository
        val userPrefs = app.container.userPreferences
        val scheduleRepo = app.container.blockingScheduleRepository
        val contactRepo = app.container.contactRepository

        // Launch on our service scope with a hard timeout. If screening logic
        // doesn't complete in SCREEN_TIMEOUT_MS we allow the call through to
        // avoid ever silently blocking a legitimate call due to DB/IO latency.
        serviceScope.launch {
            val completed = withTimeoutOrNull(SCREEN_TIMEOUT_MS) {
                screenCallInternal(
                    callDetails, phoneNumber, app,
                    blockedRepo, callerIdRepo, userPrefs, scheduleRepo, contactRepo,
                    ::respondOnce
                )
                true
            }

            if (completed == null) {
                Log.w(TAG, "Screening timeout for $phoneNumber — allowing call through")
                respondOnce(CallResponse.Builder().build())
            }
        }
    }

    private suspend fun screenCallInternal(
        callDetails: Call.Details,
        phoneNumber: String,
        app: TruCallerApplication,
        blockedRepo: com.byron.trucaller.data.repository.BlockedNumberRepository,
        callerIdRepo: com.byron.trucaller.data.repository.CallerIdRepository,
        userPrefs: com.byron.trucaller.data.preferences.UserPreferences,
        scheduleRepo: com.byron.trucaller.data.repository.BlockingScheduleRepository,
        contactRepo: com.byron.trucaller.data.repository.ContactRepository,
        respondOnce: (CallResponse) -> Unit
    ) {
        try {
            val userId = userPrefs.loggedInUserId.firstOrNull()

            // --- 1. Block-list check ---
            if (userId != null && blockedRepo.isBlocked(userId, phoneNumber)) {
                Log.d(TAG, "Blocked number detected: $phoneNumber — rejecting silently")
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                    .build()
                respondOnce(response)

                val lookup = callerIdRepo.lookupNumberLocal(phoneNumber, userId)
                val blockedEntry = lookup.callerIdEntry
                CallNotificationHelper.showBlockedCallNotification(
                    context = this@TruCallerCallScreeningService,
                    callerName = blockedEntry?.name,
                    number = phoneNumber,
                    spamScore = blockedEntry?.spamScore ?: -1
                )
                return
            }

            // --- 2. Caller-ID lookup (local only — no network on screening thread) ---
            val lookupResult = callerIdRepo.lookupNumberLocal(phoneNumber, userId)
            val entry = lookupResult.callerIdEntry

            CallerIdCache.put(phoneNumber, lookupResult)

            if (entry != null) {
                Log.d(
                    TAG,
                    "Caller ID match: ${entry.name} | category=${entry.category} " +
                            "| spamScore=${entry.spamScore} | source=${lookupResult.source}"
                )
            } else {
                Log.d(TAG, "No caller ID match for $phoneNumber")
            }

            // --- 3. Auto-reject high-spam calls ---
            val spamScore = entry?.spamScore ?: 0
            when {
                spamScore >= 80 -> {
                    Log.d(TAG, "FRAUD spam score ($spamScore) for $phoneNumber — rejecting silently")
                    respondOnce(
                        CallResponse.Builder()
                            .setDisallowCall(true).setRejectCall(true)
                            .setSkipCallLog(false).setSkipNotification(true)
                            .build()
                    )
                    return
                }
                spamScore >= 60 -> {
                    Log.d(TAG, "SPAM score ($spamScore) for $phoneNumber — rejecting with notification")
                    respondOnce(
                        CallResponse.Builder()
                            .setDisallowCall(true).setRejectCall(true)
                            .setSkipCallLog(false).setSkipNotification(false)
                            .build()
                    )
                    return
                }
                else -> Log.d(TAG, "Spam score ($spamScore) for $phoneNumber — checking schedules")
            }

            // --- 4. Schedule-based blocking ---
            if (userId != null) {
                val activeSchedules = scheduleRepo.getActiveSchedulesByUser(userId)
                if (activeSchedules.isNotEmpty()) {
                    val contacts = contactRepo.getContactsByUser(userId).firstOrNull() ?: emptyList()
                    val decision = ScheduleEvaluator.shouldBlockCall(
                        schedules = activeSchedules,
                        phoneNumber = phoneNumber,
                        contacts = contacts
                    )
                    if (decision.shouldBlock) {
                        Log.d(TAG, "Schedule blocked: ${decision.reason} — rejecting $phoneNumber")
                        respondOnce(
                            CallResponse.Builder()
                                .setDisallowCall(true).setRejectCall(true)
                                .setSkipCallLog(false).setSkipNotification(true)
                                .build()
                        )
                        val lookup = callerIdRepo.lookupNumberLocal(phoneNumber, userId)
                        CallNotificationHelper.showBlockedCallNotification(
                            context = this@TruCallerCallScreeningService,
                            callerName = lookup.callerIdEntry?.name,
                            number = phoneNumber,
                            spamScore = lookup.callerIdEntry?.spamScore ?: -1
                        )
                        return
                    }
                    Log.d(TAG, "Schedule check passed: ${decision.reason}")
                }
            }

            // All checks passed — allow the call through
            Log.d(TAG, "All checks passed for $phoneNumber — allowing call")
            respondOnce(CallResponse.Builder().build())

        } catch (e: Exception) {
            Log.e(TAG, "Error screening call from $phoneNumber", e)
            respondOnce(CallResponse.Builder().build())
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
