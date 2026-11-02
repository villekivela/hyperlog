package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Bench row above an adjustable-bench exercise's sets, and its sheet (SPEC, Today → Bench angle). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class BenchAngleScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val incline = Exercise(
        "db_incline_press", "Incline DB Press", Equipment.Dumbbell, MovementPattern.HorizontalPush, GripDemand.High, listOf("Chest"),
        adjustableBench = true,
    )
    private val flat = incline.copy(id = "db_bench_press", name = "DB Bench Press", adjustableBench = false)
    private val saved = mutableListOf<Pair<Long, Int>>()

    private fun show(exercise: Exercise, benchAngle: Int? = null, lastBenchAngle: Int? = null) {
        val current = SessionExercise(
            id = 1, exercise = exercise, sets = emptyList(), lastTime = emptyList(), ladder = WeightLadder.Default, ladderKnown = true,
            benchAngle = benchAngle, lastBenchAngle = lastBenchAngle,
        )
        val entry = SetEntry("25", "", "", null, Field.Weight, partialMode = false, fresh = true)
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(current), currentId = 1, entry = entry,
            padVisible = true, restEndsAt = null, restTotalMs = 0,
        )
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(exercise), Saving(saved), onLeave = {}) } }
    }

    private fun key(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(BenchSheetTag)))

    @Test
    fun anAdjustableBenchExerciseShowsLastTimesAngleAndTakesATypedOne() {
        show(incline, lastBenchAngle = 30)
        compose.onNodeWithText("Bench").assertIsDisplayed()
        compose.onNodeWithText("30°").assertIsDisplayed()

        compose.onNodeWithText("Bench").performClick()
        // Robolectric has no tilt sensor: the angle is typed.
        key("4").performClick()
        key("5").performClick()
        compose.onNode(hasContentDescription("Save 45°") and hasAnyAncestor(hasTestTag(BenchSheetTag))).performClick()
        assertEquals(listOf(1L to 45), saved)
    }

    @Test
    fun aSessionsSummaryNamesTheAngleEachExerciseWasDoneAt() {
        compose.setContent {
            HyperlogTheme {
                androidx.compose.foundation.layout.Column {
                    SummaryRows(listOf(com.hyperlog.app.session.ExerciseSummary("Incline DB Press", listOf(com.hyperlog.app.domain.LoggedSet(25.0, 10, 0, 2)), benchAngle = 32)), onOpen = null)
                }
            }
        }
        compose.onNodeWithText("Incline DB Press · 32°").assertIsDisplayed()
    }

    @Test
    fun anExerciseWithoutAnAdjustableBenchHasNoBenchRow() {
        show(flat)
        compose.onAllNodesWithText("Bench").assertCountEquals(0)
    }

    @Test
    fun theSheetSavesTheLiveReadingUntilAKeyIsTyped() {
        var used: Int? = null
        compose.setContent { HyperlogTheme { BenchAngleSheet("Incline DB Press", target = 30, againstToday = false, start = null, reading = 34, measuring = true, onSave = { used = it }, onClose = {}) } }
        compose.onNodeWithText("34°").assertIsDisplayed()
        compose.onNodeWithText("4° over 30°").assertIsDisplayed()
        compose.onNodeWithText("From the phone").assertIsDisplayed()
        compose.onNode(hasContentDescription("Save 34°")).performClick()
        assertEquals(34, used)

        key("3").performClick()
        key("2").performClick()
        // Within 3° of last time reads on target.
        compose.onNodeWithText("On target, 30°").assertIsDisplayed()
        compose.onNodeWithText("Typed").assertIsDisplayed()
        compose.onNode(hasContentDescription("Save 32°")).performClick()
        assertEquals(32, used)
    }

    @Test
    fun onceAnAngleIsSetTodayTheSheetComparesWithIt() {
        show(incline, benchAngle = 32, lastBenchAngle = 40)
        compose.onNodeWithText("Bench").performClick()
        // Robolectric has no tilt sensor, so the sheet starts from againstToday's 32°, the target now.
        compose.onNodeWithText("On target, 32° set today").assertIsDisplayed()

        key("3").performClick()
        key("6").performClick()
        compose.onNodeWithText("4° over 32° set today").assertIsDisplayed()
    }

    @Test
    fun minusAndPlusStepWhatShowsByADegreeWithin0To90() {
        var used: Int? = null
        compose.setContent { HyperlogTheme { BenchAngleSheet("Incline DB Press", target = 30, againstToday = false, start = null, reading = 34, measuring = true, onSave = { used = it }, onClose = {}) } }
        compose.onNode(hasContentDescription("1° more")).performClick()
        compose.onNodeWithText("35°").assertIsDisplayed()
        compose.onNodeWithText("Typed").assertIsDisplayed()
        compose.onNode(hasContentDescription("1° less")).performClick()
        compose.onNode(hasContentDescription("1° less")).performClick()
        compose.onNode(hasContentDescription("Save 33°")).performClick()
        assertEquals(33, used)

        compose.onNode(hasContentDescription("Delete")).performClick()
        compose.onNode(hasContentDescription("Delete")).performClick()
        key("9").performClick()
        key("5").performClick()
        compose.onNode(hasContentDescription("1° more")).performClick()
        compose.onNode(hasContentDescription("Save 90°")).performClick()
        assertEquals(90, used)
    }

    @Test
    fun aFirstTimeBenchCellSaysWhatItIsFor() {
        show(incline)
        compose.onNodeWithText("Set").assertIsDisplayed()
    }
}

