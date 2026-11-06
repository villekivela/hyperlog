package com.hyperlog.app.ui.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.history.ExerciseTrend
import com.hyperlog.app.history.TrendLine
import com.hyperlog.app.history.TrendPoint
import com.hyperlog.app.history.WeekSets
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.ui.theme.NextAction
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Progress at a glance (#270): it holds with a full block, one session and no data. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgressGlanceTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 5)
    private val monday = LocalDate.of(2026, 11, 2)
    private val muscles = listOf("Chest", "Lats", "Upper back", "Front delts", "Side delts", "Rear delts", "Biceps", "Triceps", "Quads", "Hamstrings", "Glutes", "Calves", "Abs")

    private fun block() = (0 until 8).map { w ->
        WeekSets(monday.minusWeeks(w.toLong()), muscles.mapIndexed { i, m -> m to (4 + (i + w) % 11).toDouble() }.toMap(), 40 + w)
    }

    private fun lift(name: String, from: Double, to: Double) = ExerciseTrend(
        Exercise(name, name, Equipment.Barbell, MovementPattern.entries.first(), GripDemand.None, listOf("Chest")),
        listOf(TrendLine(null, null, listOf(TrendPoint(1, today.minusDays(14), LoggedSet(80.0, 6, 0, 1), from), TrendPoint(2, today.minusDays(2), LoggedSet(85.0, 6, 0, 1), to)))),
    )

    private val opened = mutableListOf<String>()
    private var nexts = 0

    private fun show(weeks: List<WeekSets>, trends: List<ExerciseTrend>, next: NextAction? = NextAction("Start", "Day A") { nexts++ }) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.15f)) {
            HyperlogTheme {
                HistoryScreen(
                    emptyList(), weeks, trends, emptyList(), tab = 1, onTab = {}, onOpenSession = {}, onOpenExercise = { opened += it },
                    onBodyweight = {}, onLogBodyweight = {}, onBack = {}, today = today, next = next,
                )
            }
        }
    }

    @Test
    fun aFullBlockSharesOneScreen() {
        show(block(), listOf(lift("Bench", 100.0, 105.0), lift("Squat", 140.0, 150.0), lift("Row", 90.0, 85.0)))
        compose.onNodeWithText("Weekly sets").assertIsDisplayed()
        compose.onNodeWithText("Moving").assertIsDisplayed()
        // Third by how far it moved; the Exercises list further down has its own Bench.
        val last = compose.onNodeWithContentDescription("Bench, estimate 100 to 105 kg, +5%", useUnmergedTree = true).getBoundsInRoot()
        // The navigation bar takes the last 80 dp on the phone.
        assertTrue("The last lift ends at ${last.bottom}", last.bottom <= 834.dp)
    }

    @Test
    fun theMusclesFurthestShortShowFirstAndTheRestOnATap() {
        show(listOf(WeekSets(monday, mapOf("Chest" to 9.0, "Lats" to 2.0, "Quads" to 3.0, "Glutes" to 4.0, "Calves" to 5.0, "Abs" to 6.0, "Biceps" to 7.0), 20)), emptyList())
        compose.onNodeWithText("1 of 7 on target").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lats, 2 sets, target 8 to 14, under target", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Chest, 9 sets, target 8 to 14, on target", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("All 7 muscles").performClick()
        compose.onNodeWithContentDescription("Chest, 9 sets, target 8 to 14, on target", useUnmergedTree = true).assertExists()
    }

    /** A tap on the chart reads that week, and its muscles follow; the chevrons do the same for TalkBack. */
    @Test
    fun aWeekReadOnTheChartShowsItsMuscles() {
        val weeks = listOf(WeekSets(monday, mapOf("Chest" to 9.0), 20), WeekSets(monday.minusWeeks(1), mapOf("Chest" to 3.0), 30))
        show(weeks, emptyList())
        compose.onNodeWithText("20 · 2 Nov").assertIsDisplayed()
        compose.onNodeWithContentDescription("Work sets a week over the last 2 weeks, from 20 to 30, 20 in the latest")
            .performTouchInput { click(Offset(8f, centerY)) }
        compose.onNodeWithText("30 · 26 Oct").assertIsDisplayed()
        compose.onNodeWithText("Sets last week").assertIsDisplayed()
        compose.onNodeWithContentDescription("Chest, 3 sets, target 8 to 14, under target", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Later week").performClick()
        compose.onNodeWithText("20 · 2 Nov").assertIsDisplayed()
    }

    @Test
    fun oneSessionStillReads() {
        show(listOf(WeekSets(monday, mapOf("Chest" to 3.0), 3)) + (1 until 8).map { WeekSets(monday.minusWeeks(it.toLong()), emptyMap()) }, listOf(lift("Bench", 100.0, 100.0)))
        compose.onNodeWithText("3 · 2 Nov").assertIsDisplayed()
        compose.onNodeWithText("Lifts that are moving show here after a few sessions.").assertIsDisplayed()
    }

    @Test
    fun noDataSaysWhatToAimFor() {
        show((0 until 8).map { WeekSets(monday.minusWeeks(it.toLong()), emptyMap()) }, emptyList(), next = null)
        compose.onNodeWithText("No sets in the last 8 weeks. Aim for 8–14 sets a muscle each week.").assertIsDisplayed()
        compose.onNodeWithText("Lifts that are moving show here after a few sessions.").assertIsDisplayed()
        compose.onNodeWithText("Weekly sets").assertDoesNotExist()
    }

    /** An empty week still leads with something: the last week trained, named as such. */
    @Test
    fun aWeekWithNoSetsShowsTheLastWeekTrained() {
        show(listOf(WeekSets(monday, emptyMap()), WeekSets(monday.minusWeeks(1), mapOf("Chest" to 9.0), 12)), emptyList())
        compose.onNodeWithText("Sets last week").assertIsDisplayed()
        compose.onNodeWithText("1 of 1 on target").assertIsDisplayed()
    }

    @Test
    fun aMovingLiftOpensItsTrend() {
        show(block(), listOf(lift("Bench", 100.0, 105.0)))
        compose.onNodeWithContentDescription("Bench, estimate 100 to 105 kg, +5%", useUnmergedTree = true).assertExists().performClick()
        assertEquals(listOf("Bench"), opened)
    }

    @Test
    fun theNextActionLeadsProgress() {
        show(block(), emptyList())
        compose.onNodeWithText("Start").performClick()
        assertEquals(1, nexts)
    }
}
