package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.HoldPosition
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.FollowUp
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Next row is the session's one clock: rest, a count-up after it, a breathing pause and an iso hold. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class NextRowClockTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))
    private val calls = mutableListOf<String>()

    private fun show(
        restEndsAt: Long? = null, restTotalMs: Long = 0, breathing: Boolean = false,
        holdStartedAt: Long? = null, followUp: FollowUp? = null, padVisible: Boolean = true,
    ) {
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = emptyList(), lastTime = emptyList(), ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = listOf(SetPrescription(RepRange(4, 6), 1, 180)), plates = DefaultPlates,
        )
        val other = SessionExercise(2, bench.copy(id = "squat", name = "Back Squat"), emptyList(), emptyList(), DefaultPlates.ladder(), true)
        val entry = SetEntry("100", "6", "", 1, Field.Reps, partialMode = false, fresh = true)
        val session = ActiveSession(
            id = 1, startedAt = 0, exercises = listOf(exercise, other), currentId = 1, entry = entry,
            padVisible = padVisible, restEndsAt = restEndsAt, restTotalMs = restTotalMs, breathing = breathing,
            holdStartedAt = holdStartedAt, followUp = followUp,
        )
        compose.setContent { HyperlogTheme { SessionScreen(session, listOf(bench), Recording(calls), onLeave = {}) } }
    }

    private fun restRunsOnTheRow(padVisible: Boolean) {
        show(restEndsAt = System.currentTimeMillis() + 90_000, restTotalMs = 180_000, padVisible = padVisible)
        compose.onNodeWithTag(ClockDigitsTag).assertIsDisplayed()
        compose.onNodeWithText("Back Squat").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Rest").assertCountEquals(0)
    }

    @Test
    fun restDrainsOnTheNextRowAndLeavesNoPillInTheHeader() = restRunsOnTheRow(padVisible = true)

    @Test
    fun restShowsOnTheNextRowWithThePadHidden() = restRunsOnTheRow(padVisible = false)

    @Test
    fun theRestOfTheRowStillGoesToTheList() {
        show(restEndsAt = System.currentTimeMillis() + 90_000, restTotalMs = 180_000)
        compose.onNodeWithText("Back Squat").performClick()
        compose.onNodeWithText("Session").assertIsDisplayed()
    }

    @Test
    fun theDigitsOpenTheRestSheet() {
        show(restEndsAt = System.currentTimeMillis() + 90_000, restTotalMs = 180_000)
        compose.onNodeWithTag(ClockDigitsTag).performClick()
        compose.onNodeWithText("End rest").assertIsDisplayed()
    }

    @Test
    fun afterRestTheRowCountsUpMuted() {
        show(restEndsAt = System.currentTimeMillis() - 12_000, restTotalMs = 180_000)
        compose.onNodeWithTag(ClockDigitsTag).assertTextEquals("+0:12")
        compose.onNodeWithTag(NextRowTag).assertIsDisplayed()
    }

    @Test
    fun theHoldRunsOnTheRow() {
        show(holdStartedAt = System.currentTimeMillis() - 5_000, followUp = FollowUp.Hold(30, HoldPosition.Stretch))
        compose.onNodeWithTag(ClockDigitsTag, useUnmergedTree = true).assertTextEquals("0:05")
    }

    @Test
    fun theBreathingPauseRunsOnTheRow() {
        show(restEndsAt = System.currentTimeMillis() + 10_000, restTotalMs = 15_000, breathing = true)
        compose.onNodeWithTag(ClockDigitsTag, useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Breathe").assertCountEquals(0)
    }

}
