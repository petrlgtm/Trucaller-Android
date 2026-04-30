package com.byron.trucaller.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.byron.trucaller.R
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.Background
import com.byron.trucaller.ui.theme.GreatVibesFontFamily
import com.byron.trucaller.ui.theme.MontserratFontFamily
import com.byron.trucaller.ui.theme.TextSecondary
import com.byron.trucaller.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun SplashScreen(navController: NavController, authViewModel: AuthViewModel) {
    var iconVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }
    var taglineVisible by remember { mutableStateOf(false) }
    var barVisible by remember { mutableStateOf(false) }
    var loadingVisible by remember { mutableStateOf(false) }

    val iconScale by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0.3f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "icon_scale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0f,
        animationSpec = tween(700),
        label = "icon_alpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(500),
        label = "text_alpha"
    )
    val textOffset by animateFloatAsState(
        targetValue = if (textVisible) 0f else 24f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "text_offset"
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (taglineVisible) 1f else 0f,
        animationSpec = tween(500),
        label = "tagline_alpha"
    )
    val barAlpha by animateFloatAsState(
        targetValue = if (barVisible) 1f else 0f,
        animationSpec = tween(400),
        label = "bar_alpha"
    )
    val loadingAlpha by animateFloatAsState(
        targetValue = if (loadingVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "loading_alpha"
    )

    // Pulsing glow ring
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    LaunchedEffect(Unit) {
        delay(200)
        iconVisible = true
        delay(500)
        textVisible = true
        delay(300)
        taglineVisible = true
        delay(200)
        barVisible = true
        delay(300)
        loadingVisible = true

        delay(800)
        val currentState = authViewModel.authState.first()
        delay(200)

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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BrandDark,
                        Background,
                        BrandDark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Full logo — icon + "Truecaller" text from the brand image
            Box(
                modifier = Modifier
                    .scale(iconScale)
                    .alpha(iconAlpha),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing glow behind logo
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .scale(glowScale)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Brand.copy(alpha = glowAlpha),
                                    Brand.copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )
                // Full logo image (icon + text)
                Image(
                    painter = painterResource(R.drawable.trucaller_logo_full),
                    contentDescription = "TruCaller Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(260.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tagline below the logo in Great Vibes
            Text(
                text = "Secure · Identify · Protect",
                fontFamily = GreatVibesFontFamily,
                color = Brand.copy(alpha = 0.5f),
                fontSize = 20.sp,
                modifier = Modifier.alpha(taglineAlpha)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Logo color bar
            Box(
                modifier = Modifier
                    .alpha(barAlpha)
                    .width(72.dp)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Brand.copy(alpha = 0.3f), Brand, Brand.copy(alpha = 0.3f))
                        ),
                        RoundedCornerShape(2.dp)
                    )
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Loading spinner
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .alpha(loadingAlpha),
                color = Brand,
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Round
            )
        }

        // Version text at bottom
        Text(
            text = "v1.0",
            color = TextSecondary.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .alpha(taglineAlpha)
        )
    }
}
