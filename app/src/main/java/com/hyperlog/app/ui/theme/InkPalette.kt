package com.hyperlog.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Hyperlog's own colours, Ink and violet: surfaces, text and dividers carry a faint violet tint,
// and one violet accent marks what you act on or what's selected, the same on every phone. The
// accent fills primary and primaryContainer (the Start button) and tints secondaryContainer (the
// selected tab, chips that are on); tertiary follows primary. Secondary and tertiaryContainer
// (advice, technique pills) stay tinted greys, so nothing else colours the screen;
// tertiaryContainer is its own step, apart from the plain sets' tone and the cards'. Light cards are
// white, dark is near-black ink. Errors keep Material's defaults. surfaceTint is transparent so
// elevation never tints a surface.

internal val InkLight = lightColorScheme(
    primary = Color(0xFF5B3DD0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5B3DD0),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFC4B1FF),
    surfaceTint = Color.Transparent,
    secondary = Color(0xFF5E5570),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DEFF),
    onSecondaryContainer = Color(0xFF23145C),
    tertiary = Color(0xFF5B3DD0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDAD3E6),
    onTertiaryContainer = Color(0xFF1B1623),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF9FE),
    onBackground = Color(0xFF1B1623),
    surface = Color(0xFFFBF9FE),
    onSurface = Color(0xFF1B1623),
    surfaceVariant = Color(0xFFE4DEEE),
    onSurfaceVariant = Color(0xFF5E5570),
    inverseSurface = Color(0xFF302A3A),
    inverseOnSurface = Color(0xFFF3EFF8),
    outline = Color(0xFF8A8199),
    outlineVariant = Color(0xFFD6CFE2),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFE4DEEE),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F4FB),
    surfaceContainer = Color(0xFFF0ECF7),
    surfaceContainerHigh = Color(0xFFEAE6F2),
    surfaceContainerHighest = Color(0xFFE4DEEE),
)

internal val InkDark = darkColorScheme(
    primary = Color(0xFFC4B1FF),
    onPrimary = Color(0xFF2A1670),
    primaryContainer = Color(0xFF6A4CE0),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF5B3DD0),
    surfaceTint = Color.Transparent,
    secondary = Color(0xFFA9A0BA),
    onSecondary = Color(0xFF14111B),
    secondaryContainer = Color(0xFF3A2C66),
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = Color(0xFFC4B1FF),
    onTertiary = Color(0xFF2A1670),
    tertiaryContainer = Color(0xFF3A3248),
    onTertiaryContainer = Color(0xFFECE6F5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF14111B),
    onBackground = Color(0xFFECE6F5),
    surface = Color(0xFF14111B),
    onSurface = Color(0xFFECE6F5),
    surfaceVariant = Color(0xFF2E2640),
    onSurfaceVariant = Color(0xFFA9A0BA),
    inverseSurface = Color(0xFFECE6F5),
    inverseOnSurface = Color(0xFF302A3A),
    outline = Color(0xFF6B6280),
    outlineVariant = Color(0xFF352D45),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF14111B),
    surfaceBright = Color(0xFF241E2F),
    surfaceContainerLowest = Color(0xFF0F0D15),
    surfaceContainerLow = Color(0xFF1C1724),
    surfaceContainer = Color(0xFF18141F),
    surfaceContainerHigh = Color(0xFF211B2B),
    surfaceContainerHighest = Color(0xFF2E2640),
)
