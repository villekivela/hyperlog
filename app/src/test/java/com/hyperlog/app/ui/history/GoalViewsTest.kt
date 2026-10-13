package com.hyperlog.app.ui.history

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.hyperlog.app.history.LiftGoal
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A goal's status line (SPEC, Lift goals): the date it is aimed at goes once the goal is reached. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class GoalViewsTest {
    @get:Rule
    val compose = createComposeRule()

    private val goal = LiftGoal("barbell_bench_press", 100.0, LocalDate.of(2026, 12, 31))

    @Test
    fun onTheWayTheDateFollowsWhatIsLeft() {
        compose.setContent { GoalStatusRow(95.0, goal) }
        compose.onNodeWithText("5 kg to go · by Thu 31 Dec").assertExists()
    }

    @Test
    fun aReachedGoalDropsItsDate() {
        compose.setContent { GoalStatusRow(100.5, goal) }
        compose.onNodeWithText("Goal reached").assertExists()
    }
}
