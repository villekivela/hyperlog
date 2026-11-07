package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.history.GoalProgress
import com.hyperlog.app.history.LiftGoal
import com.hyperlog.app.history.LiftTrend
import com.hyperlog.app.history.TrendPoint
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.ui.theme.HyperlogTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The exercise sheet holds the lift trend: the e1RM, the trend in words and its chart (owner, #269). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class LiftTrendSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))
    private val points = listOf(30L, 16L, 2L).mapIndexed { i, ago -> TrendPoint(i + 1L, LocalDate.now().minusDays(ago), LoggedSet(100.0, 5, 0, 1), 101.0 + i * 2) }

    private fun show(trend: LiftTrend?, goal: GoalProgress? = null) = compose.setContent {
        HyperlogTheme { ExerciseSheet(bench, machine = null, onChangeMachine = null, goal = goal, trend = trend?.let { it to points }) }
    }

    @Test
    fun itShowsTheE1rmTheTrendAndItsChart() {
        show(LiftTrend.Moved(101.0, 105.0))
        compose.onNodeWithText("e1RM 105 kg").assertExists()
        compose.onNodeWithText("e1RM up 4% in 4 weeks, to 105 kg").assertExists()
        compose.onAllNodesWithContentDescription("Barbell Bench Press's e1RM, session by session").assertCountEquals(1)
    }

    @Test
    fun aLiftThatHasntMovedSaysSo() {
        show(LiftTrend.Stalled(3))
        compose.onNodeWithText("Hasn't moved in 3 sessions").assertExists()
    }

    @Test
    fun withNothingToSayThereIsNone() {
        show(null)
        compose.onAllNodesWithText("e1RM", substring = true).assertCountEquals(0)
    }

    /** With a goal, the trend joins its block: one chart, the goal's. */
    @Test
    fun withAGoalItJoinsTheGoalsBlock() {
        show(LiftTrend.Moved(101.0, 105.0), GoalProgress(LiftGoal("barbell_bench_press", 120.0, null), points))
        compose.onNodeWithText("e1RM up 4% in 4 weeks, to 105 kg").assertExists()
        compose.onAllNodesWithContentDescription("Barbell Bench Press's e1RM, session by session").assertCountEquals(0)
    }
}
