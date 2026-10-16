package com.hyperlog.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun lightIsNavy() {
        val scheme = themed().first
        assertEquals(Color(0xFF303E94), scheme.primary)
        assertEquals(Color(0xFFEFEDF4), scheme.surfaceContainer)
        assertEquals(Color.White, scheme.surfaceContainerLowest)
    }

    @Test
    @Config(qualifiers = "night")
    fun darkIsNavyWithBlueTintedSurfaces() {
        val scheme = themed().first
        assertEquals(Color(0xFFBBC3FF), scheme.primary)
        assertEquals(Color(0xFF303E94), scheme.primaryContainer)
        assertEquals(Color(0xFF0D1021), scheme.surface)
        assertEquals(Color(0xFF181A2C), scheme.surfaceContainer)
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
    fun phonesBeforeAndroidTwelveGetTheSameNavy() {
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
