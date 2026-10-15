package com.hyperlog.app.ui.session

import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import com.hyperlog.app.waitForDescription
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.press
import com.hyperlog.app.ui.program.ProgramStore
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitGone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Warm-ups show above the first work set, each with its plates drawn, and the pad draws the plates
 * per side for the weight being typed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class WarmupScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private fun startDay(exercise: String) {
        compose.waitFor("Today")
        runBlocking {
            ProgramStore.get(ApplicationProvider.getApplicationContext<Application>()).import(
                """
                { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 2, "days": [
                  { "id": "a", "name": "Day A", "groups": [ { "slots": [ { "id": "s", "exercise": "$exercise",
                    "sets": [ { "reps": "4-6", "rir": 1 }, { "count": 2, "reps": "6-8", "rir": 1, "backoff": {} } ] } ] } ] } ] } ] }
                """,
            )
        }
        compose.waitFor("Day A")
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Reps")
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test
    fun aNinetyKgBenchShowsItsRampAsFullRowsWithoutPlates() {
        startDay("barbell_bench_press")
        compose.press("9"); compose.press("0")
        // The ramp waits for the pad to move on, so it never flickers through a 9 kg ramp.
        assertEquals(0, compose.onAllNodes(hasContentDescription("Warm-up", substring = true)).fetchSemanticsNodes().size)
        compose.press("Next")
        compose.waitForDescription("Warm-up 1, 40 kg × 8")
        compose.onNodeWithContentDescription("Warm-up 4, 75 kg × 2").assertIsDisplayed()
        // Only the pad draws plates: the 90 being typed.
        compose.onNodeWithContentDescription("Plates per side: 1 × 20, 1 × 15, on a 20 kg bar").assertIsDisplayed()
        assertEquals(1, compose.onAllNodes(hasContentDescription("Plates per side", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun aWarmUpsCheckTicksItAndTheWorkSetStaysActive() {
        startDay("barbell_bench_press")
        compose.press("9"); compose.press("0"); compose.press("Next")
        compose.waitForDescription("Warm-up 1, 40 kg × 8")
        val first = compose.onNodeWithContentDescription("Warm-up 1, 40 kg × 8")
        first.assertIsOff()
        first.performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Warm-up 1, 40 kg × 8").assertIsOn()
        compose.onNodeWithContentDescription("Warm-up 2, 55 kg × 5").assertIsOff()
        // No rest starts for a warm-up.
        assertEquals(0, compose.onAllNodesWithContentDescription("Rest").fetchSemanticsNodes().size)

        // The work set still logs, and the warm-ups go.
        compose.press("5"); compose.press("Log set")
        compose.waitGone("Warm-up", substring = true)
        // The pad's plates come with the weight field: 80 kg for the first back-off.
        compose.onNode(clickLabel("kg"), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Plates per side: 1 × 20, 1 × 10, on a 20 kg bar").assertIsDisplayed()
    }

    @Test
    fun aTappedWarmUpIsTypedOnThePadAndArrowTicksItAndMovesOn() {
        startDay("barbell_bench_press")
        compose.press("9"); compose.press("0"); compose.press("Next")
        compose.waitForDescription("Warm-up 2, 55 kg × 5")
        compose.onNode(clickLabel("Type warm-up 2"), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        // The pad's plate strip shows the warm-up.
        compose.onNodeWithContentDescription("Plates per side: 1 × 15, 1 × 2.5, on a 20 kg bar").assertIsDisplayed()
        compose.press("5"); compose.press("0"); compose.press("Next")
        compose.waitFor("Warm-up 2 of 4")
        compose.press("6"); compose.press("Tick warm-up")
        compose.waitForDescription("Warm-up 2, 50 kg × 6")
        compose.onNodeWithContentDescription("Warm-up 2, 50 kg × 6").assertIsOn()
        // The next warm-up is on the pad now; the close key goes back to set 1.
        compose.onNodeWithTag(ActiveWarmupTag).assertIsDisplayed()
        compose.press("Back to the set")
        compose.waitForIdle()
        compose.onNodeWithTag(ActiveWarmupTag).assertDoesNotExist()
        compose.press("6")
        compose.onNodeWithContentDescription("Log set 1").assertIsDisplayed()
    }

    @Test
    fun theCheckLogsTheSetAsShownAndCanTakeItBack() {
        startDay("barbell_bench_press")
        compose.press("9"); compose.press("0"); compose.press("Next"); compose.press("5")
        compose.onNodeWithContentDescription("Log set 1").performClick()
        compose.waitForDescription("Rest")
        // The set just logged is the one its check takes back.
        compose.onNodeWithContentDescription("Take back set 1").assertIsDisplayed()
    }

    @Test
    fun dumbbellsHaveWarmUpsButNoPlates() {
        startDay("db_bench_press")
        compose.press("3"); compose.press("0"); compose.press("Next")
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Warm-up 1,", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, compose.onAllNodes(hasContentDescription("Plates per side", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun tappingThePadsPlatesOpensTheWholeBar() {
        startDay("barbell_bench_press")
        compose.press("1"); compose.press("0"); compose.press("0")
        compose.onNode(clickLabel("Show the whole bar"), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("100 kg: a 20 kg bar with 2 × 20 on each side").assertIsDisplayed()
    }

    @Test
    // A shorter screen stands in for the why-line a suggestion adds above the table on the phone.
    @Config(qualifiers = "w410dp-h790dp")
    fun withItsWarmUpsTheActiveSetStaysInViewBelowThem() {
        startDay("barbell_bench_press")
        compose.press("1"); compose.press("0"); compose.press("0"); compose.press("Next")
        compose.press("5")
        compose.waitForDescription("Warm-up 4, 85 kg × 2")
        // The active set is in view, and the warm-ups above it are not the rows scrolled away first.
        compose.onNodeWithContentDescription("Log set 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Warm-up 2, 60 kg × 5").assertIsDisplayed()
    }

    @Test
    fun thePadKeepsItsHeightBetweenTheWeightAndTheReps() {
        startDay("barbell_bench_press")
        compose.press("1"); compose.press("0"); compose.press("0")
        val onWeight = compose.onNodeWithTag(PadTag).getBoundsInRoot()
        compose.press("Next")
        compose.waitForIdle()
        val onReps = compose.onNodeWithTag(PadTag).getBoundsInRoot()
        assertEquals(onWeight.top.value, onReps.top.value, 0.5f)
    }
}
