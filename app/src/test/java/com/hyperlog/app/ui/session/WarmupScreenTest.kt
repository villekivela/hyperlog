package com.hyperlog.app.ui.session

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
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

/** Warm-ups show above the first work set, each with its plates, and the work set shows its plates per side. */
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
        compose.waitFor("Target")
    }

    @Test
    fun aNinetyKgBenchShowsItsRampAndThePlatesPerSide() {
        startDay("barbell_bench_press")
        compose.press("9"); compose.press("0")
        compose.waitFor("Warm-up · plates per side")
        // 40, 55, 65 and 75 kg, each with its plates.
        compose.onNodeWithContentDescription("Warm-up 1, 40 kg × 8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Warm-up 4, 75 kg × 2").assertIsDisplayed()
        compose.onNodeWithText("20 · 5 · 2.5").assertIsDisplayed()
        compose.onNodeWithText("20 · 15 per side").assertIsDisplayed()
    }

    @Test
    fun aWarmUpTicksOffWithATapAndTheWorkSetStaysActive() {
        startDay("barbell_bench_press")
        compose.press("9"); compose.press("0"); compose.press("Next")
        compose.waitFor("Warm-up · plates per side")
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
        compose.onNodeWithText("20 · 10 per side").assertIsDisplayed()
    }

    @Test
    fun dumbbellsHaveWarmUpsButNoPlates() {
        startDay("db_bench_press")
        compose.press("3"); compose.press("0")
        compose.waitFor("Warm-up")
        assertEquals(0, compose.onAllNodes(hasText("per side", substring = true)).fetchSemanticsNodes().size)
    }
}
