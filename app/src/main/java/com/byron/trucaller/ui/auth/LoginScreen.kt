package com.byron.trucaller.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import com.byron.trucaller.R
import com.byron.trucaller.ui.theme.GlassBorder
import com.byron.trucaller.ui.theme.GreatVibesFontFamily
import com.byron.trucaller.ui.theme.MontserratFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.components.TruCallerButton
import com.byron.trucaller.ui.components.TruCallerTextField
import com.byron.trucaller.util.isValidPhoneInput
import com.byron.trucaller.viewmodel.AuthViewModel
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LoginScreen(navController: NavController, authViewModel: AuthViewModel) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Shake animation for error feedback
    val shakeOffset = remember { Animatable(0f) }

    // Staggered entrance animation
    var showLogo by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var showLinks by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showLogo = true
        delay(200)
        showForm = true
        delay(150)
        showLinks = true
    }

    val logoScale by animateFloatAsState(
        targetValue = if (showLogo) 1f else 0.5f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "logo_scale"
    )

    val colorScheme = MaterialTheme.colorScheme

    // Trigger shake animation when error changes
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.background,
                        colorScheme.background,
                        colorScheme.surface
                    ),
                    startY = 0f,
                    endY = 600f
                )
            )
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header with contextual background image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1512499617640-c74ae3a79d37?auto=format&fit=crop&w=800&h=560&q=80",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colorScheme.background.copy(alpha = 0.3f),
                                colorScheme.background.copy(alpha = 0.7f),
                                colorScheme.background
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo with scale animation — no clip, let the transparent edges show
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.trucaller_logo),
                    contentDescription = "TruCaller Logo",
                    modifier = Modifier
                        .scale(logoScale)
                        .size(110.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Welcome Back",
                    fontFamily = GreatVibesFontFamily,
                    fontSize = 36.sp,
                    color = colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sign in to your account",
                    fontFamily = MontserratFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Form card
        AnimatedVisibility(
            visible = showForm,
            enter = slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(400))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        colorScheme.surface,
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                // Phone input
                Text(
                    "Phone Number",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                TruCallerTextField(
                    value = phone,
                    onValueChange = {
                        if (it.length <= 9) phone = it.filter { c -> c.isDigit() }
                        error = null
                    },
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
                        .testTag("login_phone_input")
                        .offset {
                            IntOffset(shakeOffset.value.roundToInt(), 0)
                        }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Password input
                Text(
                    "Password",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                TruCallerTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
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
                    isError = error != null && error!!.contains("password", ignoreCase = true),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                        .offset {
                            IntOffset(shakeOffset.value.roundToInt(), 0)
                        }
                )

                // Forgot password
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Forgot Password?",
                        color = colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { navController.navigate("forgot_password") }
                    )
                }

                // Error
                AnimatedVisibility(visible = error != null) {
                    Text(
                        text = error ?: "",
                        color = colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_error_text")
                            .padding(top = 12.dp)
                            .background(
                                colorScheme.error.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Login button using TruCallerButton
                TruCallerButton(
                    text = "Sign In",
                    testTag = "login_sign_in_button",
                    onClick = {
                        if (!isValidPhoneInput(phone)) {
                            error = "Enter a valid 9-digit phone number"
                            return@TruCallerButton
                        }
                        if (password.length < 6) {
                            error = "Password must be at least 6 characters"
                            return@TruCallerButton
                        }
                        isLoading = true
                        error = null
                        scope.launch {
                            delay(800)
                            val success = authViewModel.login(phone, password)
                            isLoading = false
                            if (success) {
                                val promptShown = authViewModel.isDeviceProtectionPromptShown()
                                val destination = if (promptShown) "main" else "device_protection_prompt"
                                navController.navigate(destination) {
                                    popUpTo("splash") { inclusive = true }
                                }
                            } else {
                                error = "Invalid phone number or password"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    isLoading = isLoading,
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(GlassBorder)
                    )
                    Text(
                        "or",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(GlassBorder)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Register link
                AnimatedVisibility(
                    visible = showLinks,
                    enter = fadeIn(tween(300))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Don't have an account? ",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Register",
                                color = colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { navController.navigate("register") }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Admin login
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Admin? ",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Login as Admin",
                                color = colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { navController.navigate("admin_login") }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Logo color accent bar
                        Row(
                            modifier = Modifier.width(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .background(
                                        colorScheme.primary.copy(alpha = 0.3f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .background(
                                        colorScheme.primary,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .background(
                                        colorScheme.primary.copy(alpha = 0.3f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
