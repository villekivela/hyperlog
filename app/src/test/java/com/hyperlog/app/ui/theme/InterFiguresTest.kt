package com.hyperlog.app.ui.theme

import android.content.Context
import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Inter with tabular figures, as every style sets it: the digits share one width, and a hyphen keeps
 * its own (scripts/inter-proportional-hyphen.py), so "Ramp-in" never reads "Ramp - in".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InterFiguresTest {
    private val fonts = listOf(R.font.inter_regular, R.font.inter_medium, R.font.inter_semibold, R.font.inter_bold)

    private fun paint(font: Int, features: String) = Paint().apply {
        typeface = ResourcesCompat.getFont(ApplicationProvider.getApplicationContext<Context>(), font)
        textSize = 100f
        fontFeatureSettings = features
    }

    @Test
    fun digitsShareOneWidth() = fonts.forEach { font ->
        val p = paint(font, TabularFigures)
        val widths = "0123456789".map { p.measureText(it.toString()) }.toSet()
        assertEquals("digits of font $font", 1, widths.size)
    }

    @Test
    fun aHyphenKeepsItsOwnWidth() = fonts.forEach { font ->
        assertEquals("hyphen of font $font", paint(font, "").measureText("-"), paint(font, TabularFigures).measureText("-"))
    }
}
