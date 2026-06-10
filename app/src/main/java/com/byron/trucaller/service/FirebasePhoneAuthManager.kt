package com.byron.trucaller.service

import android.app.Activity
import com.byron.trucaller.BuildConfig
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
                // Auto-verification (e.g., on same device) — sign in immediately
                signInWithCredential(credential)
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

    fun verifyCode(code: String) {
        val vId = verificationId
        if (vId == null) {
            _state.value = PhoneAuthState.Error("No verification in progress")
            return
        }
        try {
            val credential = PhoneAuthProvider.getCredential(vId, code)
            signInWithCredential(credential)
        } catch (e: Exception) {
            _state.value = PhoneAuthState.Error("Invalid code")
        }
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        _state.value = PhoneAuthState.Verifying
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener {
                _state.value = PhoneAuthState.Verified
            }
            .addOnFailureListener { e ->
                _state.value = PhoneAuthState.Error(e.message ?: "Verification failed")
            }
    }

    fun resendCode(phoneNumber: String, activity: Activity) {
        if (!isFirebaseAvailable()) return

        val fullPhone = if (phoneNumber.startsWith("+")) phoneNumber else "+256$phoneNumber"
        _state.value = PhoneAuthState.Sending

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithCredential(credential)
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

    /**
     * Registers a fictional phone number + SMS code pair for instant OTP auto-retrieval.
     *
     * Call this BEFORE [sendVerificationCode] in a test scenario. When
     * [PhoneAuthProvider.verifyPhoneNumber] is called for the registered number,
     * Firebase fires [PhoneAuthProvider.OnVerificationStateChangedCallbacks.onVerificationCompleted]
     * immediately with a [PhoneAuthCredential] — no real SMS is sent.
     *
     * Requirements:
     *  - The phone number must be whitelisted in the Firebase Console
     *    (Authentication → Phone → Test phone numbers).
     *  - This method is a no-op in release builds ([BuildConfig.DEBUG] == false).
     *  - NEVER ship hardcoded fictional numbers or calls to this function in production.
     */
    fun configureTestAutoRetrieval(phoneNumber: String, smsCode: String) {
        if (!BuildConfig.DEBUG) return
        if (!isFirebaseAvailable()) return
        val auth = FirebaseAuth.getInstance()
        auth.firebaseAuthSettings.setAutoRetrievedSmsCodeForPhoneNumber(phoneNumber, smsCode)
    }

    fun signOut() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) { }
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
    data object Verifying : PhoneAuthState()
    data object Verified : PhoneAuthState()
    data class Error(val message: String) : PhoneAuthState()
    data object FirebaseNotConfigured : PhoneAuthState()
}
