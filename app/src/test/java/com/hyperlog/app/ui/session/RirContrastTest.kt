package com.hyperlog.app.ui.session

import androidx.compose.ui.graphics.Color
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
    fun lightWarmAndCalmRingsKeepThreeToOneOnEveryLightSurface() {
        for (rir in listOf(1, 2, 3, 4)) {
            lightSurfaces.forEach { (name, surface) ->
                val ratio = contrast(rirRingColor(rir, dark = false), surface)
                assertTrue("RIR $rir ring on $name is $ratio:1", ratio >= 3f)
            }
        }
    }

    @Test
    fun darkRingsAndFilledDotsKeepTheScale() {
        for (rir in 0..4) assertEquals(rirColor(rir), rirRingColor(rir, dark = true))
        assertEquals(rirColor(0), rirRingColor(0, dark = false))
    }
}
