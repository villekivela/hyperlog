package com.hyperlog.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Hyperlog's own colours, Navy on grey (#42): neutral grey surfaces, text and dividers, and one navy
// accent, the same on every phone. The accent fills primary and primaryContainer (the Start button)
// and tints secondaryContainer (the selected tab, chips that are on); tertiary follows primary, and
// secondary and tertiaryContainer (advice cards, technique pills) stay neutral grey, so nothing else
// tints the screen. Light is white, dark near-black grey.
// Errors keep Material's defaults. surfaceTint is transparent so elevation never tints a surface.

internal val NavyLight = lightColorScheme(
    primary = Color(0xFF2B3A8F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2B3A8F),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFB4BEFF),
    surfaceTint = Color.Transparent,
    secondary = Color(0xFF5E5E64),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E6F7),
    onSecondaryContainer = Color(0xFF1B2560),
    tertiary = Color(0xFF2B3A8F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4E4E7),
    onTertiaryContainer = Color(0xFF1A1A1C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1A1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1C),
    surfaceVariant = Color(0xFFE8E8EB),
    onSurfaceVariant = Color(0xFF5E5E64),
    inverseSurface = Color(0xFF2E2F32),
    inverseOnSurface = Color(0xFFF1F1F3),
    outline = Color(0xFF8A8A90),
    outlineVariant = Color(0xFFD6D6DA),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFDCDCDF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F8),
    surfaceContainer = Color(0xFFF1F1F3),
    surfaceContainerHigh = Color(0xFFEBEBED),
    surfaceContainerHighest = Color(0xFFE4E4E7),
)

internal val NavyDark = darkColorScheme(
    primary = Color(0xFFB4BEFF),
    onPrimary = Color(0xFF16257D),
    primaryContainer = Color(0xFF3A49A8),
    onPrimaryContainer = Color(0xFFF0F1FF),
    inversePrimary = Color(0xFF3A49A8),
    surfaceTint = Color.Transparent,
    secondary = Color(0xFFC4C5CA),
    onSecondary = Color(0xFF2E2F32),
    secondaryContainer = Color(0xFF262C4A),
    onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = Color(0xFFB4BEFF),
    onTertiary = Color(0xFF16257D),
    tertiaryContainer = Color(0xFF303134),
    onTertiaryContainer = Color(0xFFE6E6E8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111214),
    onBackground = Color(0xFFE6E6E8),
    surface = Color(0xFF111214),
    onSurface = Color(0xFFE6E6E8),
    surfaceVariant = Color(0xFF2A2B2E),
    onSurfaceVariant = Color(0xFFA6A7AC),
    inverseSurface = Color(0xFFE6E6E8),
    inverseOnSurface = Color(0xFF2E2F32),
    outline = Color(0xFF7A7B80),
    outlineVariant = Color(0xFF3A3B3F),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF111214),
    surfaceBright = Color(0xFF37383B),
    surfaceContainerLowest = Color(0xFF0B0C0D),
    surfaceContainerLow = Color(0xFF17181A),
    surfaceContainer = Color(0xFF1C1D20),
    surfaceContainerHigh = Color(0xFF26272A),
    surfaceContainerHighest = Color(0xFF303134),
)
