package com.hyperlog.app.ui.symbols

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SymbolTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everySymbolShipsAsAVectorDrawable() {
        Symbol.entries.forEach { symbol ->
            val drawable = context.getDrawable(symbol.drawable)
            assertTrue("$symbol is ${drawable?.javaClass}", drawable is VectorDrawable)
        }
    }

    @org.junit.Ignore("theme-proposals: bundles display fonts")
    @Test
    fun theAppBundlesNoFonts() {
        // The Material Symbols variable font is 15 MB; icons ship as vector drawables instead.
        try {
            Class.forName("com.hyperlog.app.R\$font")
            fail("res/font exists")
        } catch (_: ClassNotFoundException) {
        }
    }
}
