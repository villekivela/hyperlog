package com.hyperlog.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.hyperlog.app.program.TechniqueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each technique has its own colour, red, teal, violet, orange and blue (SPEC, Palette → Technique colours). */
class TechniqueColourTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
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

    /** Each family's hue range in degrees; red wraps, so it runs from -15 to 8. */
    private val hues = mapOf(
        TechniqueKind.Dropset to -15f..8f,
        TechniqueKind.Failure to 20f..40f,
        TechniqueKind.LengthenedPartials to 165f..190f,
        TechniqueKind.IsoHold to 205f..230f,
        TechniqueKind.MyoReps to 257f..285f,
    )

    private fun surfaces(s: ColorScheme) = with(s) {
        listOf(surface, surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest)
    }

    @Test
    fun eachTechniqueSitsInItsOwnHueFamilyInLightAndDark() {
        for (dark in listOf(false, true)) for (kind in TechniqueKind.entries) {
            val h = hue(techniqueColor(kind, dark)).let { if (kind == TechniqueKind.Dropset && it > 180f) it - 360f else it }
            assertTrue("$kind (dark=$dark) hue $h", h in hues.getValue(kind))
        }
    }

    @Test
    fun aTechniquesColourKeepsThreeToOneOnEveryNeutralSurface() {
        for (dark in listOf(false, true)) for (kind in TechniqueKind.entries) {
            surfaces(if (dark) GraphiteDark else GraphiteLight).forEach {
                val ratio = contrast(techniqueColor(kind, dark), it)
                assertTrue("$kind (dark=$dark) on $it is $ratio:1", ratio >= 3f)
            }
        }
    }

    @Test
    fun theIconKeepsThreeToOneOnItsColour() {
        for (dark in listOf(false, true)) for (kind in TechniqueKind.entries) {
            val ratio = contrast(onTechniqueColor(kind, dark), techniqueColor(kind, dark))
            assertTrue("$kind (dark=$dark) icon is $ratio:1", ratio >= 3f)
        }
    }

    @Test
    fun noTwoTechniquesShareAColour() {
        for (dark in listOf(false, true)) assertEquals(TechniqueKind.entries.size, TechniqueKind.entries.map { techniqueColor(it, dark) }.toSet().size)
    }
}
