package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.hyperlog.app.program.Backoff
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import androidx.compose.ui.test.getBoundsInRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The set table's index and check columns, and Next on the pad (docs/design/logging-screen.md). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SetTableTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))
    private val calls = mutableListOf<String>()

    /** [warmups]: false leaves the weight being typed with no ramp yet, so no warm-ups show. */
    private fun show(sets: List<LoggedSet>, plan: List<SetPrescription>, latest: Pair<Long, Int>? = null, warmups: Boolean = true, reps: String = "6") {
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = sets, lastTime = emptyList(), ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = plan, plates = DefaultPlates,
        )
        val other = SessionExercise(2, bench.copy(id = "squat", name = "Back Squat"), emptyList(), emptyList(), DefaultPlates.ladder(), true)
        val entry = SetEntry("100", reps, "", 1, if (warmups) Field.Reps else Field.Weight, partialMode = false, fresh = true)
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(exercise, other), currentId = 1, entry = entry,
            padVisible = true, restEndsAt = null, restTotalMs = 0, rampEntry = entry.takeIf { warmups }, latestSet = latest,
        )
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench), Recording(calls), onLeave = {}) } }
    }

    private val top = SetPrescription(RepRange(4, 6), 1, 180)

    @Test
    fun techniqueSetsWearTheirBadgeAndBackOffsNone() {
        show(
            emptyList(),
            listOf(top, SetPrescription(RepRange(6, 8), 1, 180, backoff = Backoff(0, 10)), SetPrescription(RepRange(8, 10), 0, 120, technique = Technique.Dropset())),
            warmups = false,
        )
        compose.onNodeWithContentDescription("Drop set").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Myo-reps").assertCountEquals(0)
        // No technique or back-off rows: the tags are gone.
        compose.onAllNodesWithText("Back-off", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Drop set,", substring = true).assertCountEquals(0)
        // Targets are placeholders in the empty cells.
        compose.onNodeWithText("6–8").assertIsDisplayed()
        // `+` at the drop set's foot adds a drop.
        compose.onNodeWithContentDescription("Add a drop").performClick()
        assertEquals("addDropOrMiniSet 2", calls.last())
    }

    /** Set 1 logged with [technique] and [done] of its drops or mini-sets, the set still open for [followUp]. */
    private fun showOpen(technique: Technique, done: Int, followUp: com.hyperlog.app.session.FollowUp) {
        val kind = if (technique is Technique.Dropset) com.hyperlog.app.domain.Extension.Kind.Drop else com.hyperlog.app.domain.Extension.Kind.MiniSet
        val set = LoggedSet(25.0, 9, 0, 1, technique = technique, extensions = List(done) { com.hyperlog.app.domain.Extension(kind, 17.5, 6) })
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = listOf(set), lastTime = emptyList(), ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = listOf(SetPrescription(RepRange(8, 10), 1, 120, technique = technique)), plates = DefaultPlates,
        )
        val entry = SetEntry("17.5", "6", "", null, Field.Reps, partialMode = false, fresh = true)
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = entry,
            padVisible = true, restEndsAt = null, restTotalMs = 0, followUp = followUp, openSetId = 7, latestSet = 1L to 0,
        )
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench), Recording(calls), onLeave = {}) } }
    }

    @Test
    fun plusAddsADropToADropSetUnderWay() {
        showOpen(Technique.Dropset(2, 25), done = 1, com.hyperlog.app.session.FollowUp.Drop(1, 2))
        compose.onNodeWithContentDescription("Add a drop").performClick()
        assertEquals("addDropOrMiniSet 0", calls.last())
    }

    @Test
    fun plusAddsAMiniSetToMyoRepsUnderWay() {
        showOpen(Technique.MyoReps(4, 3), done = 1, com.hyperlog.app.session.FollowUp.MiniSet(1, 3, 4))
        compose.onNodeWithContentDescription("Add a mini-set").performClick()
        assertEquals("addDropOrMiniSet 0", calls.last())
    }

    @Test
    fun plusAddsAMiniSetBeforeMyoRepsStart() {
        show(emptyList(), listOf(SetPrescription(RepRange(10, 12), 0, 90, technique = Technique.MyoReps(4, 3))), warmups = false)
        compose.onNodeWithContentDescription("Add a mini-set").performClick()
        assertEquals("addDropOrMiniSet 0", calls.last())
    }

    @Test
    fun theSwapChipOpensSwap() {
        val db = bench.copy(id = "db_bench_press", name = "DB Bench Press", equipment = Equipment.Dumbbell)
        val slot = com.hyperlog.app.program.PlannedSlot("s", "1", 0, "barbell_bench_press", emptyList(), null, listOf(top))
        val exercise = SessionExercise(
            id = 1, exercise = db, sets = emptyList(), lastTime = emptyList(), ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = listOf(top), slot = slot,
        )
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = SetEntry("25", "", "", 1, Field.Reps, partialMode = false, fresh = true),
            padVisible = true, restEndsAt = null, restTotalMs = 0,
        )
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench, db), Recording(calls), onLeave = {}) } }
        compose.onNodeWithText("for Barbell Bench Press").performClick()
        compose.onNodeWithText("Swap DB Bench Press").assertIsDisplayed()
    }

    @Test
    fun theSupersetChipGoesToThePartner() {
        val slot = com.hyperlog.app.program.PlannedSlot("a", "1a", 0, "barbell_bench_press", emptyList(), null, listOf(top))
        val curl = bench.copy(id = "leg_curl", name = "Seated Leg Curl")
        val exercises = listOf(
            SessionExercise(1, bench, emptyList(), emptyList(), DefaultPlates.ladder(), true, prescription = listOf(top), slot = slot),
            SessionExercise(2, curl, emptyList(), emptyList(), DefaultPlates.ladder(), true, prescription = listOf(top), slot = slot.copy(id = "b", label = "1b", exercise = "leg_curl")),
        )
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = exercises, currentId = 1, entry = SetEntry("100", "", "", 1, Field.Reps, partialMode = false, fresh = true),
            padVisible = true, restEndsAt = null, restTotalMs = 0,
        )
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench, curl), Recording(calls), onLeave = {}) } }
        compose.onNodeWithText("1a + Seated Leg Curl").performClick()
        assertEquals("switchTo 2", calls.last())
    }

    @Test
    fun warmUpsAreFullRowsAboveTheSets() {
        show(emptyList(), listOf(top))
        compose.onNodeWithContentDescription("Warm-up 1, 45 kg × 8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Log set 1").assertIsDisplayed()
    }

    @Test
    fun theActiveSetsCheckLogsItAndTheLastLoggedOnesTakesItBack() {
        show(listOf(LoggedSet(100.0, 6, 0, 1)), listOf(top, top), latest = 1L to 0, warmups = false)
        compose.onNodeWithContentDescription("Log set 2").performClick()
        assertEquals("logShown", calls.last())
        compose.onNodeWithContentDescription("Take back set 1").assertIsOn().performClick()
        assertEquals("undo 1", calls.last())
    }

    @Test
    fun theRepsStartEmptyUnderTheTargetRangeAndTheCheckWaitsForThem() {
        show(emptyList(), listOf(top), warmups = false, reps = "")
        // The range is the muted placeholder; no ✓ logs anything yet.
        compose.onNodeWithText("4–6").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Log set 1").assertCountEquals(0)
    }

    @Test
    fun theCheckLogsOnceRepsAreTyped() {
        show(emptyList(), listOf(top), warmups = false, reps = "5")
        compose.onAllNodesWithText("4–6").assertCountEquals(0)
        compose.onNodeWithContentDescription("Log set 1").performClick()
        assertEquals("logShown", calls.last())
    }

    @Test
    fun aDividerSeparatesNextFromThePlateStripInLightAndDark() {
        for (night in listOf("+notnight", "+night")) {
            org.robolectric.RuntimeEnvironment.setQualifiers(night)
            show(emptyList(), listOf(top), warmups = false)
            compose.waitForIdle()
            val next = compose.onNodeWithTag(NextRowTag).getBoundsInRoot()
            val divider = compose.onNodeWithTag(PadDividerTag).assertIsDisplayed().getBoundsInRoot()
            val strip = compose.onNodeWithTag(PlateStripTag).getBoundsInRoot()
            assertTrue("$night: Next ends above the divider", next.bottom <= divider.top)
            assertTrue("$night: the divider ends above the plate strip", divider.bottom <= strip.top)
        }
    }

    @Test
    fun nextIsThePadsTopRow() {
        show(emptyList(), listOf(top))
        compose.onNodeWithTag(NextRowTag).assertIsDisplayed()
        compose.onNodeWithText("Back Squat").assertIsDisplayed()
    }
}

private class Recording(val calls: MutableList<String>) : SessionActions {
    override fun logShown() { calls += "logShown" }
    override fun undo(sessionExerciseId: Long) { calls += "undo $sessionExerciseId" }
    override fun addDropOrMiniSet(setIndex: Int) { calls += "addDropOrMiniSet $setIndex" }
    override fun setTechnique(setIndex: Int, technique: Technique?) { calls += "setTechnique $setIndex $technique" }
    override fun press(key: PadKey) = Unit
    override fun done() = Unit
    override fun selectRir(value: Int) = Unit
    override fun focus(field: Field) = Unit
    override fun partialMode(on: Boolean) = Unit
    override fun pickWeight(kg: Double) = Unit
    override fun setPadVisible(visible: Boolean) = Unit
    override fun addExercise(exerciseId: String) = Unit
    override fun switchTo(sessionExerciseId: Long) { calls += "switchTo $sessionExerciseId" }
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
    override fun finishSet() = Unit
    override fun startHold() = Unit
    override fun stopHold() = Unit
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
