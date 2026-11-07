package com.hyperlog.app.ui.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hyperlog.app.coach.NumberRange
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
    private val muscles = listOf("Chest", "Lats", "Upper Back", "Front Delts", "Side Delts", "Rear Delts", "Biceps", "Triceps Long Head", "Triceps Lateral Head", "Quads", "Hamstrings", "Glutes", "Calves")

    private fun block() = (0 until 8).map { w ->
        WeekSets(monday.minusWeeks(w.toLong()), muscles.mapIndexed { i, m -> m to (4 + (i + w) % 11).toDouble() }.toMap(), 40 + w)
    }

    private fun lift(name: String, from: Double, to: Double) = ExerciseTrend(
        Exercise(name, name, Equipment.Barbell, MovementPattern.entries.first(), GripDemand.None, listOf("Chest")),
        listOf(TrendLine(null, null, listOf(TrendPoint(1, today.minusDays(14), LoggedSet(80.0, 6, 0, 1), from), TrendPoint(2, today.minusDays(2), LoggedSet(85.0, 6, 0, 1), to)))),
    )

    private val opened = mutableListOf<String>()
    private var nexts = 0

    private fun show(
        weeks: List<WeekSets>, trends: List<ExerciseTrend>, next: NextAction? = NextAction("Day A", "Start") { nexts++ },
        range: (String) -> NumberRange = { NumberRange(8, 14) },
    ) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1.15f)) {
            HyperlogTheme {
                HistoryScreen(
                    emptyList(), weeks, trends, emptyList(), tab = 1, onTab = {}, onOpenSession = {}, onOpenExercise = { opened += it },
                    onBodyweight = {}, onLogBodyweight = {}, onBack = {}, today = today, next = next, range = range,
                )
            }
        }
    }

    @Test
    fun aFullBlockSharesOneScreen() {
        show(block(), listOf(lift("Bench", 100.0, 105.0), lift("Squat", 140.0, 150.0), lift("Row", 90.0, 85.0)))
        compose.onNodeWithText("40 sets, week of 2 Nov").assertIsDisplayed()
        compose.onNodeWithText("Moving").assertIsDisplayed()
        // Third by how far it moved; the Exercises list further down has its own Bench.
        val last = compose.onNodeWithContentDescription("Bench, estimate 100 to 105 kg, +5%", useUnmergedTree = true).getBoundsInRoot()
        // On the phone the status bar takes 40 dp and the navigation bar the last 80.
        assertTrue("The last lift ends at ${last.bottom}", last.bottom <= 794.dp)
    }

    /** Round 2: one set reads as one, written and spoken. */
    @Test
    fun oneSetIsSingular() {
        show(listOf(WeekSets(monday, mapOf("Lats" to 1.0), 1)), emptyList())
        compose.onNodeWithText("1 set, week of 2 Nov").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lats, 1 set, target 8 to 14, under target", useUnmergedTree = true).assertExists()
    }

    /** One scale for the rows: zero, the target's ends, and the target in words (round 2). */
    @Test
    fun theMusclesHaveALabelledScale() {
        show(listOf(WeekSets(monday, mapOf("Lats" to 2.0), 2)), emptyList())
        compose.onNodeWithText("Target 8–14 sets").assertIsDisplayed()
        listOf("0", "8", "14").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    /** A focus gives muscles different ranges, so no one target can head the scale. */
    @Test
    fun aScaleOverDifferentRangesStaysFromZeroToTheTop() {
        show(listOf(WeekSets(monday, mapOf("Lats" to 2.0, "Chest" to 3.0), 5)), emptyList(), range = { if (it == "Lats") NumberRange(14, 21) else NumberRange(4, 7) })
        compose.onNodeWithText("Target", substring = true).assertDoesNotExist()
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithText("21").assertIsDisplayed()
    }

    @Test
    fun movingSaysWhatItMeasuresAndOverWhen() {
        show(block(), listOf(lift("Bench", 100.0, 105.0)))
        compose.onNodeWithText("Estimate, last 4 weeks").assertIsDisplayed()
    }

    @Test
    fun theFoldLooksLikeItOpens() {
        show(listOf(WeekSets(monday, mapOf("Chest" to 9.0, "Lats" to 2.0, "Quads" to 3.0, "Glutes" to 4.0, "Calves" to 5.0), 20)), emptyList())
        compose.onNodeWithTag(MusclesFoldTag, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("All 5 muscles").performClick()
        compose.onNodeWithText("Fewer muscles").assertIsDisplayed()
    }

    /** The untrained are one line of names, not rows of zeros. */
    @Test
    fun aMuscleWithNoSetsIsNamedNotDrawn() {
        val week = WeekSets(monday, mapOf("Chest" to 9.0), 9)
        val earlier = WeekSets(monday.minusWeeks(1), mapOf("Lats" to 9.0, "Quads" to 9.0), 18)
        show(listOf(week, earlier), emptyList())
        compose.onNodeWithText("No sets yet: Lats · Quads").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lats, 0 sets, target 8 to 14, under target", useUnmergedTree = true).assertDoesNotExist()
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
        compose.onNodeWithText("20 sets, week of 2 Nov").assertIsDisplayed()
        compose.onNodeWithContentDescription("Work sets a week over the last 2 weeks, from 20 to 30, 20 in the latest")
            .performTouchInput { click(Offset(8f, centerY)) }
        compose.onNodeWithText("30 sets, week of 26 Oct").assertIsDisplayed()
        compose.onNodeWithText("Sets last week").assertIsDisplayed()
        compose.onNodeWithContentDescription("Chest, 3 sets, target 8 to 14, under target", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Later week").performClick()
        compose.onNodeWithText("20 sets, week of 2 Nov").assertIsDisplayed()
    }

    @Test
    fun oneSessionStillReads() {
        show(listOf(WeekSets(monday, mapOf("Chest" to 3.0), 3)) + (1 until 8).map { WeekSets(monday.minusWeeks(it.toLong()), emptyMap()) }, listOf(lift("Bench", 100.0, 100.0)))
        compose.onNodeWithText("3 sets, week of 2 Nov").assertIsDisplayed()
        compose.onNodeWithText("Lifts that are moving show here after a few sessions.").assertIsDisplayed()
    }

    @Test
    fun noDataSaysWhatToAimFor() {
        show((0 until 8).map { WeekSets(monday.minusWeeks(it.toLong()), emptyMap()) }, emptyList(), next = null)
        compose.onNodeWithText("No sets in the last 8 weeks. Aim for 8–14 sets a muscle each week.").assertIsDisplayed()
        compose.onNodeWithText("Lifts that are moving show here after a few sessions.").assertIsDisplayed()
        compose.onNodeWithText("sets, week of", substring = true).assertDoesNotExist()
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

    /** Short names stay on one line, or a block of real muscles pushes Moving off the screen. */
    @Test
    fun aLongMuscleNameKeepsToOneLine() {
        show(listOf(WeekSets(monday, mapOf("Triceps Lateral Head" to 2.0), 2)), emptyList())
        val row = compose.onNodeWithContentDescription("Triceps Lateral Head, 2 sets, target 8 to 14, under target", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("The row is ${row.bottom - row.top} tall", row.bottom - row.top <= 32.dp)
    }

    /** Round 2: Go to Today led to a day with no Start. A day still to come names its date and offers nothing. */
    @Test
    fun aDayStillToComeHasItsDateAndNoButton() {
        show(block(), emptyList(), next = NextAction("Week 8 · Day A", date = "Mon 9 Nov"))
        compose.onNodeWithText("Next · Mon 9 Nov").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Week 8 · Day A")).assertDoesNotExist()
        compose.onNodeWithText("Start").assertDoesNotExist()
    }

    @Test
    fun theNextActionLeadsProgress() {
        show(block(), emptyList())
        compose.onNodeWithText("Start").performClick()
        assertEquals(1, nexts)
    }
}
