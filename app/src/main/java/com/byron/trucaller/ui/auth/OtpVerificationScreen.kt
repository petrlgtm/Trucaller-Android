package com.byron.trucaller.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.theme.GlassBorder
import com.byron.trucaller.util.DeviceAdminHelper
import com.byron.trucaller.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    email: String
) {
    var otp by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(60) }
    var verificationSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val colorScheme = MaterialTheme.colorScheme

    val shakeOffset = remember { Animatable(0f) }

    val successScale by animateFloatAsState(
        targetValue = if (verificationSuccess) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "success_scale"
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(countdown) {
        if (countdown > 0) { delay(1000); countdown-- }
    }

    LaunchedEffect(error) {
        if (error != null) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0; -12f at 50; 12f at 100; -10f at 150
                    10f at 200; -6f at 250; 6f at 300; -2f at 350; 0f at 400
                }
            )
        }
    }

    fun onVerificationSuccess() { verificationSuccess = true }

    LaunchedEffect(verificationSuccess) {
        if (verificationSuccess) {
            delay(1200)
            navController.navigate("device_protection_prompt") {
                popUpTo("splash") { inclusive = true }
            }
        }
    }

    fun verifyOtp() {
        if (otp.length != 6) { error = "Enter all 6 digits"; return }
        error = null
        isLoading = true
        scope.launch {
            val codeValid = authViewModel.verifyEmailOtpViaBackend(email, otp)
            if (codeValid) {
                val success = authViewModel.verifyOtp(otp, backendVerified = true)
                isLoading = false
                if (success) onVerificationSuccess()
                else error = "Failed to create account. Please try again."
            } else {
                isLoading = false
                error = "Invalid or expired OTP code."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Text("Verify Email", fontWeight = FontWeight.Bold, color = colorScheme.onBackground)
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colorScheme.onBackground)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            if (verificationSuccess) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(successScale)
                        .background(colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Verified",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                AnimatedVisibility(
                    visible = verificationSuccess,
                    enter = fadeIn(tween(300, delayMillis = 300)) + scaleIn(tween(300, delayMillis = 300))
                ) {
                    Text("Verified!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                }
            } else {
                Text(
                    text = "Enter Verification Code",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "We sent a 6-digit code to", color = colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text(
                    text = email,
                    color = colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(40.dp))

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
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
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
                                        .height(56.dp)
                                        .border(
                                            width = if (isFocused) 2.dp else 1.dp,
                                            color = when {
                                                error != null -> colorScheme.error
                                                isFocused -> colorScheme.primary
                                                else -> GlassBorder
                                            },
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .background(colorScheme.surface, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onBackground,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = error!!, color = colorScheme.error, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))

                TruCallerButton(
                    text = "Verify",
                    onClick = { verifyOtp() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    isLoading = isLoading,
                    enabled = !isLoading && otp.length == 6
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (countdown > 0) {
                    Text(
                        text = "Resend code in ${countdown}s",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                } else {
                    TextButton(onClick = {
                        countdown = 60
                        otp = ""
                        error = null
                        scope.launch { authViewModel.sendEmailOtp(email, "registration") }
                    }) {
                        Text("Resend Code", color = colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "A verification code has been sent to your email address",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
