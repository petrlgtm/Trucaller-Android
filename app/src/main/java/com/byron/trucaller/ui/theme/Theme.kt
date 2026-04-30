package com.byron.trucaller.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColorScheme = lightColorScheme(
    primary = Brand,                        // True Blue
    onPrimary = BrandOnPrimary,             // White
    primaryContainer = BrandLight,          // Light Blue
    onPrimaryContainer = BrandDark,         // Dark Blue
    secondary = BrandDark,                  // Dark Blue for secondary accents
    onSecondary = Color.White,
    secondaryContainer = BrandLight,
    onSecondaryContainer = BrandDark,
    tertiary = Priority,                    // Purple
    onTertiary = Color.White,
    background = LightBackground,           // White
    onBackground = LightTextPrimary,        // Near black
    surface = LightSurface,                 // White
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    error = Danger,                         // Spam Red
    onError = Color.White,
    outline = LightDivider,
    outlineVariant = LightOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = Brand,                        // True Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFF002D6E),   // Deep Navy Blue
    onPrimaryContainer = BrandLight,
    secondary = BrandLight,
    onSecondary = BrandDark,
    tertiary = Priority,
    onTertiary = Color.White,
    background = Background,                // Pure Black
    onBackground = TextPrimary,             // White
    surface = Surface,                      // Dark Surface
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = Danger,
    onError = Color.White,
    outline = Divider,
    outlineVariant = Color(0xFF333333)
)

@Composable
fun TruCallerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use Brand color for status bar in Light mode to look like Truecaller app, 
            // or stick to white for cleaner look. Official app often has blue top bar.
            val barColor = if (darkTheme) Color.Black.toArgb() else LightBackground.toArgb()
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = MontserratFontFamily)
        ) {
            content()
        }
    }
}
