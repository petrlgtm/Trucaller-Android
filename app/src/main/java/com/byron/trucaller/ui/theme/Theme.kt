package com.byron.trucaller.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Brand,                    // Yellow
    onPrimary = BrandDark,              // Black on yellow
    primaryContainer = BrandLight,
    onPrimaryContainer = BrandDark,
    secondary = Accent,                 // Red
    onSecondary = Color.White,
    secondaryContainer = AccentLight,
    onSecondaryContainer = Color.White,
    tertiary = BrandGold,
    onTertiary = BrandDark,
    background = Background,            // Dark background
    onBackground = TextPrimary,         // Light text
    surface = Surface,                  // Dark surface
    onSurface = TextPrimary,            // Light text
    surfaceVariant = SurfaceElevated,
    error = Danger,                     // Red
    onError = Color.White,
    outline = Divider,
    outlineVariant = Color(0xFF333333)
)

private val DarkColorScheme = darkColorScheme(
    primary = Brand,                    // Yellow stays bold in dark
    onPrimary = BrandDark,              // Black on yellow
    primaryContainer = Color(0xFF3A3A00),
    onPrimaryContainer = BrandLight,
    secondary = AccentLight,            // Red
    onSecondary = Color.White,
    tertiary = BrandGold,
    onTertiary = Color.White,
    background = BackgroundDark,
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2A2A),
    error = DangerLight,
    onError = Color.White,
    outline = Color(0xFF444444),
    outlineVariant = Color(0xFF333333)
)

@Composable
fun TruCallerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Set status bar appearance
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BrandDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
