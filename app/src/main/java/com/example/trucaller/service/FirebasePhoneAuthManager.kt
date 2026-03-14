package com.example.trucaller.service

import android.app.Activity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class FirebasePhoneAuthManager {

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    private val _state = MutableStateFlow<PhoneAuthState>(PhoneAuthState.Idle)
    val state: StateFlow<PhoneAuthState> = _state.asStateFlow()

    fun isFirebaseAvailable(): Boolean {
        return try {
            FirebaseApp.getInstance()
            FirebaseAuth.getInstance()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun sendVerificationCode(phoneNumber: String, activity: Activity) {
        if (!isFirebaseAvailable()) {
            _state.value = PhoneAuthState.FirebaseNotConfigured
            return
        }

        _state.value = PhoneAuthState.Sending

        val fullPhone = if (phoneNumber.startsWith("+")) phoneNumber else "+256$phoneNumber"

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-verification (e.g., on same device)
                _state.value = PhoneAuthState.AutoVerified(credential)
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                _state.value = PhoneAuthState.Error(e.message ?: "Verification failed")
            }

            override fun onCodeSent(
                vId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = vId
                resendToken = token
                _state.value = PhoneAuthState.CodeSent
            }
        }

        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(fullPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyCode(code: String): Boolean {
        val vId = verificationId ?: return false
        return try {
            val credential = PhoneAuthProvider.getCredential(vId, code)
            _state.value = PhoneAuthState.Verifying(credential)
            true
        } catch (e: Exception) {
            _state.value = PhoneAuthState.Error("Invalid code")
            false
        }
    }

    fun resendCode(phoneNumber: String, activity: Activity) {
        if (!isFirebaseAvailable()) return

        val fullPhone = if (phoneNumber.startsWith("+")) phoneNumber else "+256$phoneNumber"
        _state.value = PhoneAuthState.Sending

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                _state.value = PhoneAuthState.AutoVerified(credential)
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                _state.value = PhoneAuthState.Error(e.message ?: "Resend failed")
            }

            override fun onCodeSent(
                vId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = vId
                resendToken = token
                _state.value = PhoneAuthState.CodeSent
            }
        }

        val optionsBuilder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(fullPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        resendToken?.let { optionsBuilder.setForceResendingToken(it) }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    fun reset() {
        verificationId = null
        resendToken = null
        _state.value = PhoneAuthState.Idle
    }
}

sealed class PhoneAuthState {
    data object Idle : PhoneAuthState()
    data object Sending : PhoneAuthState()
    data object CodeSent : PhoneAuthState()
    data class AutoVerified(val credential: PhoneAuthCredential) : PhoneAuthState()
    data class Verifying(val credential: PhoneAuthCredential) : PhoneAuthState()
    data class Error(val message: String) : PhoneAuthState()
    data object FirebaseNotConfigured : PhoneAuthState()
}
