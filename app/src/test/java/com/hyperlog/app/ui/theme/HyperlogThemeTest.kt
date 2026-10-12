package com.hyperlog.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HyperlogThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun themedScheme(): ColorScheme {
        lateinit var scheme: ColorScheme
        compose.setContent { HyperlogTheme { scheme = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        return scheme
    }

    // The logo is white on #1A1A1A (brand/hyperlog-background.svg).
    private val logoInk = Color(0xFF1A1A1A)

    @Test
    @Config(sdk = [30])
    fun withoutDynamicColourLightUsesTheLogoPalette() {
        val scheme = themedScheme()
        assertEquals(logoInk, scheme.primary)
        assertEquals(Color.White, scheme.onPrimary)
    }

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun withoutDynamicColourDarkUsesTheLogoPalette() {
        val scheme = themedScheme()
        assertEquals(Color.White, scheme.primary)
        assertEquals(logoInk, scheme.onPrimary)
    }

    @Test
    fun withDynamicColourLightFollowsTheWallpaper() {
        val expected = dynamicLightColorScheme(ApplicationProvider.getApplicationContext())
        assertEquals(expected.primary, themedScheme().primary)
    }

    @Test
    @Config(qualifiers = "night")
    fun withDynamicColourDarkFollowsTheWallpaper() {
        val expected = dynamicDarkColorScheme(ApplicationProvider.getApplicationContext())
        assertEquals(expected.primary, themedScheme().primary)
    }
}
