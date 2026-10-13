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
import com.hyperlog.app.assertButtonOrder
import com.hyperlog.app.click
import com.hyperlog.app.ui.program.ProgramStore
import com.hyperlog.app.waitFor
import com.hyperlog.app.waitForDescription
import com.hyperlog.app.waitForNoDescription
import com.hyperlog.app.waitGone
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

    private fun startDay() {
        compose.waitFor("Today")
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
        compose.waitFor("Day A")
        compose.click("Start session")
        compose.waitFor("Target")
    }

    @Test
    fun theMenuSwapsTheExerciseToAnAlternative() {
        startDay()
        compose.onNodeWithContentDescription("More").performClick()
        compose.click("Swap exercise")
        compose.waitFor("Alternatives")
        compose.click("DB Bench Press")
        compose.waitFor("DB Bench Press")
        compose.waitGone("Barbell Bench Press")
    }

    @Test
    fun addSetAddsARowAndTheSetSheetRemovesOne() {
        startDay()
        compose.click("Add set")
        compose.waitForDescription("Set 3 options")

        compose.onNodeWithContentDescription("Set 2 options").performClick()
        compose.waitFor("Remove set")
        compose.click("Remove set")
        compose.waitForNoDescription("Set 3 options")
    }

    @Test
    fun theSetSheetChangesTheTarget() {
        startDay()
        compose.onNodeWithContentDescription("Set 2 options").performClick()
        compose.waitFor("Target")
        compose.onNodeWithContentDescription("Raise the top of the range").performClick()
        compose.onNodeWithContentDescription("Raise the top of the range").performClick()
        compose.onNodeWithContentDescription("More in reserve").performClick()
        compose.waitFor("6–10")
    }

    @Test
    fun theSessionListMovesAndRemovesExercises() {
        startDay()
        compose.click("DB Lateral Raise")
        compose.waitFor("Session")
        compose.onAllNodesWithContentDescription("Edit DB Lateral Raise")[0].performClick()
        compose.click("Remove from session")
        compose.waitFor("Remove DB Lateral Raise?")
        compose.assertButtonOrder("Cancel", "Remove")
        compose.click("Remove")
        compose.waitGone("DB Lateral Raise")
        compose.onAllNodesWithText("Barbell Bench Press")[0].assertIsDisplayed()
    }
}
