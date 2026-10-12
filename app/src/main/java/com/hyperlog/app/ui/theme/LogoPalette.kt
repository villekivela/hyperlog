package com.hyperlog.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The fallback for phones without dynamic colour (Android < 12), built from the logo: white on
// #1A1A1A, with neutral greys between. Errors keep Material's defaults.

private val LogoInk = Color(0xFF1A1A1A)

internal val LogoLightColorScheme = lightColorScheme(
    primary = LogoInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF1B1B1B),
    inversePrimary = Color(0xFFC6C6C6),
    secondary = Color(0xFF5E5E5E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E2E2),
    onSecondaryContainer = Color(0xFF1B1B1B),
    tertiary = Color(0xFF474747),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDADADA),
    onTertiaryContainer = Color(0xFF1B1B1B),
    background = Color(0xFFF9F9F9),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFF9F9F9),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFE2E2E2),
    onSurfaceVariant = Color(0xFF474747),
    surfaceTint = LogoInk,
    inverseSurface = Color(0xFF303030),
    inverseOnSurface = Color(0xFFF1F1F1),
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFFC6C6C6),
    surfaceBright = Color(0xFFF9F9F9),
    surfaceDim = Color(0xFFDADADA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3F3),
    surfaceContainer = Color(0xFFEEEEEE),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE2E2E2),
)

internal val LogoDarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = LogoInk,
    primaryContainer = Color(0xFF474747),
    onPrimaryContainer = Color(0xFFE2E2E2),
    inversePrimary = Color(0xFF5E5E5E),
    secondary = Color(0xFFC6C6C6),
    onSecondary = Color(0xFF303030),
    secondaryContainer = Color(0xFF474747),
    onSecondaryContainer = Color(0xFFE2E2E2),
    tertiary = Color(0xFFC6C6C6),
    onTertiary = Color(0xFF303030),
    tertiaryContainer = Color(0xFF5E5E5E),
    onTertiaryContainer = Color(0xFFF1F1F1),
    background = Color(0xFF131313),
    onBackground = Color(0xFFE2E2E2),
    surface = Color(0xFF131313),
    onSurface = Color(0xFFE2E2E2),
    surfaceVariant = Color(0xFF474747),
    onSurfaceVariant = Color(0xFFC6C6C6),
    surfaceTint = Color.White,
    inverseSurface = Color(0xFFE2E2E2),
    inverseOnSurface = Color(0xFF303030),
    outline = Color(0xFF919191),
    outlineVariant = Color(0xFF474747),
    surfaceBright = Color(0xFF393939),
    surfaceDim = Color(0xFF131313),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = Color(0xFF353535),
)
