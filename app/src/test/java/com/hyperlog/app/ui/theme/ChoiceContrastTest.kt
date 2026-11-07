package com.hyperlog.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** An option not picked is still an option: its words keep 4.5:1, so it never reads as disabled (#273). */
class ChoiceContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private fun words(scheme: ColorScheme, on: Color) = choiceColors(scheme, on, onAccent = false).let { contrast(it.onUnchecked, it.unchecked.compositeOver(on)) }

    @Test
    fun anOptionNotPickedKeepsItsWordsReadable() {
        listOf("dark" to GraphiteDark, "light" to GraphiteLight).forEach { (name, scheme) ->
            listOf("screen" to scheme.surfaceContainer, "card" to scheme.surfaceBright, "light card" to scheme.surfaceContainerLowest).forEach { (where, on) ->
                val ratio = words(scheme, on)
                println("PROBE $name on $where $ratio")
                assertTrue("$name on $where is $ratio:1", ratio >= 4.5f)
            }
        }
    }
}
