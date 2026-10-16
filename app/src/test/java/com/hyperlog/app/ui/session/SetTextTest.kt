package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A set on screen ends in its RIR chip, after its drops, mini-sets or hold, and never wraps inside. */
@RunWith(RobolectricTestRunner::class)
class SetTextTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theChipComesAfterTheWholeSetWhichNeverBreaks() {
        val myo = LoggedSet(45.0, 10, 0, 2, extensions = List(3) { Extension(Extension.Kind.MiniSet, 45.0, 3) })
        val drop = LoggedSet(25.0, 9, 0, 1, extensions = listOf(Extension(Extension.Kind.Drop, 18.0, 7)))
        val partials = LoggedSet(50.0, 8, 3, 0)
        compose.setContent { HyperlogTheme { SummaryRows(listOf(ExerciseSummary("Leg Extension", listOf(myo, drop, partials))), onOpen = null) } }
        // Spaces inside a set are no-break; only the gaps between sets can wrap. The chip reads "N RIR".
        val sets = listOf("45 × 10 + 3 + 3 + 3" to 2, "25 × 9, 18 × 7" to 1, "50 × 8 + 3p" to 0)
        compose.onNodeWithText(sets.joinToString("   ") { (set, rir) -> set.replace(' ', ' ') + " $rir RIR" }).assertIsDisplayed()
    }
}
