package com.hyperlog.app.ui.session

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
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

    // Navy on grey (#42): its light and dark surfaces and containers, and its accents.
    private val neutralLight = listOf(0xFFFFFFFF, 0xFFF7F7F8, 0xFFEEEEF0, 0xFFE9E9EC, 0xFFE4E4E7).map(::Color)
    private val neutralDark = listOf(0xFF111214, 0xFF17181A, 0xFF1E1F22, 0xFF26272A, 0xFF303134).map(::Color)

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
                    val ratio = contrast(rirChipFill(rir, dark), surface)
                    assertTrue("RIR $rir fill (dark=$dark) on $surface is $ratio:1", ratio >= 3f)
                }
            }
        }
    }

    @Test
    fun aFilledChipsNumberReadsAtFourAndAHalfToOne() {
        for (rir in 0..10) {
            for (dark in listOf(false, true)) {
                val fill = rirChipFill(rir, dark)
                val ratio = contrast(RirChipOnFill.compositeOver(fill), fill)
                assertTrue("RIR $rir number (dark=$dark) is $ratio:1", ratio >= 4.5f)
            }
        }
    }
}
