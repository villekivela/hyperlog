package com.hyperlog.app.ui.session

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.hyperlog.app.ui.theme.InkDark
import com.hyperlog.app.ui.theme.InkLight
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The colours that don't follow the theme (RIR, progression, plates, errors) must never pass for
 * the accent: each hue keeps 30° from every accent fill. Near-greys (a 5 kg plate, the flat trend)
 * carry no hue to confuse.
 */
class FixedColoursTest {
    private val fixed: List<Pair<String, Color>> = buildList {
        for (rir in 0..4) {
            add("RIR $rir" to rirColor(rir))
            add("RIR $rir chip" to rirChipColor(rir, dark = false))
            add("RIR $rir badge" to rirBadgeColor(rir))
        }
        Direction.entries.forEach { add("trend $it" to it.color) }
        listOf(25.0, 20.0, 15.0, 10.0, 5.0, 1.25).forEach { add("$it kg plate" to plateColor(it)) }
        listOf(InkLight, InkDark).forEach { add("error" to it.error); add("errorContainer" to it.errorContainer) }
    }

    private fun accents(s: ColorScheme) = listOf(s.primary, s.primaryContainer, s.secondaryContainer)

    @Test
    fun noFixedColourSharesTheAccentsHue() {
        for (scheme in listOf(InkLight, InkDark)) {
            for (accent in accents(scheme)) {
                fixed.filter { chroma(it.second) >= 0.15f }.forEach { (name, c) ->
                    val apart = abs(hue(c) - hue(accent)).let { minOf(it, 360 - it) }
                    assertTrue("$name $c is $apart° from accent $accent", apart >= 30f)
                }
            }
        }
    }

    private fun chroma(c: Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)

    private fun hue(c: Color): Float {
        val max = maxOf(c.red, c.green, c.blue)
        val d = chroma(c)
        val h = when (max) {
            c.red -> ((c.green - c.blue) / d).mod(6f)
            c.green -> (c.blue - c.red) / d + 2
            else -> (c.red - c.green) / d + 4
        }
        return h * 60
    }
}
