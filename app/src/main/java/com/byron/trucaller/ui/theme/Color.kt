package com.byron.trucaller.ui.theme

import androidx.compose.ui.graphics.Color

// ── Official Truecaller Palette ──────────────────────────────────────────

// Primary — True Blue (interactive, buttons, accents)
val Brand = Color(0xFF0087FF)            // Official True Blue
val BrandDark = Color(0xFF0047AB)        // Official Dark Blue
val BrandLight = Color(0xFFE1F0FF)       // Soft light blue background
val BrandGold = Color(0xFFFFB347)        // Warm amber accent (Verified/Suspicious)

// Secondary — Spam Crimson
val Accent = Color(0xFFEB002B)           // Official Spam Red
val AccentLight = Color(0xFFFF4D6D)
val AccentDark = Color(0xFFA5001D)

// ── Dark Surfaces (Midnight/Deep Navy) ──────────────────────────────────
val Surface = Color(0xFF121212)          // Standard Material Dark Surface
val SurfaceElevated = Color(0xFF1E1E1E) // Elevated cards
val Background = Color(0xFF000000)      // Pure black background for Midnight theme
val BackgroundDark = Color(0xFF000000)
val SurfaceCard = Color(0xFF121212)
val SurfaceLight = Color(0xFF242424)

// ── Light Surfaces ──────────────────────────────────────────────────────
val LightBackground = Color(0xFFFFFFFF)  // Pure white
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFF5F7FA)
val LightSurfaceCard = Color(0xFFFFFFFF)

// ── Status Colors ───────────────────────────────────────────────────────
val Danger = Color(0xFFEB002B)          // Spam/Error
val DangerLight = Color(0xFFFF4D6D)
val DangerBg = Color(0x1AEB002B)
val Success = Color(0xFF24B024)         // Verified Green
val SuccessLight = Color(0xFF4ADE80)
val SuccessBg = Color(0x1A24B024)
val Warning = Color(0xFFFFB347)         // Yellow/Warning
val WarningLight = Color(0xFFFFCA6E)
val WarningBg = Color(0x1AFFB347)
val Priority = Color(0xFF8B3DFF)        // Priority/Verified Purple

// ── Text (Dark Mode) ────────────────────────────────────────────────────
val TextPrimary = Color(0xFFFFFFFF)     // Pure white
val TextSecondary = Color(0xFF9E9E9E)   // Grey sublabel
val TextDisabled = Color(0xFF616161)
val TextOnDark = Color(0xFFFFFFFF)
val TextOnYellow = Color(0xFF000000)

// ── Text (Light Mode) ───────────────────────────────────────────────────
val LightTextPrimary = Color(0xFF111111) // Near black
val LightTextSecondary = Color(0xFF5F6368) // Google/Standard grey

// ── Utility ─────────────────────────────────────────────────────────────
val Inactive = Color(0xFFBDBDBD)
val Divider = Color(0xFF2A2A2A)
val Overlay = Color(0x80000000)
val Shimmer = Color(0x330087FF)

val LightDivider = Color(0xFFEEEEEE)
val LightOutlineVariant = Color(0xFFE0E0E0)

// ── Borders ─────────────────────────────────────────────────────────────
val GlassBorder = Color(0x1FFFFFFF)
val GlassBorderLight = Color(0x0DFFFFFF)

// ── Material3 ───────────────────────────────────────────────────────────
val BrandOnPrimary = Color(0xFFFFFFFF)  // White on Blue
val BrandOnBackground = Color(0xFF111111)
val BrandOnSurface = Color(0xFF111111)

// ── Gradients ───────────────────────────────────────────────────────────
val CardGradientStart = Color(0xFF121212)
val CardGradientEnd = Color(0xFF1E1E1E)
val BlueGradientStart = Color(0xFF0087FF)
val BlueGradientEnd = Color(0xFF0047AB)
val RedGradientStart = Color(0xFFEB002B)
val RedGradientEnd = Color(0xFFA5001D)

// ── Legacy/Logo-Specific (Aligned to new palette) ───────────────────────
val LogoBlue = Brand
val LogoBlueDark = BrandDark
val LogoBlueLight = Color(0xFF4B9EFF)
val LogoCrimson = Accent
val LogoCrimsonLight = AccentLight
val LogoGold = BrandGold
val LogoGoldLight = WarningLight
val LogoSilver = Color(0xFF8899AA)
val LogoSilverLight = Color(0xFFAABBCC)
