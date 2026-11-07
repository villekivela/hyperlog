package com.hyperlog.app.ui.session

import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
    fun aFirstSessionHasNothingToCompareSoSaysNothing() {
        show(listOf(lift("pec_deck", null, sets = 1)))
        compose.onNodeWithText("1 set").assertIsDisplayed()
        compose.onNodeWithText("lifts up", substring = true).assertDoesNotExist()
        compose.onNodeWithText("record", substring = true).assertDoesNotExist()
    }

    @Test
    fun theSessionsRepsComeTogether() {
        show()
        assertEquals(4, compose.onAllNodesWithTag(DoneMarkTag, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aFinishedWeekHasItsMoment() {
        show(week = WeekDone(week = 8, days = 4, workSets = 42))
        compose.onNodeWithText("Week 8 done").assertIsDisplayed()
        compose.onNodeWithText("4 days · 42 sets").assertIsDisplayed()
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

    @Test
    fun withAnimationsOffTheMarksAreAllThereAtOnce() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertEquals(4, doneMarksAtFirst(4, removed = true))
        assertEquals(0, doneMarksAtFirst(4, removed = false))
    }
}
