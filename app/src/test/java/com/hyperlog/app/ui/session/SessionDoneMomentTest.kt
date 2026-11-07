package com.hyperlog.app.ui.session

import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.progression.Record
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.LiftChange
import com.hyperlog.app.session.SessionSummary
import com.hyperlog.app.session.WeekDone
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The end of a session and of a week (#272): worth seeing, and never in the way. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class SessionDoneMomentTest {
    @get:Rule
    val compose = createComposeRule()

    private val set = LoggedSet(100.0, 6, 0, 1)

    private fun lift(name: String, change: LiftChange?, sets: Int = 2, records: List<Record?> = emptyList()) =
        ExerciseSummary(name, List(sets) { set }, records, exerciseId = name, change = change)

    private val session = listOf(
        lift("hack_squat", LiftChange.Up, records = listOf(Record.Estimate, null)),
        lift("barbell_rdl", LiftChange.Up),
        lift("leg_press", LiftChange.Same),
        lift("pec_deck", null),
    )

    private var closed = 0

    private fun show(exercises: List<ExerciseSummary> = session, week: WeekDone? = null) = compose.setContent {
        HyperlogTheme { SessionDoneScreen(SessionSummary(1, 0, 3_600_000, exercises, week), onResume = {}, onClose = { closed++ }) }
    }

    @After
    fun animationsBackOn() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun aSessionSaysWhatItCameTo() {
        show()
        compose.onNodeWithText("8 sets").assertIsDisplayed()
        compose.onNodeWithText("2 of 3 lifts up").assertIsDisplayed()
        compose.onNodeWithText("1 record").assertIsDisplayed()
    }

    @Test
    fun aSessionIsRatedInAWord() {
        show(listOf(ExerciseSummary("hack_squat", List(2) { set }, exerciseId = "hack_squat", change = LiftChange.Up, lastTop = LoggedSet(95.0, 6, 0, 1), planned = 2, aimed = 2, onEffort = 2)))
        compose.onNodeWithText("Strong session").assertIsDisplayed()
    }

    @Test
    fun theMeasuresBehindTheWordAreOnScreen() {
        show(listOf(ExerciseSummary("hack_squat", List(2) { set }, exerciseId = "hack_squat", planned = 3, aimed = 2, onEffort = 1)))
        compose.onNodeWithText("2 of 3 sets").assertIsDisplayed()
        compose.onNodeWithText("1 of 2 on effort").assertIsDisplayed()
    }

    @Test
    fun theBestLiftIsCalledOut() {
        val best = ExerciseSummary("Hack Squat", listOf(LoggedSet(105.0, 6, 0, 1)), exerciseId = "hack_squat", change = LiftChange.Up, lastTop = LoggedSet(100.0, 6, 0, 1))
        show(listOf(best))
        compose.onNodeWithText("Best lift: Hack Squat, 100\u00A0×\u00A06 → 105\u00A0×\u00A06").assertIsDisplayed()
    }

    /** The rating, stats and best lift stand under the shaped check, with a lift to name or not. */
    @Test
    fun theShapedCheckLeadsTheEnd() {
        show()
        compose.onNodeWithTag(DoneCheckTag, useUnmergedTree = true).assertExists()
    }

    @Test
    fun aFinishedSessionIsFeltAndAFinishedWeekMoreSo() {
        val felt = mutableListOf<HapticFeedbackType>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { felt += hapticFeedbackType }
        }
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                HyperlogTheme { SessionDoneScreen(SessionSummary(1, 0, 3_600_000, session, WeekDone(8, 4, 42)), onResume = {}, onClose = {}) }
            }
        }
        compose.waitForIdle()
        assertEquals(listOf(HapticFeedbackType.Confirm, HapticFeedbackType.LongPress), felt)
    }

    /** Review: coming back from an exercise's table isn't finishing again. */
    @Test
    fun comingBackToSessionDoneIsNotFelt() {
        val felt = mutableListOf<HapticFeedbackType>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { felt += hapticFeedbackType }
        }
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                HyperlogTheme { SessionDoneScreen(SessionSummary(1, 0, 3_600_000, session), firstShown = false, onResume = {}, onClose = {}) }
            }
        }
        compose.waitForIdle()
        assertEquals(emptyList<HapticFeedbackType>(), felt)
    }

    @Test
    fun aFirstSessionHasNothingToCompareSoSaysNothing() {
        show(listOf(lift("pec_deck", null, sets = 1)))
        compose.onNodeWithText("1 set").assertIsDisplayed()
        compose.onNodeWithText("lifts up", substring = true).assertDoesNotExist()
        compose.onNodeWithText("record", substring = true).assertDoesNotExist()
    }

    @Test
    fun aFinishedWeekHasItsMoment() {
        show(week = WeekDone(week = 8, days = 4, workSets = 42))
        compose.onNodeWithText("Week 8 done").assertIsDisplayed()
        compose.onNodeWithText("4 days · 42 sets").assertIsDisplayed()
    }

    @Test
    fun aFinishedWeekComparesWithTheLastAndCountsTheRun() {
        show(week = WeekDone(week = 8, days = 4, workSets = 42, lastWeekSets = 39, streak = 3))
        compose.onNodeWithText("3 more than last week").assertIsDisplayed()
        compose.onNodeWithText("3 weeks in a row").assertIsDisplayed()
    }

    @Test
    fun aLighterWeekSaysSoPlainly() {
        show(week = WeekDone(week = 8, days = 4, workSets = 30, lastWeekSets = 31, streak = 1))
        compose.onNodeWithText("1 fewer than last week").assertIsDisplayed()
        compose.onNodeWithText("in a row", substring = true).assertDoesNotExist()
    }

    @Test
    fun aWeeksDaysAreCounted() {
        show(week = WeekDone(week = 8, days = 4, workSets = 42))
        assertEquals(4, compose.onAllNodesWithTag(WeekDotTag, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aSessionMidWeekHasNoWeekMoment() {
        show()
        compose.onNodeWithText("done", substring = true, ignoreCase = false).assertExists()
        compose.onNodeWithText("Week", substring = true).assertDoesNotExist()
    }

    /** The marks are still coming in, and Done already works. */
    @Test
    fun theMomentNeverHoldsUpTheEndOfASession() {
        compose.mainClock.autoAdvance = false
        show()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Done").performClick()
        assertEquals(1, closed)
    }
}