private class Saving(val saved: MutableList<Pair<Long, Int>>) : SessionActions {
    override fun setBenchAngle(sessionExerciseId: Long, angle: Int) { saved += sessionExerciseId to angle }
    override fun press(key: PadKey) = Unit
    override fun done() = Unit
    override fun selectRir(value: Int) = Unit
    override fun focus(field: Field) = Unit
    override fun partialMode(on: Boolean) = Unit
    override fun pickWeight(kg: Double) = Unit
    override fun setPadVisible(visible: Boolean) = Unit
    override fun finishSet() = Unit
    override fun startHold() = Unit
    override fun stopHold() = Unit
    override fun split(on: Boolean) = Unit
    override fun cancelCorrection() = Unit
    override fun addExercise(exerciseId: String) = Unit
    override fun switchTo(sessionExerciseId: Long) = Unit
    override fun adjustRest(deltaMs: Long) = Unit
    override fun endRest() = Unit
    override fun finish() = Unit
    override fun chooseMachine(itemId: Long) = Unit
    override fun addMachine(name: String, ladder: LadderSpec) = Unit
    override fun changeMachine() = Unit
    override fun saveMachine(item: com.hyperlog.app.gym.EquipmentItem) = Unit
    override fun setUpLadder(spec: LadderSpec) = Unit
    override fun skipLadderSetup() = Unit
    override fun takeLadderOffer() = Unit
    override fun declineLadderOffer() = Unit
    override fun notAtGym() = Unit
    override fun setTechnique(setIndex: Int, technique: com.hyperlog.app.program.Technique?) = Unit
    override fun swap(sessionExerciseId: Long, exerciseId: String) = Unit
    override fun addSet(sessionExerciseId: Long) = Unit
    override fun removeSet(sessionExerciseId: Long, setIndex: Int) = Unit
    override fun setTarget(sessionExerciseId: Long, setIndex: Int, reps: com.hyperlog.app.program.RepRange, rir: Int?) = Unit
    override fun removeExercise(sessionExerciseId: Long) = Unit
    override fun move(sessionExerciseId: Long, by: Int) = Unit
    override fun tickWarmup(index: Int) = Unit
    override fun open(sessionExerciseId: Long) = Unit
    override fun closeView() = Unit
    override fun correct(sessionExerciseId: Long, set: Int, extension: Int?) = Unit
}
