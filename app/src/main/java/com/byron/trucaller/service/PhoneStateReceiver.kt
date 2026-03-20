package com.byron.trucaller.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * BroadcastReceiver that listens for phone state changes (incoming/outgoing calls)
 * and shows the CallerIdOverlayService with caller information.
 *
 * States:
 * - RINGING  -> Incoming call, show caller ID overlay
 * - OFFHOOK  -> Call answered or outgoing call, show overlay if number available
 * - IDLE     -> Call ended, dismiss overlay
 */
class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneStateReceiver"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var lastNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "Phone state changed: $stateStr, number: $incomingNumber")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Incoming call ringing
                lastState = TelephonyManager.CALL_STATE_RINGING
                if (!incomingNumber.isNullOrBlank()) {
                    lastNumber = incomingNumber
                    Log.d(TAG, "Incoming call from: $incomingNumber — showing overlay")
                    CallerIdOverlayService.show(context, incomingNumber)
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered or outgoing call started
                if (lastState == TelephonyManager.CALL_STATE_IDLE) {
                    // Transition from IDLE -> OFFHOOK means outgoing call
                    val number = incomingNumber ?: lastNumber
                    if (!number.isNullOrBlank()) {
                        Log.d(TAG, "Outgoing call to: $number — showing overlay")
                        CallerIdOverlayService.show(context, number)
                    }
                }
                lastState = TelephonyManager.CALL_STATE_OFFHOOK
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended
                lastState = TelephonyManager.CALL_STATE_IDLE
                lastNumber = null
                Log.d(TAG, "Call ended — dismissing overlay")
                CallerIdOverlayService.dismiss(context)
            }
        }
    }
}
