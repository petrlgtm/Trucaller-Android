package com.byron.trucaller.ui.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.service.PhoneAuthState
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Accent
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Divider
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.util.DeviceAdminHelper
import com.byron.trucaller.util.maskPhoneNumber
import com.byron.trucaller.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    phone: String
) {
    var otp by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(60) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val activity = context as? Activity
    val phoneAuthState by authViewModel.phoneAuthManager.state.collectAsState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    fun onVerificationSuccess() {
        // Auto-activate Device Admin protection (user gave consent during registration)
        if (!DeviceAdminHelper.isAdminActive(context) && activity != null) {
            DeviceAdminHelper.requestAdminPermission(activity)
        }
        navController.navigate("main") {
            popUpTo("splash") { inclusive = true }
        }
    }

    // Handle Firebase state changes
    LaunchedEffect(phoneAuthState) {
        when (phoneAuthState) {
            is PhoneAuthState.Verified -> {
                // Firebase verified the code — now create the user account
                isLoading = true
                val success = authViewModel.verifyOtp(otp, firebaseVerified = true)
                isLoading = false
                if (success) {
                    onVerificationSuccess()
                } else {
                    error = "Failed to create account"
                }
            }
            is PhoneAuthState.Error -> {
                isLoading = false
                error = (phoneAuthState as PhoneAuthState.Error).message
            }
            is PhoneAuthState.Verifying -> {
                isLoading = true
                error = null
            }
            else -> { }
        }
    }

    fun verifyOtp() {
        if (otp.length != 6) {
            error = "Enter all 6 digits"
            return
        }
        error = null

        if (authViewModel.isFirebaseAvailable()) {
            // Firebase mode: send code to Firebase for verification
            // The LaunchedEffect above handles the Verified/Error states
            isLoading = true
            authViewModel.phoneAuthManager.verifyCode(otp)
        } else {
            // Demo mode: local OTP check
            isLoading = true
            scope.launch {
                delay(500)
                val success = authViewModel.verifyOtp(otp)
                isLoading = false
                if (success) {
                    onVerificationSuccess()
                } else {
                    error = "Invalid OTP code. Check the hint below."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopAppBar(
            title = { Text("Verify Phone", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Enter Verification Code",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We sent a 6-digit code to",
                color = TextSecondary,
                fontSize = 14.sp
            )
            Text(
                text = maskPhoneNumber("+256$phone"),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(40.dp))

            // OTP boxes
            BasicTextField(
                value = otp,
                onValueChange = {
                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                        otp = it
                        error = null
                        if (it.length == 6) verifyOtp()
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.focusRequester(focusRequester),
                decorationBox = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(6) { index ->
                            val char = otp.getOrNull(index)?.toString() ?: ""
                            val isFocused = otp.length == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .border(
                                        width = if (isFocused) 2.dp else 1.5.dp,
                                        color = if (isFocused) Brand else if (char.isNotEmpty()) Brand else Divider,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .background(
                                        if (char.isNotEmpty()) Color(0xFF252525) else Background,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            )

            // Error
            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = error!!, color = Danger, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Verify button
            Button(
                onClick = { verifyOtp() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                enabled = !isLoading && otp.length == 6
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Brand, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Verify", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Resend
            if (countdown > 0) {
                Text(
                    text = "Resend code in ${countdown}s",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            } else {
                TextButton(onClick = {
                    countdown = 60
                    otp = ""
                    error = null
                    if (authViewModel.isFirebaseAvailable() && activity != null) {
                        authViewModel.phoneAuthManager.resendCode(phone, activity)
                    }
                }) {
                    Text("Resend Code", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!authViewModel.isFirebaseAvailable()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Background, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Demo Mode: Your OTP is ${authViewModel.getGeneratedOtp() ?: "------"}",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Background, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "A verification code has been sent to your phone via SMS",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
