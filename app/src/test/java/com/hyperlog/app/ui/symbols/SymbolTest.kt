package com.hyperlog.app.ui.symbols

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

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

    @Test
    fun theAppBundlesNoSymbolFont() {
        // The Material Symbols variable font is 15 MB; icons ship as vector drawables instead.
        // res/font holds the display face's static fonts, each well under 1 MB.
        File("src/main/res/font").listFiles().orEmpty().forEach { font ->
            assertFalse("${font.name} is Material Symbols", font.name.contains("symbol", ignoreCase = true))
            assertTrue("${font.name} is ${font.length()} bytes", font.length() < 1_000_000)
        }
    }
}
