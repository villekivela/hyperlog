package com.hyperlog.app.ui.session

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
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
import com.hyperlog.app.ui.program.ProgramStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Editing the session on the fly from the screen (SPEC, Today → Edit on the fly). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SessionEditScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun gone(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
    }

    private fun click(text: String) = compose.onNodeWithText(text).performClick().also { compose.waitForIdle() }

    private fun startDay() {
        waitFor("Today")
        runBlocking {
            ProgramStore.get(ApplicationProvider.getApplicationContext<Application>()).import(
                """
                { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 2, "days": [
                  { "id": "a", "name": "Day A", "groups": [
                    { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "alternatives": [ "db_bench_press" ],
                      "sets": [ { "count": 2, "reps": "6-8", "rir": 1 } ] } ] },
                    { "slots": [ { "id": "lateral", "exercise": "db_lateral_raise", "sets": [ { "count": 2, "reps": "10-12", "rir": 0 } ] } ] } ] } ] } ] }
                """,
            )
        }
        waitFor("Day A")
        click("Start session")
        waitFor("Target")
    }

    @Test
    fun theMenuSwapsTheExerciseToAnAlternative() {
        startDay()
        compose.onNodeWithContentDescription("More").performClick()
        click("Swap exercise")
        waitFor("Alternatives")
        click("DB Bench Press")
        waitFor("DB Bench Press")
        gone("Barbell Bench Press")
    }

    @Test
    fun addSetAddsARowAndTheSetSheetRemovesOne() {
        startDay()
        click("Add set")
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Set 3 options").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("Set 2 options").performClick()
        waitFor("Remove set")
        click("Remove set")
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Set 3 options").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun theSetSheetChangesTheTarget() {
        startDay()
        compose.onNodeWithContentDescription("Set 2 options").performClick()
        waitFor("Target")
        compose.onNodeWithContentDescription("Raise the top of the range").performClick()
        compose.onNodeWithContentDescription("Raise the top of the range").performClick()
        compose.onNodeWithContentDescription("More in reserve").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("6–10").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theSessionListMovesAndRemovesExercises() {
        startDay()
        click("DB Lateral Raise")
        waitFor("Session")
        compose.onAllNodesWithContentDescription("Edit DB Lateral Raise")[0].performClick()
        click("Remove from session")
        waitFor("Remove DB Lateral Raise?")
        click("Remove")
        gone("DB Lateral Raise")
        compose.onAllNodesWithText("Barbell Bench Press")[0].assertIsDisplayed()
    }
}
