package com.hyperlog.app.ui.session

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ColorScheme
import com.hyperlog.app.ui.theme.NavyDark
import com.hyperlog.app.ui.theme.NavyLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RirContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private val lightSurfaces = with(NavyLight) {
        mapOf(
            "surface" to surface, "surfaceContainerLowest" to surfaceContainerLowest, "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainer" to surfaceContainer, "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
        )
    }

    @Test
    fun lightRingsKeepThreeToOneOnEveryLightSurface() {
        for (rir in 0..4) {
            lightSurfaces.forEach { (name, surface) ->
                val ratio = contrast(rirRingColor(rir, dark = false), surface)
                assertTrue("RIR $rir ring on $name is $ratio:1", ratio >= 3f)
            }
        }
    }

    @Test
    fun darkRingsKeepTheBaseScale() {
        for (rir in 0..4) assertEquals(rirColor(rir), rirRingColor(rir, dark = true))
    }

    /** Every surface and container a chip can sit on, from the theme's own schemes. */
    private fun surfaces(scheme: ColorScheme) = with(scheme) {
        listOf(surface, surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest)
    }
    private val neutralLight = surfaces(NavyLight)
    private val neutralDark = surfaces(NavyDark)

    @Test
    fun targetChipsKeepThreeToOneOnEveryNeutralSurface() {
        for (rir in 0..10) {
            for (dark in listOf(false, true)) {
                (if (dark) neutralDark else neutralLight).forEach { surface ->
                    val ratio = contrast(rirChipColor(rir, dark), surface)
                    assertTrue("RIR $rir chip (dark=$dark) on $surface is $ratio:1", ratio >= 3f)
                }
            }
        }
    }

    @Test
    fun aLoggedChipsNumberIsWhite() {
        // The owner's call (#43): a solid badge, a white number on the darker shade, in both modes.
        assertEquals(Color.White, RirBadgeNumber)
    }

    @Test
    fun aBadgesWhiteNumberReadsAtFourAndAHalfToOne() {
        for (rir in 0..10) {
            val ratio = contrast(Color.White, rirBadgeColor(rir))
            assertTrue("RIR $rir number is $ratio:1", ratio >= 4.5f)
        }
    }

    @Test
    fun aBadgeKeepsThreeToOneOnEveryLightSurface() {
        for (rir in 0..10) {
            neutralLight.forEach { surface ->
                val ratio = contrast(rirBadgeColor(rir), surface)
                assertTrue("RIR $rir badge on $surface is $ratio:1", ratio >= 3f)
            }
        }
    }

    @Test
    fun aBadgeKeepsItsHue() {
        // Darkened only as far as white needs: still recognisably hot, warm and calm.
        for (rir in listOf(0, 1, 3)) {
            val ring = rirRingColor(rir, dark = false)
            val badge = rirBadgeColor(rir)
            assertEquals("RIR $rir hue", hue(ring), hue(badge), 4f)
            assertTrue("RIR $rir is darker", badge.luminance() < ring.luminance())
        }
    }

    private fun hue(c: Color): Float {
        val max = maxOf(c.red, c.green, c.blue)
        val d = max - minOf(c.red, c.green, c.blue)
        val h = when (max) {
            c.red -> ((c.green - c.blue) / d).mod(6f)
            c.green -> (c.blue - c.red) / d + 2
            else -> (c.red - c.green) / d + 4
        }
        return h * 60
    }
}
