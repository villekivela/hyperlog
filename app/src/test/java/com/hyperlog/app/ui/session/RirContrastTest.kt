package com.hyperlog.app.ui.session

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.ColorScheme
import com.hyperlog.app.ui.theme.InkDark
import com.hyperlog.app.ui.theme.InkLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RirContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun darkOutlinesKeepTheBaseScale() {
        for (rir in 0..4) assertEquals(rirColor(rir), rirChipColor(rir, dark = true))
    }

    /** Every surface and container a chip can sit on, from the theme's own schemes. */
    private fun surfaces(scheme: ColorScheme) = with(scheme) {
        listOf(surface, surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest)
    }
    private val neutralLight = surfaces(InkLight)
    private val neutralDark = surfaces(InkDark)

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
    fun aChipsNumberReadsAtFourAndAHalfToOneOnEveryNeutralSurface() {
        for (rir in 0..10) {
            for (dark in listOf(false, true)) {
                (if (dark) neutralDark else neutralLight).forEach { surface ->
                    val ratio = contrast(rirNumberColor(rir, dark), surface)
                    assertTrue("RIR $rir number (dark=$dark) on $surface is $ratio:1", ratio >= 4.5f)
                }
            }
        }
    }

    @Test
    fun aBadgesNumberReadsAtFourAndAHalfToOneOnItsFill() {
        for (rir in 0..10) {
            for (dark in listOf(false, true)) {
                val ratio = contrast(rirNumberColor(rir, dark), rirBadgeColor(rir, dark))
                assertTrue("RIR $rir number (dark=$dark) on its badge is $ratio:1", ratio >= 4.5f)
            }
        }
    }

    @Test
    fun aNumberKeepsItsHue() {
        // Darker in light and lighter in dark, but still recognisably hot, warm and calm.
        for (rir in listOf(0, 1, 3)) {
            for (dark in listOf(false, true)) {
                assertEquals("RIR $rir hue (dark=$dark)", hue(rirColor(rir)), hue(rirNumberColor(rir, dark)), 4f)
            }
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
