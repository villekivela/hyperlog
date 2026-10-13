package com.hyperlog.app.ui.history

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.FreshDatabaseRule
import com.hyperlog.app.MainActivity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.history.LiftGoal
import com.hyperlog.app.history.LiftGoals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** Lift goals on screen (SPEC, Lift goals): the bench's estimate against 100 kg, in History and on its prescription sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class LiftGoalScreenTest {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(compose)

    private val db get() = HyperlogDatabase.get(ApplicationProvider.getApplicationContext<Context>())

    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForTappable(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText(text).and(hasClickAction())).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForDescription(description: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun at(date: LocalDate) = date.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Two finished bench sessions at 1 RIR: 70 × 5, then 75 × 5, an estimate of 75 × 1.2 = 90 kg. */
    private fun benchHistory() = runBlocking {
        val dao = db.sessions()
        listOf(LocalDate.of(2026, 9, 28) to 70.0, LocalDate.of(2026, 10, 1) to 75.0).forEach { (date, kg) ->
            val id = dao.insertSession(SessionEntity(startedAt = at(date), finishedAt = at(date) + 3_600_000))
            val se = dao.insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = "barbell_bench_press", position = 0))
            dao.insertSet(LoggedSetEntity(sessionExerciseId = se, position = 0, weightKg = kg, fullReps = 5, partialReps = 0, rir = 1, loggedAt = at(date)))
        }
    }

    @Test
    fun aGoalSetInHistoryShowsTheEstimateAgainstIt() {
        waitFor("Today")
        benchHistory()
        waitForDescription("History")
        compose.onNodeWithContentDescription("History").performClick()
        waitFor("Progress")
        compose.onNodeWithText("Progress").performClick()
        waitForTappable("Barbell Bench Press")
        compose.onNodeWithText("Barbell Bench Press").performClick()

        waitForDescription("Lift goal")
        compose.onNodeWithContentDescription("Lift goal").performClick()
        waitFor("Save")
        compose.onNode(hasSetTextAction()).performTextInput("100")
        compose.onNodeWithText("Save").performClick()

        waitFor("of 100 kg")
        compose.onNodeWithText("10 kg to go").assertIsDisplayed()
        assertEquals(LiftGoal("barbell_bench_press", 100.0, null), runBlocking { LiftGoals(db).of("barbell_bench_press") })
    }

    @Test
    fun theBenchsPrescriptionSheetShowsTheEstimateAgainstTheGoal() {
        waitFor("Today")
        benchHistory()
        runBlocking { LiftGoals(db).set("barbell_bench_press", 100.0, by = LocalDate.of(2026, 12, 31)) }
        waitFor("Start session")
        compose.onNodeWithText("Start session").performClick()
        waitFor("Add exercise")
        compose.onNodeWithText("Barbell Bench Press").performClick()
        waitForDescription("Hide keypad")

        compose.onNodeWithText("Barbell Bench Press").performClick()

        waitFor("of 100 kg")
        compose.onNodeWithText("90 kg").assertIsDisplayed()
        compose.onNodeWithText("10 kg to go · by Thu 31 Dec").assertIsDisplayed()
        compose.onNodeWithContentDescription("Barbell Bench Press estimate against the goal").assertIsDisplayed()
    }
}
