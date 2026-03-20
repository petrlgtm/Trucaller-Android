package com.byron.trucaller.service

import android.net.Uri
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.byron.trucaller.TruCallerApplication
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

/**
 * System [CallScreeningService] that intercepts incoming calls before they ring.
 *
 * For each incoming call the service:
 * 1. Checks whether the number is on the user's block list — if so the call
 *    is silently rejected (no ring, no notification).
 * 2. Looks the number up in the central caller-ID / spam database so the
 *    result can be logged and, in the future, surfaced in the UI.
 *
 * Named `TruCallerCallScreeningService` to avoid a naming conflict with the
 * framework's [android.telecom.CallScreeningService] superclass.
 */
class TruCallerCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "TruCallerCallScreeningService"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val handle: Uri? = callDetails.handle
        val phoneNumber = handle?.schemeSpecificPart

        if (phoneNumber.isNullOrBlank()) {
            Log.d(TAG, "Incoming call with no number — allowing")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        Log.d(TAG, "Screening incoming call from: $phoneNumber")

        val app = application as? TruCallerApplication
        if (app == null) {
            Log.e(TAG, "Could not obtain TruCallerApplication — allowing call")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val blockedRepo = app.container.blockedNumberRepository
        val callerIdRepo = app.container.callerIdRepository
        val userPrefs = app.container.userPreferences

        // onScreenCall must respond synchronously, so we use runBlocking to
        // bridge into the suspend world for Room / DataStore access.
        runBlocking {
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
                    respondToCall(callDetails, response)
                    return@runBlocking
                }

                // --- 2. Caller-ID lookup ---
                val lookupResult = callerIdRepo.lookupNumber(phoneNumber)
                val entry = lookupResult.callerIdEntry

                if (entry != null) {
                    Log.d(
                        TAG,
                        "Caller ID match: ${entry.name} | category=${entry.category} " +
                                "| spamScore=${entry.spamScore} | source=${lookupResult.source}"
                    )
                } else {
                    Log.d(TAG, "No caller ID match for $phoneNumber")
                }

                // Allow the call through — the lookup result is logged above
                // and can be used by the UI layer to display caller info.
                respondToCall(callDetails, CallResponse.Builder().build())

            } catch (e: Exception) {
                Log.e(TAG, "Error screening call from $phoneNumber", e)
                // On error, let the call through so we never silently drop a
                // legitimate call.
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        }
    }
}
