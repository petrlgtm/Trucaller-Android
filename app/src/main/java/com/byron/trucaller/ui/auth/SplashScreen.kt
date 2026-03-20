package com.byron.trucaller.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.ui.theme.Accent
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.BrandGold
import com.byron.trucaller.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun SplashScreen(navController: NavController, authViewModel: AuthViewModel) {
    var iconVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }
    var taglineVisible by remember { mutableStateOf(false) }
    var barVisible by remember { mutableStateOf(false) }

    // Icon scale + alpha
    val iconScale by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0.3f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "icon_scale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0f,
        animationSpec = tween(600),
        label = "icon_alpha"
    )

    // Text slide-up + fade
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(500),
        label = "text_alpha"
    )
    val textOffset by animateFloatAsState(
        targetValue = if (textVisible) 0f else 20f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "text_offset"
    )

    // Tagline fade
    val taglineAlpha by animateFloatAsState(
        targetValue = if (taglineVisible) 1f else 0f,
        animationSpec = tween(400),
        label = "tagline_alpha"
    )

    // Color bar reveal
    val barAlpha by animateFloatAsState(
        targetValue = if (barVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "bar_alpha"
    )

    LaunchedEffect(Unit) {
        delay(200)
        iconVisible = true
        delay(400)
        textVisible = true
        delay(300)
        taglineVisible = true
        delay(200)
        barVisible = true

        // Wait for auth state
        delay(1000)
        val currentState = authViewModel.authState.first()
        delay(300)

        if (currentState.isAuthenticated) {
            val promptShown = authViewModel.isDeviceProtectionPromptShown()
            val destination = if (promptShown) "main" else "device_protection_prompt"
            navController.navigate(destination) {
                popUpTo("splash") { inclusive = true }
            }
        } else {
            navController.navigate("login") {
                popUpTo("splash") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Shield icon with yellow glow ring
            Box(
                modifier = Modifier
                    .scale(iconScale)
                    .alpha(iconAlpha),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Brand.copy(alpha = 0.2f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                // Inner icon circle
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Brand, BrandGold)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Shield",
                        tint = BrandDark,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App name with slide up
            Text(
                text = "TruCaller",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                modifier = Modifier
                    .alpha(textAlpha)
                    .offset(y = textOffset.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Secure. Identify. Protect.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                modifier = Modifier.alpha(taglineAlpha)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Uganda flag color bar
            Box(
                modifier = Modifier
                    .alpha(barAlpha)
                    .width(60.dp)
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(BrandDark, Brand, Accent)
                        ),
                        RoundedCornerShape(2.dp)
                    )
            )
        }

        // Version text at bottom
        Text(
            text = "v1.0",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(taglineAlpha)
        )
    }
}
