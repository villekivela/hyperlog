package com.hyperlog.app.ui.today

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.asAndroidBitmap
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.week.DayFit
import com.hyperlog.app.week.TimeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek

/** The time line is an amber chip with dark ink (owner, 2026-10-27): the same on the hero, a card and in both themes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FitChipTest {
    @get:Rule
    val compose = createComposeRule()

    /** The amber the spec names, the progression's weight-down mark: written out, so the chip can't drift with the constant. */
    private val Amber = Color(0xFFE0A84E)

    private val fit = DayFit(TimeOver(58, 50, DayOfWeek.SATURDAY), null)

    /** WCAG contrast of two opaque colours, worked from the definition. */
    private fun contrast(a: Color, b: Color): Double {
        fun lum(c: Color): Double {
            fun ch(v: Float) = v.toDouble().let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
        }
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun chipPixel(night: Boolean): Int {
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        compose.setContent { HyperlogTheme { FitLines(fit, "Day C", Color.Gray, onTime = {}) } }
        compose.onNodeWithTag(FitTimeTag).assertHasClickAction()
        val chip = compose.onNodeWithTag(FitChipTag, useUnmergedTree = true).getBoundsInRoot()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        // Just inside the chip's left edge, mid-height, clear of its text and its rounded corners.
        val density = compose.density.density
        return bitmap.getPixel(((chip.left.value + 4) * density).toInt(), (((chip.top.value + chip.bottom.value) / 2) * density).toInt())
    }

    @Test
    fun theChipIsAmberInLightAndDark() {
        assertEquals(Amber.toArgb(), chipPixel(night = false))
    }

    @Test
    fun theChipIsAmberInDarkToo() {
        assertEquals(Amber.toArgb(), chipPixel(night = true))
    }

    @Test
    fun itsInkReadsOnTheAmberAtFourAndAHalfToOne() {
        assertTrue(contrast(FitChipInk, Amber) >= 4.5)
    }
}
