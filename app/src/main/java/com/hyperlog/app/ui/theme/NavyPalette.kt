package com.hyperlog.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Hyperlog's own colours: Material 3 light and dark schemes from the tonal palettes of Navy, seed
// #16236E (HCT, material-color-utilities), the same on every phone. Dark mode departs from the
// generated scheme twice: its surfaces are a deeper, blue-tinted navy instead of neutral greys, and
// its primaryContainer is light mode's primary (#303E94) with near-white text, so the Start button
// stands off those surfaces. Errors keep Material's defaults.

internal val NavyLight = lightColorScheme(
    primary = Color(0xFF303E94),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1B2A81),
    onPrimaryContainer = Color(0xFFE6E6FF),
    inversePrimary = Color(0xFFBBC3FF),
    surfaceTint = Color(0xFF4957AD),
    secondary = Color(0xFF5A5D75),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE0FD),
    onSecondaryContainer = Color(0xFF171A2E),
    tertiary = Color(0xFF63597C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF1F1635),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE2E1EF),
    onSurfaceVariant = Color(0xFF454651),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2EFF7),
    outline = Color(0xFF767682),
    outlineVariant = Color(0xFFC6C5D3),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFDBD9E0),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
)

internal val NavyDark = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    onPrimary = Color(0xFF16257D),
    primaryContainer = Color(0xFF303E94),
    onPrimaryContainer = Color(0xFFF0EFFF),
    inversePrimary = Color(0xFF4957AD),
    surfaceTint = Color(0xFFBBC3FF),
    secondary = Color(0xFFC3C5E0),
    onSecondary = Color(0xFF2C2F44),
    secondaryContainer = Color(0xFF42455C),
    onSecondaryContainer = Color(0xFFDFE0FD),
    tertiary = Color(0xFFCEC0E8),
    onTertiary = Color(0xFF342B4B),
    tertiaryContainer = Color(0xFF4B4163),
    onTertiaryContainer = Color(0xFFE9DDFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D1021),
    onBackground = Color(0xFFE0E1F9),
    surface = Color(0xFF0D1021),
    onSurface = Color(0xFFE0E1F9),
    surfaceVariant = Color(0xFF42455F),
    onSurfaceVariant = Color(0xFFC2C4E4),
    inverseSurface = Color(0xFFE0E1F9),
    inverseOnSurface = Color(0xFF2D2F42),
    outline = Color(0xFF8C8FAC),
    outlineVariant = Color(0xFF42455F),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF0D1021),
    surfaceBright = Color(0xFF313446),
    surfaceContainerLowest = Color(0xFF070A1B),
    surfaceContainerLow = Color(0xFF141628),
    surfaceContainer = Color(0xFF181A2C),
    surfaceContainerHigh = Color(0xFF222537),
    surfaceContainerHighest = Color(0xFF2D2F42),
)
