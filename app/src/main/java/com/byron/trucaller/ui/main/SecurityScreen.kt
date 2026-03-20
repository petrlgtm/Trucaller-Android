package com.byron.trucaller.ui.main

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.ui.theme.Danger
import com.byron.trucaller.ui.theme.Divider
import com.byron.trucaller.ui.theme.Success
import com.byron.trucaller.ui.theme.TextPrimary
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(navController: NavController, authViewModel: AuthViewModel) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // PIN state
    var currentPin by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pinSuccess by remember { mutableStateOf<String?>(null) }
    var pinLoading by remember { mutableStateOf(false) }
    val hasPin = authViewModel.hasSecurityPin()

    // PIN change cooldown state
    var pinFailedAttempts by remember { mutableIntStateOf(0) }
    var pinCooldownUntil by remember { mutableLongStateOf(0L) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pinCooldownUntil) {
        while (pinCooldownUntil > 0 && System.currentTimeMillis() < pinCooldownUntil) {
            currentTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
        currentTime = System.currentTimeMillis()
    }
    val pinCooldownActive = pinCooldownUntil > currentTime

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopAppBar(
            title = { Text("Security", fontWeight = FontWeight.Bold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandDark)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Security PIN Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (hasPin) "Change Security PIN" else "Set Security PIN",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Your 4-digit PIN is required to report a stolen device",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    if (hasPin) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("PIN is set", color = Success, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Current PIN field (only when changing existing PIN)
                    if (hasPin) {
                        Text("Current PIN", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        PinInput(value = currentPin, onValueChange = { currentPin = it; pinError = null; pinSuccess = null })
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(if (hasPin) "New PIN" else "Enter PIN", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    PinInput(value = pin, onValueChange = { pin = it; pinError = null; pinSuccess = null })

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Confirm PIN", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    PinInput(value = confirmPin, onValueChange = { confirmPin = it; pinError = null; pinSuccess = null })

                    if (pinCooldownActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val remaining = ((pinCooldownUntil - currentTime) / 1000).coerceAtLeast(0)
                        Text(
                            "Too many failed attempts. Try again in ${remaining}s.",
                            color = Danger,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    } else if (pinError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(pinError!!, color = Danger, fontSize = 13.sp)
                    }
                    if (pinSuccess != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(pinSuccess!!, color = Success, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (pinCooldownActive) return@Button
                            // Verify current PIN if one exists
                            if (hasPin) {
                                if (currentPin.length != 4) {
                                    pinError = "Enter your current 4-digit PIN"
                                    return@Button
                                }
                                if (!authViewModel.verifySecurityPin(currentPin)) {
                                    pinFailedAttempts++
                                    currentPin = ""
                                    if (pinFailedAttempts >= 3) {
                                        pinCooldownUntil = System.currentTimeMillis() + 30_000L
                                        pinError = "Too many failed attempts. Try again in 30 seconds."
                                    } else {
                                        pinError = "Current PIN is incorrect (${3 - pinFailedAttempts} attempts remaining)"
                                    }
                                    return@Button
                                }
                            }
                            if (pin.length != 4) {
                                pinError = "PIN must be 4 digits"
                                return@Button
                            }
                            if (pin != confirmPin) {
                                pinError = "PINs do not match"
                                return@Button
                            }
                            pinLoading = true
                            pinError = null
                            pinSuccess = null
                            scope.launch {
                                val result = authViewModel.setSecurityPin(pin)
                                pinLoading = false
                                if (result) {
                                    pinSuccess = "Security PIN saved successfully!"
                                    currentPin = ""
                                    pin = ""
                                    confirmPin = ""
                                    pinFailedAttempts = 0
                                } else {
                                    pinError = "Failed to save PIN"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        enabled = !pinLoading && !pinCooldownActive && pin.length == 4 && confirmPin.length == 4 && (!hasPin || currentPin.length == 4)
                    ) {
                        if (pinLoading) {
                            CircularProgressIndicator(color = Brand, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (hasPin) "Update PIN" else "Set PIN",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Change Password Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Change Password", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Update your account password", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it; error = null; success = null },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, unfocusedBorderColor = Color(0xFF444444), focusedContainerColor = Color(0xFF252525), unfocusedContainerColor = Color(0xFF252525))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; error = null; success = null },
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
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, unfocusedBorderColor = Color(0xFF444444), focusedContainerColor = Color(0xFF252525), unfocusedContainerColor = Color(0xFF252525))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null; success = null },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Brand, unfocusedBorderColor = Color(0xFF444444), focusedContainerColor = Color(0xFF252525), unfocusedContainerColor = Color(0xFF252525))
                    )

                    if (error != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(error!!, color = Danger, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    if (success != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(success!!, color = Success, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (currentPassword.isEmpty()) {
                                error = "Enter your current password"
                                return@Button
                            }
                            if (newPassword.length < 6) {
                                error = "New password must be at least 6 characters"
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                error = "Passwords do not match"
                                return@Button
                            }
                            isLoading = true
                            error = null
                            success = null
                            scope.launch {
                                val result = authViewModel.changePassword(currentPassword, newPassword)
                                isLoading = false
                                if (result) {
                                    success = "Password changed successfully!"
                                    currentPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                } else {
                                    error = "Current password is incorrect"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Brand, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Update Password", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PinInput(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) onValueChange(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(color = Color.Transparent),
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val char = value.getOrNull(index)?.toString() ?: ""
                    val isFocused = value.length == index
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                if (isFocused) 2.dp else 1.5.dp,
                                if (isFocused || char.isNotEmpty()) Brand else Divider,
                                RoundedCornerShape(12.dp)
                            )
                            .background(
                                if (char.isNotEmpty()) Color(0xFF252525) else Background,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (char.isNotEmpty()) "\u2022" else "",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    )
}
