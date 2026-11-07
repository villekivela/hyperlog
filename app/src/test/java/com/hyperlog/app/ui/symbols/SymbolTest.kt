package com.hyperlog.app.ui.symbols

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /**
     * Material Symbols Rounded draws on a 960 grid, so a symbol on another grid likely came from
     * another set, at another weight (#271). Two of the Rounded set itself ship on a 24 grid.
     */
    @Test
    fun everySymbolIsOnTheSetsGrid() {
        val others = File("src/main/res/drawable").listFiles().orEmpty()
            .filter { it.name.startsWith("symbol_") && "viewportWidth=\"960\"" !in it.readText() }.map { it.name }
        assertEquals(setOf("symbol_insights.xml", "symbol_push_pin.xml"), others.toSet())
    }

    /** One icon, one meaning: the dumbbell is the program, and nothing else (round 3). */
    @Test
    fun theDumbbellMeansTheProgram() {
        val program = setOf("MainActivity.kt", "StarterOffer.kt", "ProgramScreen.kt", "TodayScreen.kt", "SessionScreen.kt", "Symbol.kt")
        val others = File("src/main/java/com/hyperlog/app").walkTopDown()
            .filter { it.extension == "kt" && "Symbol.FitnessCenter" in it.readText() }.map { it.name }.toSet() - program
        assertEquals(emptySet<String>(), others)
        val today = File("src/main/java/com/hyperlog/app/ui/today/TodayScreen.kt").readLines().filter { "Symbol.FitnessCenter" in it }
        assertEquals("Only Today's empty state, with no program yet", 1, today.size)
    }

    /** The clock with an arrow is the History tab (round 4). */
    @Test
    fun theHistorySymbolMeansTheTab() {
        val others = File("src/main/java/com/hyperlog/app").walkTopDown()
            .filter { it.extension == "kt" && "Symbol.History" in it.readText() }.map { it.name }.toSet() - setOf("MainActivity.kt")
        assertEquals(emptySet<String>(), others)
    }

    @Test
    fun theAppBundlesNoSymbolFont() {
        // The Material Symbols variable font is 15 MB; icons ship as vector drawables instead.
        // res/font holds the display and text faces' static fonts, each well under 1 MB.
        File("src/main/res/font").listFiles().orEmpty().forEach { font ->
            assertFalse("${font.name} is Material Symbols", font.name.contains("symbol", ignoreCase = true))
            assertTrue("${font.name} is ${font.length()} bytes", font.length() < 1_000_000)
        }
    }
}
