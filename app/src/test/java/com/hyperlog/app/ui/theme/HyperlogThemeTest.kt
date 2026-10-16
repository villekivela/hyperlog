package com.hyperlog.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HyperlogThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun themed(): Pair<ColorScheme, Typography> {
        lateinit var scheme: ColorScheme
        lateinit var type: Typography
        compose.setContent {
            HyperlogTheme {
                scheme = MaterialTheme.colorScheme
                type = MaterialTheme.typography
            }
        }
        compose.waitForIdle()
        return scheme to type
    }

    @Test
    fun lightIsNavyOnWhite() {
        val scheme = themed().first
        assertEquals(Color(0xFF2B3A8F), scheme.primary)
        assertEquals(Color(0xFF2B3A8F), scheme.primaryContainer)
        assertEquals(Color.White, scheme.surface)
        assertEquals(Color(0xFFE3E6F7), scheme.secondaryContainer)
    }

    @Test
    @Config(qualifiers = "night")
    fun darkIsNavyOnNearBlackGrey() {
        val scheme = themed().first
        assertEquals(Color(0xFFB4BEFF), scheme.primary)
        assertEquals(Color(0xFF3A49A8), scheme.primaryContainer)
        assertEquals(Color(0xFF111214), scheme.surface)
        assertEquals(Color(0xFF262C4A), scheme.secondaryContainer)
    }

    @Test
    fun lightSurfacesTextAndDividersAreNeutralGrey() = assertNeutral(themed().first)

    @Test
    @Config(qualifiers = "night")
    fun darkSurfacesTextAndDividersAreNeutralGrey() = assertNeutral(themed().first)

    @Test
    fun lightTertiaryIsTheAccentNotASecondHue() {
        val scheme = themed().first
        assertEquals(scheme.primary, scheme.tertiary)
    }

    @Test
    @Config(qualifiers = "night")
    fun darkTertiaryIsTheAccentNotASecondHue() {
        val scheme = themed().first
        assertEquals(scheme.primary, scheme.tertiary)
    }

    @Test
    fun lightTextOnAccentReads() = assertTextContrast(themed().first)

    @Test
    @Config(qualifiers = "night")
    fun darkTextOnAccentReads() = assertTextContrast(themed().first)

    /** Surfaces, text, dividers and secondary carry at most a faint tint: channels within 8 of each other. */
    private fun assertNeutral(s: ColorScheme) {
        listOf(
            "background" to s.background, "surface" to s.surface, "surfaceDim" to s.surfaceDim,
            "surfaceBright" to s.surfaceBright, "surfaceVariant" to s.surfaceVariant,
            "surfaceContainerLowest" to s.surfaceContainerLowest, "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer, "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest, "onSurface" to s.onSurface,
            "onSurfaceVariant" to s.onSurfaceVariant, "outline" to s.outline, "outlineVariant" to s.outlineVariant,
            "inverseSurface" to s.inverseSurface, "secondary" to s.secondary,
            // Advice cards (the coach's workout, time off) and technique pills: grey, not an accent block.
            "tertiaryContainer" to s.tertiaryContainer, "onTertiaryContainer" to s.onTertiaryContainer,
        ).forEach { (name, c) ->
            val channels = listOf(c.red, c.green, c.blue).map { (it * 255).toInt() }
            assertTrue("$name $c is tinted", channels.max() - channels.min() <= 8)
        }
    }

    private fun assertTextContrast(s: ColorScheme) {
        listOf(
            "onPrimary" to (s.onPrimary to s.primary),
            "onPrimaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
            "onSecondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
            "onTertiary" to (s.onTertiary to s.tertiary),
            "onTertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
            "tertiary on tertiaryContainer" to (s.tertiary to s.tertiaryContainer),
            "onSurface" to (s.onSurface to s.surface),
            "onSurfaceVariant" to (s.onSurfaceVariant to s.surfaceContainerHighest),
        ).forEach { (name, pair) ->
            val (hi, lo) = listOf(pair.first.luminance(), pair.second.luminance()).sortedDescending()
            val ratio = (hi + 0.05f) / (lo + 0.05f)
            assertTrue("$name is $ratio:1", ratio >= 4.5f)
        }
    }

    @Test
    fun androidTwelveAndUpGetsNoDynamicColourInLight() {
        val wallpaper = dynamicLightColorScheme(ApplicationProvider.getApplicationContext())
        assertNotEquals(wallpaper.primary, themed().first.primary)
    }

    @Test
    @Config(qualifiers = "night")
    fun androidTwelveAndUpGetsNoDynamicColourInDark() {
        val wallpaper = dynamicDarkColorScheme(ApplicationProvider.getApplicationContext())
        assertNotEquals(wallpaper.primary, themed().first.primary)
    }

    @Test
    @Config(sdk = [30])
    fun phonesBeforeAndroidTwelveGetTheSameNavyOnGrey() {
        assertEquals(NavyLight.primary, themed().first.primary)
    }

    @Test
    fun everyStyleHasTabularFigures() {
        val type = themed().second
        styles(type).forEach { (name, style) -> assertEquals(name, "tnum", style.fontFeatureSettings) }
    }

    @Test
    fun displayHeadlineAndTitleLargeAreSpaceGroteskAndTheRestTheSystemFont() {
        val type = themed().second
        styles(type).forEach { (name, style) ->
            val display = name.startsWith("display") || name.startsWith("headline") || name.startsWith("titleLarge")
            assertEquals(name, if (display) SpaceGrotesk else FontFamily.SansSerif, style.fontFamily)
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun styles(t: Typography): List<Pair<String, TextStyle>> = listOf(
        "displayLarge" to t.displayLarge, "displayMedium" to t.displayMedium, "displaySmall" to t.displaySmall,
        "headlineLarge" to t.headlineLarge, "headlineMedium" to t.headlineMedium, "headlineSmall" to t.headlineSmall,
        "titleLarge" to t.titleLarge, "titleMedium" to t.titleMedium, "titleSmall" to t.titleSmall,
        "bodyLarge" to t.bodyLarge, "bodyMedium" to t.bodyMedium, "bodySmall" to t.bodySmall,
        "labelLarge" to t.labelLarge, "labelMedium" to t.labelMedium, "labelSmall" to t.labelSmall,
        "displayLargeEmphasized" to t.displayLargeEmphasized, "displayMediumEmphasized" to t.displayMediumEmphasized,
        "displaySmallEmphasized" to t.displaySmallEmphasized, "headlineLargeEmphasized" to t.headlineLargeEmphasized,
        "headlineMediumEmphasized" to t.headlineMediumEmphasized, "headlineSmallEmphasized" to t.headlineSmallEmphasized,
        "titleLargeEmphasized" to t.titleLargeEmphasized, "titleMediumEmphasized" to t.titleMediumEmphasized,
        "titleSmallEmphasized" to t.titleSmallEmphasized, "bodyLargeEmphasized" to t.bodyLargeEmphasized,
        "bodyMediumEmphasized" to t.bodyMediumEmphasized, "bodySmallEmphasized" to t.bodySmallEmphasized,
        "labelLargeEmphasized" to t.labelLargeEmphasized, "labelMediumEmphasized" to t.labelMediumEmphasized,
        "labelSmallEmphasized" to t.labelSmallEmphasized,
    )
}
