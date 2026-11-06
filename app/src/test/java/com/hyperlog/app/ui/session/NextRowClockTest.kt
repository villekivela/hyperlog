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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.hyperlog.app.ui.theme.GraphiteDark
import com.hyperlog.app.ui.theme.GraphiteLight
import org.junit.Assert.assertTrue
import com.hyperlog.app.program.TechniqueKind
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.theme.onTechniqueColor
import com.hyperlog.app.ui.theme.techniqueColor
import org.junit.Assert.assertEquals
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

    private fun resting(restEndsAt: Long) = ActiveSession(
        id = 1, startedAt = 0, exercises = emptyList(), currentId = null, entry = null,
        padVisible = true, restEndsAt = restEndsAt, restTotalMs = 180_000,
    )

    /** A session left open would count up without end (`+220:45`), which says nothing about rest. */
    @Test
    fun theCountUpLeavesTheRowAfterAnHour() {
        val hour = 3_600_000L
        val ended = 1_000_000L
        assertEquals("+59:59", rowClock(resting(ended), ended + hour - 1_000)?.time)
        assertEquals(null, rowClock(resting(ended), ended + hour))
        assertEquals(null, rowClock(resting(ended), ended + 220 * 60_000L))
    }

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

/** The clock's numbers, at the row's seam: how full the row is and what it reads. */
class RowClockTest {
    private fun session(restEndsAt: Long? = null, total: Long = 0, breathing: Boolean = false, holdStartedAt: Long? = null, followUp: FollowUp? = null) =
        ActiveSession(
            id = 1, startedAt = 0, exercises = emptyList(), currentId = null, entry = null, padVisible = true,
            restEndsAt = restEndsAt, restTotalMs = total, breathing = breathing, holdStartedAt = holdStartedAt, followUp = followUp,
        )

    @Test
    fun restDrainsFromFullToEmpty() {
        val s = session(restEndsAt = 100_000, total = 100_000)
        assertEquals(1f, rowClock(s, 0)!!.fraction, 0.001f)
        assertEquals(0.25f, rowClock(s, 75_000)!!.fraction, 0.001f)
        assertEquals("0:25", rowClock(s, 75_000)!!.time)
    }

    @Test
    fun afterRestItCountsUpWithNothingFilled() {
        val c = rowClock(session(restEndsAt = 100_000, total = 100_000), 112_000)!!
        assertEquals(ClockKind.Over, c.kind)
        assertEquals("+0:12", c.time)
        assertEquals(0f, c.fraction, 0f)
    }

    @Test
    fun theHoldFillsTowardItsTarget() {
        val s = session(holdStartedAt = 0, followUp = FollowUp.Hold(20, HoldPosition.Stretch))
        assertEquals(0.5f, rowClock(s, 10_000)!!.fraction, 0.001f)
        assertEquals(ClockKind.HoldReached, rowClock(s, 20_000)!!.kind)
    }

    @Test
    fun aReachedHoldStopsItsDigitsAtTheTarget() {
        val s = session(holdStartedAt = 0, followUp = FollowUp.Hold(20, HoldPosition.Stretch))
        assertEquals("0:19", rowClock(s, 19_000)!!.time)
        assertEquals("0:20", rowClock(s, 20_000)!!.time)
        assertEquals("0:20", rowClock(s, 47_000)!!.time)
        assertEquals(1f, rowClock(s, 47_000)!!.fraction, 0f)
    }

    @Test
    fun breathingTakesTheMyoVioletAndTheHoldTheIsoBlue() {
        listOf(false, true).forEach { dark ->
            assertEquals(techniqueColor(TechniqueKind.MyoReps, dark).copy(alpha = ClockTintAlpha), ClockKind.Breath.fill(dark, Color.Red))
            assertEquals(techniqueColor(TechniqueKind.IsoHold, dark).copy(alpha = ClockTintAlpha), ClockKind.Hold.fill(dark, Color.Red))
            assertEquals(techniqueColor(TechniqueKind.IsoHold, dark), ClockKind.HoldReached.fill(dark, Color.Red))
            assertEquals(onTechniqueColor(dark), ClockKind.HoldReached.ink(dark, onSurface = Color.Red, muted = Color.Red))
        }
    }

    @Test
    fun theDigitsStayReadableOnEveryFillOverTheRowsSurface() {
        listOf(false, true).forEach { dark ->
            val scheme = if (dark) GraphiteDark else GraphiteLight
            ClockKind.entries.forEach { kind ->
                val fill = kind.fill(dark, scheme.primary)
                val ink = kind.ink(dark, scheme.onSurface, scheme.onSurfaceVariant)
                listOf(scheme.surfaceContainer, scheme.surface).forEach { row ->
                    val ratio = contrast(ink, fill.compositeOver(row))
                    assertTrue("$kind (dark=$dark) digits on $row are $ratio:1", ratio >= 4.5f)
                }
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun nothingRunsNothingShows() {
        assertEquals(null, rowClock(session(), 0))
        assertEquals(null, rowClock(session(restEndsAt = 5_000, total = 15_000, breathing = true), 6_000))
    }
}
