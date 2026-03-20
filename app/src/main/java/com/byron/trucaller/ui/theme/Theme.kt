package com.byron.trucaller.ui.theme

import android.app.Activity
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

/** User-selectable theme mode, persisted via [com.byron.trucaller.util.ThemePreferences]. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LightColorScheme = lightColorScheme(
    primary = Brand,                        // Uganda Yellow
    onPrimary = BrandDark,                  // Black on yellow
    primaryContainer = BrandLight,
    onPrimaryContainer = BrandDark,
    secondary = Accent,                     // Uganda Red
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD4), // Light red container
    onSecondaryContainer = AccentDark,
    tertiary = BrandGold,
    onTertiary = BrandDark,
    background = LightBackground,           // Off-white (#FAFAFA)
    onBackground = LightTextPrimary,        // Dark text
    surface = LightSurface,                 // White (#FFFFFF)
    onSurface = LightTextPrimary,           // Dark text
    surfaceVariant = LightSurfaceElevated,  // Light grey (#F5F5F5)
    onSurfaceVariant = LightTextSecondary,
    error = Danger,                         // Uganda Red
    onError = Color.White,
    outline = LightDivider,                 // #E0E0E0
    outlineVariant = LightOutlineVariant    // #BDBDBD
)

private val DarkColorScheme = darkColorScheme(
    primary = Brand,                        // Yellow stays bold in dark
    onPrimary = BrandDark,                  // Black on yellow
    primaryContainer = Color(0xFF3A3A00),
    onPrimaryContainer = BrandLight,
    secondary = AccentLight,                // Red
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
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    },
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Set status bar appearance
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) BrandDark.toArgb() else Brand.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
