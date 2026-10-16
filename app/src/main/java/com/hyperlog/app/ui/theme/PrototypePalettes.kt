package com.hyperlog.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// PROTOTYPE (issue #42), throwaway, never merged: palette options for "neutral surfaces and one
// sparing accent, like WhatsApp". Each option is neutral grey surfaces plus one accent hue. The
// accent fills primary and primaryContainer (the Start button) and tints secondaryContainer (the
// selected tab, selected chips); secondary stays neutral grey. Teal, green, amber and red are out
// because the RIR, progression and plate colours use them.

internal class PrototypePalette(val name: String, val light: ColorScheme, val dark: ColorScheme)

private class Accent(
    val primary: Color,
    val container: Color,
    val onContainer: Color,
    val tint: Color,
    val onTint: Color,
)

private fun neutralLight(a: Accent) = lightColorScheme(
    primary = a.primary,
    onPrimary = Color.White,
    primaryContainer = a.container,
    onPrimaryContainer = a.onContainer,
    inversePrimary = a.tint,
    surfaceTint = Color.Transparent,
    secondary = Color(0xFF5E5E64),
    onSecondary = Color.White,
    secondaryContainer = a.tint,
    onSecondaryContainer = a.onTint,
    tertiary = a.primary,
    onTertiary = Color.White,
    tertiaryContainer = a.tint,
    onTertiaryContainer = a.onTint,
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
    surfaceDim = Color(0xFFDCDCDF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F8),
    surfaceContainer = Color(0xFFF1F1F3),
    surfaceContainerHigh = Color(0xFFEBEBED),
    surfaceContainerHighest = Color(0xFFE4E4E7),
)

private fun neutralDark(a: Accent, onPrimary: Color) = darkColorScheme(
    primary = a.primary,
    onPrimary = onPrimary,
    primaryContainer = a.container,
    onPrimaryContainer = a.onContainer,
    inversePrimary = a.container,
    surfaceTint = Color.Transparent,
    secondary = Color(0xFFC4C5CA),
    onSecondary = Color(0xFF2E2F32),
    secondaryContainer = a.tint,
    onSecondaryContainer = a.onTint,
    tertiary = a.primary,
    onTertiary = onPrimary,
    tertiaryContainer = a.tint,
    onTertiaryContainer = a.onTint,
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
    surfaceDim = Color(0xFF111214),
    surfaceBright = Color(0xFF37383B),
    surfaceContainerLowest = Color(0xFF0B0C0D),
    surfaceContainerLow = Color(0xFF17181A),
    surfaceContainer = Color(0xFF1C1D20),
    surfaceContainerHigh = Color(0xFF26272A),
    surfaceContainerHighest = Color(0xFF303134),
)

internal val PrototypePalettes = listOf(
    PrototypePalette("Navy today (#37)", NavyLight, NavyDark),
    PrototypePalette(
        "A · Navy on grey",
        neutralLight(Accent(Color(0xFF2B3A8F), Color(0xFF2B3A8F), Color.White, Color(0xFFE3E6F7), Color(0xFF1B2560))),
        neutralDark(
            Accent(Color(0xFFB4BEFF), Color(0xFF3A49A8), Color(0xFFF0F1FF), Color(0xFF262C4A), Color(0xFFDDE1FF)),
            onPrimary = Color(0xFF16257D),
        ),
    ),
    PrototypePalette(
        "B · Grape",
        neutralLight(Accent(Color(0xFF6B3FD4), Color(0xFF5A2EC2), Color.White, Color(0xFFEEE7FC), Color(0xFF2C1466))),
        neutralDark(
            Accent(Color(0xFFCDB8FF), Color(0xFF6B45D6), Color(0xFFF6F1FF), Color(0xFF2E2546), Color(0xFFE8DEFF)),
            onPrimary = Color(0xFF3A1890),
        ),
    ),
    PrototypePalette(
        "C · Berry",
        neutralLight(Accent(Color(0xFFB0285F), Color(0xFF9E1F53), Color.White, Color(0xFFFBE4EC), Color(0xFF4A0F27))),
        neutralDark(
            Accent(Color(0xFFFFB0CA), Color(0xFFB5326A), Color(0xFFFFF0F5), Color(0xFF3D2230), Color(0xFFFFD9E5)),
            onPrimary = Color(0xFF5E1133),
        ),
    ),
)
