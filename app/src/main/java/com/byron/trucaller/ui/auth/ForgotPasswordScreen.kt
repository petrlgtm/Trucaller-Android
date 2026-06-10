package com.byron.trucaller.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.components.TruCallerTextField
import com.byron.trucaller.ui.theme.GlassBorder
import com.byron.trucaller.util.isValidPhoneInput
import com.byron.trucaller.util.maskPhoneNumber
import com.byron.trucaller.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(navController: NavController, authViewModel: AuthViewModel) {
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(1) } // 1=phone, 2=otp, 3=new password
    var success by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val colorScheme = MaterialTheme.colorScheme

    // Shake animation for error feedback
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(error) {
        if (error != null) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -12f at 50
                    12f at 100
                    -10f at 150
                    10f at 200
                    -6f at 250
                    6f at 300
                    -2f at 350
                    0f at 400
                }
            )
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
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
                Text(
                    "Reset Password",
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (step > 1 && !success) {
                        step--
                        error = null
                    } else {
                        navController.popBackStack()
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = colorScheme.onBackground
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.background
            )
        )

        // Step progress indicator
        if (!success) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val steps = listOf("Phone", "Verify", "Password")
                steps.forEachIndexed { index, label ->
                    val stepNum = index + 1
                    val isActive = step >= stepNum
                    val isCurrent = step == stepNum

                    // Circle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (isActive) colorScheme.primary else colorScheme.outlineVariant,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$stepNum",
                            color = if (isActive) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant
                    )
                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .width(28.dp)
                                .height(2.dp)
                                .background(
                                    if (step > stepNum) colorScheme.primary else colorScheme.outlineVariant,
                                    RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (success) {
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    "Password Reset Successfully!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "You can now login with your new password.",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                TruCallerButton(
                    text = "Back to Login",
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            } else when (step) {
                // Step 1: Enter phone number
                1 -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Reset Your Password",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Enter your phone number to receive a verification code",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    TruCallerTextField(
                        value = phone,
                        onValueChange = {
                            if (it.length <= 9) phone = it.filter { c -> c.isDigit() }
                            error = null
                        },
                        label = "Phone Number",
                        prefix = {
                            Text(
                                "+256 ",
                                color = colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        placeholder = "7XXXXXXXX",
                        keyboardType = KeyboardType.Phone,
                        isError = error != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset {
                                IntOffset(shakeOffset.value.roundToInt(), 0)
                            }
                    )

                    if (error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            error!!,
                            color = colorScheme.error,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    TruCallerButton(
                        text = "Send Verification Code",
                        onClick = {
                            if (!isValidPhoneInput(phone)) {
                                error = "Enter a valid 9-digit phone number"
                                return@TruCallerButton
                            }
                            isLoading = true
                            error = null
                            scope.launch {
                                val found = authViewModel.requestPasswordReset(phone)
                                isLoading = false
                                if (found) {
                                    step = 2
                                    countdown = 60
                                    otp = ""
                                } else {
                                    error = "Phone number not found"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        isLoading = isLoading,
                        enabled = !isLoading
                    )
                }

                // Step 2: Enter OTP
                2 -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Enter Verification Code",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "We sent a 6-digit code to",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Text(
                        text = maskPhoneNumber("+256$phone"),
                        color = colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    // OTP boxes with shake animation
                    BasicTextField(
                        value = otp,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                otp = it
                                error = null
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .offset {
                                IntOffset(shakeOffset.value.roundToInt(), 0)
                            },
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
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
                                            .background(
                                                colorScheme.surface,
                                                RoundedCornerShape(14.dp)
                                            ),
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

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    if (error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            error!!,
                            color = colorScheme.error,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    TruCallerButton(
                        text = "Verify Code",
                        onClick = {
                            if (otp.length != 6) {
                                error = "Enter all 6 digits"
                                return@TruCallerButton
                            }
                            isLoading = true
                            error = null
                            scope.launch {
                                val valid = authViewModel.verifyResetOtp(phone, otp)
                                isLoading = false
                                if (valid) {
                                    step = 3
                                } else {
                                    error = "Invalid or expired verification code"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        isLoading = isLoading,
                        enabled = otp.length == 6 && !isLoading
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Resend
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
                            scope.launch {
                                authViewModel.requestPasswordReset(phone)
                            }
                        }) {
                            Text(
                                "Resend Code",
                                color = colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Step 3: Set new password
                3 -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Set New Password",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Identity verified. Enter your new password below.",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    TruCallerTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; error = null },
                        label = "New Password",
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingContent = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password",
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        keyboardType = KeyboardType.Password,
                        isError = error != null && error!!.contains("6 characters", ignoreCase = true),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset {
                                IntOffset(shakeOffset.value.roundToInt(), 0)
                            }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TruCallerTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null },
                        label = "Confirm New Password",
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardType = KeyboardType.Password,
                        isError = error != null && error!!.contains("match", ignoreCase = true),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset {
                                IntOffset(shakeOffset.value.roundToInt(), 0)
                            }
                    )

                    if (error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            error!!,
                            color = colorScheme.error,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    TruCallerButton(
                        text = "Reset Password",
                        onClick = {
                            if (newPassword.length < 6) {
                                error = "Password must be at least 6 characters"
                                return@TruCallerButton
                            }
                            if (newPassword != confirmPassword) {
                                error = "Passwords do not match"
                                return@TruCallerButton
                            }
                            isLoading = true
                            error = null
                            scope.launch {
                                val result = authViewModel.resetPassword(phone, newPassword)
                                isLoading = false
                                if (result) {
                                    success = true
                                } else {
                                    error = "Failed to reset password. Please try again."
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        isLoading = isLoading,
                        enabled = !isLoading
                    )
                }
            }
        }
    }
}
