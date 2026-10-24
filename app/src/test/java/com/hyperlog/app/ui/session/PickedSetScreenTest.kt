package com.hyperlog.app.ui.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.PickedSet
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A later, unlogged set tapped to log it out of order (docs/design/logging-screen.md, Set row, picked out of order). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class PickedSetScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    private val plan = listOf(
        SetPrescription(RepRange(6, 8), 1, 180),
        SetPrescription(RepRange(8, 10), 2, 150),
        SetPrescription(RepRange(10, 12), 3, 90),
    )

    private fun session(sets: List<LoggedSet>, setIndices: List<Int>, picked: PickedSet? = null) = ActiveSession(
        id = 1, startedAt = 0,
        exercises = listOf(
            SessionExercise(
                id = 1, exercise = bench, sets = sets, lastTime = emptyList(), ladder = DefaultPlates.ladder(), ladderKnown = true,
                prescription = plan, plates = DefaultPlates, setIndices = setIndices,
            ),
        ),
        currentId = 1, entry = SetEntry("62.5", "9", "", 2, Field.Reps, partialMode = false, fresh = true), padVisible = true,
        restEndsAt = null, restTotalMs = 0, pickedSet = picked,
    )

    private fun show(session: ActiveSession, actions: Recording = Recording()): Recording {
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench), actions, onLeave = {}) } }
        compose.waitForIdle()
        return actions
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test
    fun aLaterSetsRowPutsItOnThePad() {
        val actions = show(session(listOf(LoggedSet(60.0, 7, 0, 1)), setIndices = listOf(0)))

        compose.onNode(clickLabel("Do set 3 now")).performClick()

        assertEquals(listOf(2), actions.picked)
        // The set to do is already on the pad.
        compose.onAllNodes(clickLabel("Do set 2 now")).assertCountEquals(0)
    }

    @Test
    fun thePickedSetIsTheActiveRowAndItsCloseKeyAndBackGoBackToTheSetToDo() {
        val picked = PickedSet(1, 2, SetEntry("55", "11", "", 3, Field.Reps, partialMode = false, fresh = true))
        val actions = show(session(listOf(LoggedSet(60.0, 7, 0, 1)), setIndices = listOf(0), picked))

        // Set 3 is on the pad with its weight, and the set to do steps back: a tap brings the pad back to it.
        compose.onAllNodesWithText("55").assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Log set 3")).assertCountEquals(1)
        compose.onNode(clickLabel("Back to the set")).performClick()
        assertEquals(1, actions.closed)
        // The pad's close key.
        compose.onNode(hasContentDescription("Back to the set")).performClick()
        assertEquals(2, actions.closed)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(3, actions.closed)
    }

    @Test
    fun theSteppedBackSetToDoKeepsItsTarget() {
        val picked = PickedSet(1, 2, SetEntry("55", "11", "", 3, Field.Reps, partialMode = false, fresh = true))
        val logged = session(listOf(LoggedSet(60.0, 7, 0, 1)), setIndices = listOf(0), picked)
        show(logged.copy(entry = logged.entry!!.copy(full = "")))

        // Set 2 waits with its rep range, as an upcoming row shows it.
        compose.onAllNodesWithText("8–10").assertCountEquals(1)
    }

    @Test
    fun aSetLoggedOutOfOrderShowsInItsOwnRow() {
        show(session(listOf(LoggedSet(60.0, 7, 0, 1), LoggedSet(50.0, 11, 0, 3)), setIndices = listOf(0, 2)))

        compose.onAllNodes(hasContentDescription("Correct set 3")).assertCountEquals(1)
        compose.onAllNodes(hasContentDescription("Correct set 2")).assertCountEquals(0)
        // Set 2 is still the set to do.
        compose.onAllNodes(hasContentDescription("Log set 2")).assertCountEquals(1)
    }

    private class Recording : SessionActions {
        val picked = mutableListOf<Int>()
        var closed = 0
        override fun pickSet(index: Int) { picked += index }
        override fun closePickedSet() { closed++ }
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
        override fun addMachine(name: String, ladder: com.hyperlog.app.domain.LadderSpec) = Unit
        override fun changeMachine() = Unit
        override fun saveMachine(item: com.hyperlog.app.gym.EquipmentItem) = Unit
        override fun setUpLadder(spec: com.hyperlog.app.domain.LadderSpec) = Unit
        override fun skipLadderSetup() = Unit
        override fun takeLadderOffer() = Unit
        override fun declineLadderOffer() = Unit
        override fun notAtGym() = Unit
        override fun finishSet() = Unit
        override fun startHold() = Unit
        override fun stopHold() = Unit
        override fun setTechnique(setIndex: Int, technique: Technique?) = Unit
        override fun split(on: Boolean) = Unit
        override fun swap(sessionExerciseId: Long, exerciseId: String) = Unit
        override fun addSet(sessionExerciseId: Long) = Unit
        override fun removeSet(sessionExerciseId: Long, setIndex: Int) = Unit
        override fun setTarget(sessionExerciseId: Long, setIndex: Int, reps: RepRange, rir: Int?) = Unit
        override fun removeExercise(sessionExerciseId: Long) = Unit
        override fun move(sessionExerciseId: Long, by: Int) = Unit
        override fun tickWarmup(index: Int) = Unit
        override fun open(sessionExerciseId: Long) = Unit
        override fun closeView() = Unit
        override fun correct(sessionExerciseId: Long, set: Int, extension: Int?) = Unit
        override fun cancelCorrection() = Unit
    }
}
