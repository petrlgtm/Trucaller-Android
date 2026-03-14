package com.example.trucaller.ui.auth

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trucaller.ui.theme.Background
import com.example.trucaller.ui.theme.Brand
import com.example.trucaller.ui.theme.BrandDark
import com.example.trucaller.ui.theme.Danger
import com.example.trucaller.ui.theme.Divider
import com.example.trucaller.ui.theme.TextPrimary
import com.example.trucaller.ui.theme.TextSecondary
import com.example.trucaller.util.isValidPhoneInput
import com.example.trucaller.util.maskPhoneNumber
import com.example.trucaller.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopAppBar(
            title = { Text("Reset Password", fontWeight = FontWeight.Bold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = {
                    if (step > 1 && !success) {
                        step--
                        error = null
                    } else {
                        navController.popBackStack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (success) {
                Spacer(modifier = Modifier.height(40.dp))
                Text("Password Reset Successfully!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand)
                Spacer(modifier = Modifier.height(12.dp))
                Text("You can now login with your new password.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand)
                ) {
                    Text("Back to Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else when (step) {
                // Step 1: Enter phone number
                1 -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Reset Your Password", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Enter your phone number to receive a verification code", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { if (it.length <= 9) phone = it.filter { c -> c.isDigit() }; error = null },
                        label = { Text("Phone Number") },
                        prefix = { Text("+256 ", color = TextSecondary) },
                        placeholder = { Text("7XXXXXXXX") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand)
                    )

                    if (error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(error!!, color = Danger, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (!isValidPhoneInput(phone)) {
                                error = "Enter a valid 9-digit phone number"
                                return@Button
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
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Send Verification Code", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Step 2: Enter OTP
                2 -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Enter Verification Code", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("We sent a 6-digit code to", color = TextSecondary, fontSize = 14.sp)
                    Text(
                        text = maskPhoneNumber("+256$phone"),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    // OTP boxes
                    BasicTextField(
                        value = otp,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                otp = it
                                error = null
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.focusRequester(focusRequester),
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
                                            .height(52.dp)
                                            .border(
                                                width = if (isFocused) 2.dp else 1.5.dp,
                                                color = if (isFocused) Brand else if (char.isNotEmpty()) Brand else Divider,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .background(
                                                if (char.isNotEmpty()) Color.White else Background,
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

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    if (error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(error!!, color = Danger, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (otp.length != 6) {
                                error = "Enter all 6 digits"
                                return@Button
                            }
                            if (authViewModel.verifyResetOtp(otp)) {
                                step = 3
                                error = null
                            } else {
                                error = "Invalid verification code"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        enabled = otp.length == 6
                    ) {
                        Text("Verify Code", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                            scope.launch {
                                authViewModel.requestPasswordReset(phone)
                            }
                        }) {
                            Text("Resend Code", color = Brand, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Demo mode hint
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Background, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Demo Mode: Your OTP is ${authViewModel.getResetOtp() ?: "------"}",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Step 3: Set new password
                3 -> {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Set New Password", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Identity verified. Enter your new password below.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; error = null },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand)
                    )

                    if (error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(error!!, color = Danger, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (newPassword.length < 6) {
                                error = "Password must be at least 6 characters"
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                error = "Passwords do not match"
                                return@Button
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
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Reset Password", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
