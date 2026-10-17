package com.hyperlog.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.hyperlog.app.ui.today.doneFill
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What marks a state has to stand off what it sits on, in both schemes (WCAG 1.4.11: 3:1 for a
 * component, 4.5:1 for its words), in the style of RirContrastTest. #58's light-mode findings.
 */
class AccentContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private val schemes = mapOf("light" to InkLight, "dark" to InkDark)

    /** The screen and the cards a done day's marker or cookie sits on. */
    private fun neutrals(s: ColorScheme) = listOf(s.surfaceContainer, s.surfaceContainerLowest, s.surfaceBright)

    @Test
    fun aDoneDaysFillStandsOffTheScreenAndItsCards() {
        schemes.forEach { (name, s) ->
            val (fill, letter) = doneFill(s)
            neutrals(s).forEach { bg ->
                val ratio = contrast(fill, bg)
                assertTrue("$name done fill on $bg is $ratio:1", ratio >= 3f)
            }
            assertTrue("$name done letter is ${contrast(letter, fill)}:1", contrast(letter, fill) >= 4.5f)
        }
    }

    @Test
    fun aChoiceOnTheAccentCardShowsWhichIsPicked() {
        schemes.forEach { (name, s) ->
            listOf("card" to (if (s == InkDark) s.surfaceBright else s.surfaceContainerLowest), "accent card" to s.primaryContainer).forEach { (where, card) ->
                val c = choiceColors(s, card, onAccent = where == "accent card")
                val unchecked = c.unchecked.compositeOver(card)
                // On the accent card the picked one must stand off the card itself. On a neutral card
                // the group's buttons are its edge: the picked one stands off the others.
                if (where == "accent card") assertTrue("$name checked on $where is ${contrast(c.checked, card)}:1", contrast(c.checked, card) >= 3f)
                assertTrue("$name checked vs unchecked on $where is ${contrast(c.checked, unchecked)}:1", contrast(c.checked, unchecked) >= 3f)
                assertTrue("$name checked words on $where", contrast(c.onChecked, c.checked) >= 4.5f)
                assertTrue("$name unchecked words on $where", contrast(c.onUnchecked, unchecked) >= 4.5f)
            }
        }
    }

    @Test
    fun aFilledButtonOnTheAccentCardStandsOffIt() {
        // The gym's Add, Coach's Review reply, the session's Finish session: as the hero's Start.
        schemes.forEach { (name, s) ->
            val (fill, words) = accentButton(s)
            assertTrue("$name button on the accent card is ${contrast(fill, s.primaryContainer)}:1", contrast(fill, s.primaryContainer) >= 3f)
            assertTrue("$name button words", contrast(words, fill) >= 4.5f)
        }
    }

    @Test
    fun aPickedChoiceIsFilledPrimaryContainerInBothModes() {
        // The owner's call (#58): every connected group picks in the deep violet of the selected
        // day, the selected tab and Start; in light primary is the same violet anyway.
        schemes.forEach { (name, s) ->
            val card = if (s == InkDark) s.surfaceBright else s.surfaceContainerLowest
            assertTrue("$name picked fill is ${choiceColors(s, card, onAccent = false).checked}", choiceColors(s, card, onAccent = false).checked == s.primaryContainer)
            assertTrue("$name picked words", choiceColors(s, card, onAccent = false).onChecked == s.onPrimaryContainer)
        }
    }
}
