package com.hyperlog.app.ui.session

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
    fun loggedChipsKeepThreeToOneOnEveryNeutralSurface() {
        for (rir in 0..10) {
            for (dark in listOf(false, true)) {
                (if (dark) neutralDark else neutralLight).forEach { surface ->
                    val ratio = contrast(rirChipColor(rir, dark), surface)
                    assertTrue("RIR $rir fill (dark=$dark) on $surface is $ratio:1", ratio >= 3f)
                }
            }
        }
    }

    @Test
    fun aFilledChipsNumberReadsAtFourAndAHalfToOne() {
        for (rir in 0..10) {
            for (dark in listOf(false, true)) {
                val fill = rirChipColor(rir, dark)
                val ratio = contrast(RirOnFill.compositeOver(fill), fill)
                assertTrue("RIR $rir number (dark=$dark) is $ratio:1", ratio >= 4.5f)
            }
        }
    }
}
