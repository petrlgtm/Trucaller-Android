package com.byron.trucaller.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.components.TruCallerTextField
import com.byron.trucaller.util.getPasswordStrength
import com.byron.trucaller.util.isValidPassword
import com.byron.trucaller.util.isValidPhoneInput
import com.byron.trucaller.viewmodel.AuthViewModel
import android.util.Patterns
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(navController: NavController, authViewModel: AuthViewModel) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var consentChecked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val strength = getPasswordStrength(password)
    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Create Account",
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Full name
            TruCallerTextField(
                value = fullName,
                onValueChange = { fullName = it; error = null },
                label = "Full Name",
                keyboardType = KeyboardType.Text,
                isError = error != null && error!!.contains("name", ignoreCase = true),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_name_input")
                    .offset {
                        IntOffset(shakeOffset.value.roundToInt(), 0)
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Phone
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
                isError = error != null && error!!.contains("phone", ignoreCase = true),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_phone_input")
                    .offset {
                        IntOffset(shakeOffset.value.roundToInt(), 0)
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Email
            TruCallerTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = "Email Address",
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                isError = error != null && error!!.contains("email", ignoreCase = true),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_email_input")
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password
            TruCallerTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "Password",
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
                isError = error != null && error!!.contains("password", ignoreCase = true)
                        && !error!!.contains("match", ignoreCase = true),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_password_input")
                    .offset {
                        IntOffset(shakeOffset.value.roundToInt(), 0)
                    }
            )

            // Password strength bar
            if (password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val barColor = when (strength) {
                        "weak" -> colorScheme.error
                        "medium" -> colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val barWidth = when (strength) {
                        "weak" -> 0.33f
                        "medium" -> 0.66f
                        else -> 1f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                colorScheme.outlineVariant,
                                RoundedCornerShape(2.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barWidth)
                                .height(4.dp)
                                .background(barColor, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strength.replaceFirstChar { it.uppercase() },
                        color = barColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm password
            TruCallerTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null },
                label = "Confirm Password",
                visualTransformation = PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
                isError = error != null && error!!.contains("match", ignoreCase = true),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_confirm_password_input")
                    .offset {
                        IntOffset(shakeOffset.value.roundToInt(), 0)
                    }
            )

            // Error
            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error!!,
                    color = colorScheme.error,
                    fontSize = 14.sp,
                    modifier = Modifier.testTag("register_error_text")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { consentChecked = !consentChecked }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = consentChecked,
                    onCheckedChange = { consentChecked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colorScheme.primary,
                        checkmarkColor = colorScheme.onPrimary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "I agree to the Terms of Service",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        "By creating an account, I consent to TruCaller collecting my phone number, contacts, and device information for caller identification, anti-theft protection, and central backup services. My data will be stored securely and used only for app functionality.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Register button using TruCallerButton
            TruCallerButton(
                text = "Create Account",
                testTag = "register_create_account_button",
                onClick = {
                    when {
                        fullName.isBlank() -> error = "Full name is required"
                        !isValidPhoneInput(phone) -> error = "Enter a valid 9-digit phone number"
                        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> error = "Enter a valid email address"
                        !isValidPassword(password) -> error = "Password must be at least 6 characters"
                        password != confirmPassword -> error = "Passwords do not match"
                        !consentChecked -> error = "You must agree to the terms to continue"
                        else -> {
                            isLoading = true
                            error = null
                            scope.launch {
                                authViewModel.register(fullName, phone, email.trim(), password)
                                val sendError = authViewModel.sendEmailOtp(email.trim(), "registration")
                                isLoading = false
                                if (sendError == null) {
                                    navController.navigate("otp_email/${email.trim()}")
                                } else {
                                    error = sendError
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                isLoading = isLoading,
                enabled = !isLoading && consentChecked
            )
        }
    }
}
