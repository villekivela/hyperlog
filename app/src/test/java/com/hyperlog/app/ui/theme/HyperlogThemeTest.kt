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
    fun lightIsInkAndViolet() {
        val scheme = themed().first
        assertEquals(Color(0xFF5B3DD0), scheme.primary)
        assertEquals(Color(0xFF5B3DD0), scheme.primaryContainer)
        assertEquals(Color(0xFFFBF9FE), scheme.surface)
        assertEquals(Color(0xFFF0ECF7), scheme.surfaceContainer)
        assertEquals(Color.White, scheme.surfaceBright)
        assertEquals(Color(0xFFE6DEFF), scheme.secondaryContainer)
    }

    @Test
    @Config(qualifiers = "night")
    fun darkIsInkAndViolet() {
        val scheme = themed().first
        assertEquals(Color(0xFFC4B1FF), scheme.primary)
        assertEquals(Color(0xFF6A4CE0), scheme.primaryContainer)
        assertEquals(Color(0xFF14111B), scheme.surface)
        assertEquals(Color(0xFF18141F), scheme.surfaceContainer)
        assertEquals(Color(0xFF241E2F), scheme.surfaceBright)
        assertEquals(Color(0xFF3A2C66), scheme.secondaryContainer)
    }

    @Test
    fun lightSurfacesTextAndDividersCarryAFaintVioletTint() = assertFaintViolet(themed().first)

    @Test
    @Config(qualifiers = "night")
    fun darkSurfacesTextAndDividersCarryAFaintVioletTint() = assertFaintViolet(themed().first)

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
    fun lightTechniqueGreyIsItsOwnStep() = assertTechniqueGreyStandsOff(themed().first)

    @Test
    @Config(qualifiers = "night")
    fun darkTechniqueGreyIsItsOwnStep() = assertTechniqueGreyStandsOff(themed().first)

    @Test
    fun lightTextOnAccentReads() = assertTextContrast(themed().first)

    @Test
    @Config(qualifiers = "night")
    fun darkTextOnAccentReads() = assertTextContrast(themed().first)

    /**
     * Surfaces, text, dividers and secondary lean violet (blue the strongest channel, red over
     * green) but only faintly: no channel more than 40 from another, so nothing reads as an accent.
     * White (light mode's cards) is the one untinted surface.
     */
    private fun assertFaintViolet(s: ColorScheme) {
        listOf(
            "background" to s.background, "surface" to s.surface, "surfaceDim" to s.surfaceDim,
            "surfaceBright" to s.surfaceBright, "surfaceVariant" to s.surfaceVariant,
            "surfaceContainerLowest" to s.surfaceContainerLowest, "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer, "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest, "onSurface" to s.onSurface,
            "onSurfaceVariant" to s.onSurfaceVariant, "outline" to s.outline, "outlineVariant" to s.outlineVariant,
            "inverseSurface" to s.inverseSurface, "secondary" to s.secondary,
            // Advice (time off) and technique pills: a tinted grey, not an accent block.
            "tertiaryContainer" to s.tertiaryContainer, "onTertiaryContainer" to s.onTertiaryContainer,
        ).filter { it.second != Color.White }.forEach { (name, c) ->
            val (r, g, b) = listOf(c.red, c.green, c.blue).map { (it * 255).toInt() }
            assertTrue("$name $c isn't violet", b >= r && r >= g && b > g)
            assertTrue("$name $c is more than a tint", b - g <= 40)
        }
    }

    /** A technique set's pill and circle are grey, but not the plain set's grey or any card's. */
    private fun assertTechniqueGreyStandsOff(s: ColorScheme) {
        listOf(
            s.surface, s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer,
            s.surfaceContainerHigh, s.surfaceContainerHighest,
        ).forEach { assertNotEquals(it, s.tertiaryContainer) }
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
    fun phonesBeforeAndroidTwelveGetTheSameInkAndViolet() {
        assertEquals(InkLight.primary, themed().first.primary)
    }

    @Test
    fun everyStyleHasTabularFigures() {
        val type = themed().second
        styles(type).forEach { (name, style) -> assertEquals(name, "tnum", style.fontFeatureSettings) }
    }

    @Test
    fun displayHeadlineAndTitleLargeAreSpaceGroteskAndTheRestInter() {
        val type = themed().second
        styles(type).forEach { (name, style) ->
            val display = name.startsWith("display") || name.startsWith("headline") || name.startsWith("titleLarge")
            assertEquals(name, if (display) SpaceGrotesk else Inter, style.fontFamily)
        }
    }

    @Test
    fun theSetTablesFiguresAreInterWithTabularFiguresAtTitleSizes() {
        val type = themed().second
        listOf(type.figureLarge to type.titleLarge, type.figureMedium to type.titleMedium).forEach { (figure, title) ->
            assertEquals(Inter, figure.fontFamily)
            assertEquals("tnum", figure.fontFeatureSettings)
            assertEquals(title.fontSize, figure.fontSize)
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
