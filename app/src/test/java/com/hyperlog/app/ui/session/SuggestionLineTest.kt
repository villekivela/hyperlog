package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.PlannedSet
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.progression.Suggestion
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The logging screen says why the active set's weight was suggested. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SuggestionLineTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    private fun show(suggestion: Suggestion?, sets: List<LoggedSet> = emptyList(), hint: String? = null) {
        val plan = List(2) { PlannedSet(RepRange(8, 10), 2, 120) }
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = sets, lastTime = listOf(LoggedSet(60.0, 10, 0, 2), LoggedSet(60.0, 10, 0, 2)),
            ladder = DefaultPlates.ladder(), ladderKnown = true, plan = plan, suggestion = suggestion,
            startingHint = hint,
        )
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1,
            entry = SetEntry("62.5", "8", "", 2, Field.Reps, partialMode = false, fresh = true),
            padVisible = true, restEndsAt = null, restTotalMs = 0,
        )
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench), NoActions, onLeave = {}) } }
    }

    @Test
    fun aSuggestionShowsWhyAboveTheSets() {
        show(Suggestion(62.5, 8, Suggestion.Kind.Up, "Last time 60 kg × 10, 10 @ 2, top of 8–10 → 62.5 kg"))
        compose.onNodeWithText("Last time 60 kg × 10, 10 @ 2, top of 8–10 → 62.5 kg").assertIsDisplayed()
        compose.onNodeWithTag(SuggestionTag).assertIsDisplayed()
    }

    @Test
    fun anEstimatedWeightSaysToCheckItOnTheFirstSet() {
        show(Suggestion(70.0, 6, Suggestion.Kind.Estimated, "Estimated from 60 kg × 9 @ 2, check on the first set"))
        compose.onNodeWithText("Estimated from 60 kg × 9 @ 2, check on the first set").assertIsDisplayed()
    }

    @Test
    fun aStartingWeightSaysItComesFromTheOldApp() {
        show(Suggestion(60.0, 8, Suggestion.Kind.Starting, "From old app: 60 kg × 8 @ 2 RIR, 12 Sep"))
        compose.onNodeWithText("From old app: 60 kg × 8 @ 2 RIR, 12 Sep").assertIsDisplayed()
    }

    @Test
    fun aMachineShowsTheOldWeightAsAHint() {
        show(null, hint = "45 kg × 12 @ 1 RIR, 12 Sep")
        compose.onNodeWithText("From old app, another machine").assertIsDisplayed()
        compose.onNodeWithText("45 kg × 12 @ 1 RIR, 12 Sep").assertIsDisplayed()
        compose.onNodeWithTag(StartingHintTag).assertIsDisplayed()
    }

    @Test
    fun aSetThatJustFollowsTheOneBeforeHasNoLine() {
        show(Suggestion(62.5, 8, Suggestion.Kind.Follow, null), sets = listOf(LoggedSet(62.5, 8, 0, 2)))
        compose.onNodeWithTag(SuggestionTag).assertDoesNotExist()
    }
}

private object NoActions : com.hyperlog.app.ui.session.SessionActions {
    override fun press(key: PadKey) = Unit
    override fun done() = Unit
    override fun selectRir(value: Int) = Unit
    override fun focus(field: Field) = Unit
    override fun partialMode(on: Boolean) = Unit
    override fun pickWeight(kg: Double) = Unit
    override fun setPadVisible(visible: Boolean) = Unit
    override fun addExercise(exerciseId: String) = Unit
    override fun switchTo(sessionExerciseId: Long) = Unit
    override fun adjustRest(deltaMs: Long) = Unit
    override fun endRest() = Unit
    override fun finish() = Unit
    override fun chooseMachine(itemId: Long) = Unit
    override fun addMachine(name: String, ladder: LadderSpec) = Unit
    override fun changeMachine() = Unit
    override fun finishSet() = Unit
    override fun startHold() = Unit
    override fun stopHold() = Unit
    override fun setTechnique(setIndex: Int, technique: com.hyperlog.app.program.Technique?) = Unit
    override fun split(on: Boolean) = Unit
    override fun swap(sessionExerciseId: Long, exerciseId: String) = Unit
    override fun addSet(sessionExerciseId: Long) = Unit
    override fun removeSet(sessionExerciseId: Long, setIndex: Int) = Unit
    override fun setTarget(sessionExerciseId: Long, setIndex: Int, reps: com.hyperlog.app.program.RepRange, rir: Int?) = Unit
    override fun removeExercise(sessionExerciseId: Long) = Unit
    override fun move(sessionExerciseId: Long, by: Int) = Unit
}
