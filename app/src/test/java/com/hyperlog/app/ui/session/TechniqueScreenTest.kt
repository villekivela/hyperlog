package com.hyperlog.app.ui.session

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.press
import com.hyperlog.app.ui.program.ProgramStore
import com.hyperlog.app.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A set's technique is named under it with its planned drops or mini-sets, and runs on the pad. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class TechniqueScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private fun startDay(exercise: String, sets: String) {
        compose.waitFor("Today")
        runBlocking {
            ProgramStore.get(ApplicationProvider.getApplicationContext<Application>()).import(
                """
                { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 2, "days": [
                  { "id": "a", "name": "Day A", "groups": [ { "slots": [ { "id": "s", "exercise": "$exercise", "sets": $sets } ] } ] } ] } ] }
                """,
            )
        }
        compose.waitFor("Day A")
        compose.onNodeWithText("Start session").performClick()
        compose.waitFor("Target")
    }

    @Test
    fun aDropSetShowsItsPlannedDropsBeforeTheSetAndRunsThemOnThePad() {
        startDay("db_bench_press", """[ { "reps": "8-10", "rir": 1, "technique": { "kind": "dropset", "drops": 2 } } ]""")
        compose.onNodeWithText("Drop set, 2 drops of 25%").assertIsDisplayed()
        compose.press("2"); compose.press("5")
        // The drops follow the weight as it is typed: 25 − 25% = 17.5, then 12.5 on the dumbbells.
        compose.waitFor("17.5")
        compose.waitFor("12.5")

        compose.press("Next"); compose.press("Log set")
        compose.waitFor("Drop 1 of 2")
        compose.onNodeWithText("Finish set").assertIsDisplayed()
        // No rest before a drop.
        compose.onAllNodesWithContentDescription("Rest").assertCountEquals(0)
    }

    @Test
    fun myoRepsBreatheBeforeEachMiniSet() {
        startDay("db_bench_press", """[ { "reps": "10-12", "rir": 0, "technique": { "kind": "myo_reps", "miniSetReps": 4 } } ]""")
        compose.onNodeWithText("Myo-reps, mini-sets of 4").assertIsDisplayed()
        compose.press("2"); compose.press("0"); compose.press("Next"); compose.press("Log set")
        compose.waitFor("Mini-set 1 of 5")
        compose.onNodeWithContentDescription("Breathe").assertIsDisplayed()
    }

    @Test
    fun aSetCanBeSwitchedToADropSetFromItsOptions() {
        startDay("db_bench_press", """[ { "count": 2, "reps": "8-10", "rir": 1 } ]""")
        compose.press("Set 2 options")
        compose.waitFor("Intensity technique")
        compose.onNodeWithText("Drop set").performClick()
        compose.waitForIdle()
        compose.waitFor("Drop set, 2 drops of 25%")
    }
}
