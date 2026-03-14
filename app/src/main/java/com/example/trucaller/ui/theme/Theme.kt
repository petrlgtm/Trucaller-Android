package com.example.trucaller.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Brand,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandLight,
    onPrimaryContainer = BrandDark,
    secondary = BrandDark,
    onSecondary = BrandOnPrimary,
    background = Background,
    onBackground = BrandOnBackground,
    surface = Surface,
    onSurface = BrandOnSurface,
    error = Danger,
    onError = BrandOnPrimary,
    outline = Divider
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandLight,
    onPrimary = BrandDark,
    primaryContainer = Brand,
    onPrimaryContainer = BrandOnPrimary,
    secondary = BrandLight,
    onSecondary = BrandDark,
    background = TextPrimary,
    onBackground = BrandOnPrimary,
    surface = TextPrimary,
    onSurface = BrandOnPrimary,
    error = DangerLight,
    onError = BrandDark,
    outline = Inactive
)

@Composable
fun TruCallerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
