package com.byron.trucaller.service

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.byron.trucaller.ui.main.InCallActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Custom InCallService to provide Trucaller-branded UI during calls.
 * Must be registered in AndroidManifest.xml and app must be set as default dialer.
 */
class CustomInCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: $call")
        
        activeCall.value = call
        
        // Ensure UI is shown
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
        
        call.registerCallback(callCallback)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved: $call")
        call.unregisterCallback(callCallback)
        if (activeCall.value == call) {
            activeCall.value = null
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        Log.d(TAG, "onCallAudioStateChanged: $audioState")
        _audioState.value = audioState
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "Call state changed: $state")
            activeCall.value = call
            
            if (state == Call.STATE_DISCONNECTED) {
                activeCall.value = null
            }
        }
    }

    companion object {
        private const val TAG = "CustomInCallService"
        
        var instance: CustomInCallService? = null
            private set

        val activeCall = MutableStateFlow<Call?>(null)
        
        private val _audioState = MutableStateFlow<CallAudioState?>(null)
        val audioState = _audioState.asStateFlow()

        fun toggleMute(currentMute: Boolean) {
            instance?.setMuted(!currentMute)
        }

        fun toggleSpeaker(currentSpeaker: Boolean) {
            val route = if (currentSpeaker) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
            instance?.setAudioRoute(route)
        }
    }
}
