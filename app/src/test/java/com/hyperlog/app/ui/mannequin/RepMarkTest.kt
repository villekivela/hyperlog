package com.hyperlog.app.ui.mannequin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec.Companion.DefaultPlates
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.history.ExerciseTrend
import com.hyperlog.app.history.TrendLine
import com.hyperlog.app.history.TrendPoint
import com.hyperlog.app.history.WeekSets
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.history.HistoryScreen
import com.hyperlog.app.ui.library.LibraryRow
import com.hyperlog.app.ui.session.ExercisePicker
import com.hyperlog.app.ui.session.Recording
import com.hyperlog.app.ui.session.SessionList
import com.hyperlog.app.ui.session.SessionScreen
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.today.SlotRows
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rep mark, Hyperlog's signature: on Today, the session, Progress and History (#271). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class RepMarkTest {
    @get:Rule
    val compose = createComposeRule()

    private val hack = Exercise("hack_squat", "Hack Squat", Equipment.Machine, MovementPattern.Squat, GripDemand.None, listOf("Quads"), note = "Knees track the toes.")
    private val unposed = Exercise("my_squat", "My Squat", Equipment.Machine, MovementPattern.Squat, GripDemand.None, listOf("Quads"))
    private val library = listOf(hack, unposed).associateBy { it.id }
    private val today = LocalDate.of(2026, 11, 5)

    private fun slot(id: String) = PlannedSlot("s", "1", 0, id, emptyList(), null, listOf(SetPrescription(RepRange(8, 10), 1, 90)))

    private fun showSession(sets: Int) {
        val exercise = SessionExercise(
            id = 1, exercise = hack, sets = emptyList(), lastTime = emptyList(), ladder = DefaultPlates.ladder(), ladderKnown = true,
            prescription = List(sets) { SetPrescription(RepRange(8, 10), 1, 90) }, plates = DefaultPlates,
        )
        val entry = SetEntry("100", "6", "", 1, Field.Weight, partialMode = false, fresh = true)
        val session = ActiveSession(id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = entry, padVisible = true, restEndsAt = null, restTotalMs = 0)
        compose.setContent { HyperlogTheme { CompositionLocalProvider(LocalExercises provides library) { SessionScreen(session, listOf(hack), Recording(mutableListOf()), onLeave = {}) } } }
    }

    @Test
    fun anExerciseOnTodayWearsItsMark() {
        compose.setContent { HyperlogTheme { CompositionLocalProvider(LocalExercises provides library) { SlotRows(listOf(slot("hack_squat")), mapOf("hack_squat" to "Hack Squat")) } } }
        compose.onNodeWithTag(RepMarkTag, useUnmergedTree = true).assertExists()
    }

    /** The library is the main place exercises are listed (round 3). */
    @Test
    fun anExerciseInTheLibraryWearsItsMark() {
        compose.setContent { HyperlogTheme { LibraryRow(hack) } }
        compose.onNodeWithTag(RepMarkTag, useUnmergedTree = true).assertExists()
    }

    @Test
    fun anExerciseNotYetPosedWearsTheFallbackInTheLibrary() {
        compose.setContent { HyperlogTheme { LibraryRow(unposed) } }
        compose.onNodeWithTag(RepMarkFallbackTag, useUnmergedTree = true).assertExists()
    }

    @Test
    fun theAddAndSwapPickersShowEachExercisesMark() {
        compose.setContent { HyperlogTheme { ExercisePicker(listOf(hack), title = "Add", onPick = {}) } }
        compose.onNodeWithTag(RepMarkTag, useUnmergedTree = true).assertExists()
    }

    @Test
    fun theSessionSheetShowsEachExercisesMark() {
        val exercise = SessionExercise(1, hack, emptyList(), emptyList(), DefaultPlates.ladder(), true)
        val session = ActiveSession(id = 1, startedAt = 0, exercises = listOf(exercise), currentId = 1, entry = null, padVisible = true, restEndsAt = null, restTotalMs = 0)
        compose.setContent { HyperlogTheme { SessionList(session, onStart = {}, onAdd = {}, onFinish = {}) } }
        compose.onNodeWithTag(RepMarkTag, useUnmergedTree = true).assertExists()
    }

    @Test
    fun anExerciseNotYetPosedWearsTheDesignedFallback() {
        compose.setContent { HyperlogTheme { CompositionLocalProvider(LocalExercises provides library) { SlotRows(listOf(slot("my_squat")), mapOf("my_squat" to "My Squat")) } } }
        compose.onNodeWithTag(RepMarkFallbackTag, useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag(RepMarkTag, useUnmergedTree = true).assertCountEquals(0)
    }

    /** The band a short exercise leaves above its table holds the rep and its cue (round 1, item 4). */
    @Test
    fun aShortExerciseFillsTheBandWithItsRepAndCue() {
        showSession(sets = 1)
        val stage = compose.onNodeWithTag(RepStageTag, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("The stage is ${stage.bottom - stage.top} tall", stage.bottom - stage.top >= 96.dp)
        compose.onNodeWithText("Knees track the toes.").assertExists()
    }

    /** Four sets leave a band too short for the rep to read. */
    @Test
    fun aBandTooShortToReadStaysEmpty() {
        showSession(sets = 4)
        compose.onAllNodesWithTag(RepStageTag, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aTapOnTheStageOpensTheExercise() {
        showSession(sets = 1)
        compose.onNodeWithTag(RepStageTag, useUnmergedTree = true).performClick()
        // The stage's cue, and the sheet's.
        compose.onAllNodesWithText("Knees track the toes.").assertCountEquals(2)
    }

    @Test
    fun aMovingLiftAndAnExerciseOnProgressWearTheirMarks() {
        val points = listOf(TrendPoint(1, today.minusDays(14), LoggedSet(80.0, 6, 0, 1), 100.0), TrendPoint(2, today.minusDays(2), LoggedSet(85.0, 6, 0, 1), 105.0))
        val trend = ExerciseTrend(hack, listOf(TrendLine(null, null, points)))
        compose.setContent {
            HyperlogTheme {
                CompositionLocalProvider(LocalExercises provides library) {
                    HistoryScreen(
                        emptyList(), listOf(WeekSets(LocalDate.of(2026, 11, 2), mapOf("Quads" to 9.0), 9)), listOf(trend), emptyList(), tab = 1, onTab = {}, onOpenSession = {},
                        onOpenExercise = {}, onBodyweight = {}, onLogBodyweight = {}, onBack = {}, today = today,
                    )
                }
            }
        }
        assertTrue(compose.onAllNodesWithTag(RepMarkTag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
