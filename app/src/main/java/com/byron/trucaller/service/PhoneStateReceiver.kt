package com.byron.trucaller.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.byron.trucaller.TruCallerApplication
import com.byron.trucaller.data.model.CallDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that listens for phone state changes (incoming/outgoing calls)
 * and shows the CallerIdOverlayService with caller information.
 *
 * Also starts/stops [CallRecordingService] when recording is enabled in preferences.
 *
 * States:
 * - RINGING  -> Incoming call, show caller ID overlay
 * - OFFHOOK  -> Call answered or outgoing call, start recording if enabled
 * - IDLE     -> Call ended, dismiss overlay and stop recording
 */
class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneStateReceiver"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var lastNumber: String? = null
        private var lastDirection: CallDirection = CallDirection.INCOMING
        private var isRecordingActive = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Capture the outgoing number before the call state changes to OFFHOOK
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            if (!number.isNullOrBlank()) {
                lastNumber = number
                lastDirection = CallDirection.OUTGOING
                Log.d(TAG, "Outgoing call captured: $number")
            }
            return
        }

        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "Phone state changed: $stateStr, number: $incomingNumber")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Incoming call ringing
                lastState = TelephonyManager.CALL_STATE_RINGING
                lastDirection = CallDirection.INCOMING
                if (!incomingNumber.isNullOrBlank()) {
                    lastNumber = incomingNumber
                    Log.d(TAG, "Incoming call from: $incomingNumber — showing overlay")
                    CallerIdOverlayService.show(context, incomingNumber)
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered or outgoing call started
                if (lastState == TelephonyManager.CALL_STATE_IDLE) {
                    // Transition from IDLE -> OFFHOOK: use number captured in NEW_OUTGOING_CALL
                    val number = lastNumber
                    if (!number.isNullOrBlank()) {
                        Log.d(TAG, "Outgoing call to: $number — showing overlay")
                        CallerIdOverlayService.show(context, number)
                    }
                }
                lastState = TelephonyManager.CALL_STATE_OFFHOOK

                // Start call recording if enabled
                val number = lastNumber
                if (!number.isNullOrBlank() && !isRecordingActive) {
                    startRecordingIfEnabled(context, number, lastDirection)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended — stop recording and dismiss overlay
                if (isRecordingActive) {
                    Log.d(TAG, "Call ended — stopping recording")
                    CallRecordingService.stop(context)
                    isRecordingActive = false
                }
                lastState = TelephonyManager.CALL_STATE_IDLE
                lastNumber = null
                Log.d(TAG, "Call ended — dismissing overlay")
                CallerIdOverlayService.dismiss(context)
            }
        }
    }

    /**
     * Checks user preferences and starts [CallRecordingService] if recording is enabled.
     *
     * The direction filter is also checked: if the user set recording to INCOMING-only or
     * OUTGOING-only, calls in the other direction are skipped.
     *
     * Uses goAsync() + coroutine to avoid blocking the main thread in onReceive().
     */
    private fun startRecordingIfEnabled(context: Context, phoneNumber: String, direction: CallDirection) {
        val app = context.applicationContext as? TruCallerApplication ?: return
        val prefs = app.container.userPreferences

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val recordingEnabled = prefs.recordingEnabled.firstOrNull() ?: false
                if (!recordingEnabled) {
                    Log.d(TAG, "Call recording is disabled in preferences")
                    return@launch
                }

                // Check direction filter
                val directionPref = prefs.recordingDirection.firstOrNull() ?: "ALL"
                val shouldRecord = when (directionPref) {
                    "INCOMING" -> direction == CallDirection.INCOMING
                    "OUTGOING" -> direction == CallDirection.OUTGOING
                    else -> true // "ALL"
                }

                if (!shouldRecord) {
                    Log.d(TAG, "Call direction $direction does not match filter $directionPref — skipping recording")
                    return@launch
                }

                Log.d(TAG, "Starting call recording for $phoneNumber (direction=$direction)")
                CallRecordingService.start(context, phoneNumber, direction)
                isRecordingActive = true
            } catch (e: Exception) {
                Log.e(TAG, "Error checking recording preferences", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
