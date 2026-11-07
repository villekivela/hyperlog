package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The room a short exercise leaves above its table holds its cue, in words, or nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class CueBandTest {
    @get:Rule
    val compose = createComposeRule()

    private val cued = Exercise("hack_squat", "Hack Squat", Equipment.Machine, MovementPattern.Squat, GripDemand.None, listOf("Quads"), note = "Knees track the toes.")
    private val slot = PlannedSlot("s", "1", 0, cued.id, emptyList(), null, listOf(SetPrescription(RepRange(8, 10), 1, 90)))

    private fun show(exercise: Exercise = cued, sets: Int = 1, slotNote: String? = null) {
        val e = SessionExercise(
            1, exercise, emptyList(), emptyList(), DefaultPlates.ladder(), true, slot = slot.copy(note = slotNote),
            prescription = List(sets) { SetPrescription(RepRange(8, 10), 1, 90) },
        )
        val session = ActiveSession(1, 0, listOf(e), 1, SetEntry.prefill(null), padVisible = true, restEndsAt = null, restTotalMs = 90_000)
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(exercise), Recording(mutableListOf()), onLeave = {}) } }
    }

    @Test
    fun aShortExerciseHasItsCueInTheBand() {
        show()
        compose.onNodeWithTag(CueBandTag, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Knees track the toes.").assertExists()
    }

    @Test
    fun theSlotsNoteIsTheCueOverTheExercisesOwn() {
        show(slotNote = "Pause at the bottom.")
        compose.onNodeWithText("Pause at the bottom.").assertExists()
        compose.onAllNodesWithText("Knees track the toes.").assertCountEquals(0)
    }

    @Test
    fun aTapOnTheCueOpensTheExercise() {
        show()
        compose.onNodeWithTag(CueBandTag, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        // The band's cue, and the sheet's.
        compose.onAllNodesWithText("Knees track the toes.").assertCountEquals(2)
    }

    @Test
    fun theCueIsATapTargetAcrossTheScreen() {
        show()
        val band = compose.onNodeWithTag(CueBandTag, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("The band is ${band.bottom - band.top} tall", band.bottom - band.top >= 48.dp)
        assertTrue("The band is ${band.right - band.left} wide", band.right - band.left >= 410.dp)
    }

    @Test
    fun noCueNoBand() {
        show(cued.copy(note = null))
        compose.onAllNodesWithTag(CueBandTag, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aTableThatLeavesNoRoomHasNoBand() {
        show(sets = 12)
        compose.onAllNodesWithTag(CueBandTag, useUnmergedTree = true).assertCountEquals(0)
    }
}
