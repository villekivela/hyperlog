package com.hyperlog.app.ui.session

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.progression.Record
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a set logged feels like (#272): a haptic each time, and a moment for a record that never holds up the next set. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SetMomentsTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("bench", "Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))
    private val plan = List(3) { SetPrescription(RepRange(4, 6), 1, 180) }
    private val set = LoggedSet(100.0, 6, 0, 1)
    private val calls = mutableListOf<String>()
    private val felt = mutableListOf<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { felt += hapticFeedbackType }
    }

    private fun session(sets: Int, records: List<Record?> = List(sets) { null }, top: LoggedSet = set): ActiveSession {
        val exercise = SessionExercise(
            id = 1, exercise = bench, sets = List(sets) { top }, lastTime = emptyList(), ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = plan, plates = DefaultPlates, records = records,
        )
        val entry = SetEntry("100", "6", "", 1, Field.Weight, partialMode = false, fresh = true)
        return ActiveSession(id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = entry, padVisible = true, restEndsAt = null, restTotalMs = 0)
    }

    private var shown by mutableStateOf<ActiveSession?>(null)

    private fun show(first: ActiveSession) {
        shown = first
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                HyperlogTheme { SessionScreen(shown!!, listOf(bench), Recording(calls), onLeave = {}) }
            }
        }
    }

    private fun confirms() = felt.count { it == HapticFeedbackType.Confirm }

    @Test
    fun loggingASetIsFelt() {
        show(session(0))
        compose.runOnIdle { shown = session(1) }
        compose.waitForIdle()
        assertEquals(1, confirms())
    }

    /** Opening a session already under way logs nothing. */
    @Test
    fun openingASessionIsNotFelt() {
        show(session(2))
        compose.waitForIdle()
        assertEquals(0, confirms())
    }

    @Test
    fun takingASetBackIsNotFelt() {
        show(session(2))
        compose.runOnIdle { shown = session(1) }
        compose.waitForIdle()
        assertEquals(0, confirms())
    }

    @Test
    fun aRecordHasItsMomentAndItPasses() {
        show(session(0))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { shown = session(1, listOf(Record.Estimate)) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("New record").assertExists()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("New record").assertDoesNotExist()
    }

    /** A record says which set made it, and is felt apart from an ordinary set. */
    @Test
    fun aRecordSaysWhatItWasAndIsFelt() {
        show(session(0))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { shown = session(1, listOf(Record.Estimate)) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Estimate record · 100 × 6").assertExists()
        assertEquals(1, felt.count { it == HapticFeedbackType.LongPress })
    }

    /** Review: a drop, mini-set or hold is logged too. */
    @Test
    fun loggingADropIsFelt() {
        show(session(1))
        compose.runOnIdle { shown = session(1, top = set.copy(extensions = listOf(Extension(Extension.Kind.Drop, 80.0, 6)))) }
        compose.waitForIdle()
        assertEquals(1, confirms())
    }

    /** Review: taking the set back cancelled the timer that clears the pill, and it stayed. */
    @Test
    fun aRecordTakenBackTakesItsMomentWithIt() {
        show(session(0))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { shown = session(1, listOf(Record.Estimate)) }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { shown = session(0) }
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("New record").assertDoesNotExist()
    }

    /** Review: a correction that makes a record logs nothing new, so it has no moment. */
    @Test
    fun aCorrectionHasNoMoment() {
        show(session(1))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { shown = session(1, listOf(Record.Estimate)) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("New record").assertDoesNotExist()
    }

    /** Review: the record shown is the set just logged, wherever it sits. */
    @Test
    fun aRecordOutOfOrderShowsTheSetThatMadeIt() {
        val heavy = LoggedSet(120.0, 3, 0, 1)
        val exercise = session(0).exercises.single().copy(sets = listOf(heavy, set), records = listOf(Record.Estimate, Record.Reps), setIndices = listOf(0, 2))
        show(session(0).copy(exercises = listOf(exercise.copy(sets = listOf(set), records = listOf(Record.Reps), setIndices = listOf(2)))))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { shown = session(0).copy(exercises = listOf(exercise), latestSet = 1L to 0) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Estimate record · 120 × 3").assertExists()
    }

    @Test
    fun aSetWithNoRecordHasNoMoment() {
        show(session(0))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { shown = session(1) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("New record").assertDoesNotExist()
    }

    /** The moment lays nothing over the pad: the next set goes straight in, with it on screen. */
    @Test
    fun theMomentNeverHoldsUpTheNextSet() {
        show(session(0))
        compose.runOnIdle { shown = session(1, listOf(Record.Estimate)) }
        compose.onNodeWithText("New record").assertExists()
        compose.onNodeWithContentDescription("Log set 2").performClick()
        assertEquals("logShown", calls.last())
    }

    @Test
    fun withAnimationsOffAMomentJustAppears() {
        assertEquals(EnterTransition.None, momentEnter(removed = true))
        assertEquals(ExitTransition.None, momentExit(removed = true))
        assertNotEquals(EnterTransition.None, momentEnter(removed = false))
    }
}
