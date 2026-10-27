package com.hyperlog.app.ui.program

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.week.WeekdayPlan
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Training days sheet's gym rows with a long gym name (SPEC, Training days). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class WeekdayPlanSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val longGymName = "Linnanmaan liikuntakeskuksen kuntosali"

    @Test
    fun aLongGymNameLeavesTheShortDayNameOnOneLineAndTheRowAsTallAsAShortOne() {
        val plan = WeekdayPlan(mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.SATURDAY to 2L))
        val gyms = listOf(Gym(1, "Home", emptyList()), Gym(2, longGymName, emptyList()))
        compose.setContent { HyperlogTheme { WeekdayPlanSheetContent(plan, gyms, {}, {}) } }

        val sat = compose.onNodeWithText("Sat", useUnmergedTree = true)
        val mon = compose.onNodeWithText("Mon", useUnmergedTree = true)
        assertEquals(mon.getUnclippedBoundsInRoot().height, sat.getUnclippedBoundsInRoot().height)
        val satRow = compose.onNodeWithContentDescription("Gym on Saturday: $longGymName").getUnclippedBoundsInRoot().height
        val monRow = compose.onNodeWithContentDescription("Gym on Monday: Home").getUnclippedBoundsInRoot().height
        assertEquals(monRow, satRow)

        val button = compose.onNodeWithContentDescription("Gym on Saturday: $longGymName").getUnclippedBoundsInRoot()
        assertTrue("the gym button stays inside 360 dp", button.right <= 360.dp)

        val gymText = compose.onNodeWithText(longGymName, useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        gymText.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        assertEquals(1, results.single().lineCount)
        assertTrue(results.single().hasVisualOverflow)
    }

    @Test
    fun everyDayThatIsOnHasAMinutesMenuFromNoLimitTo120InFives() {
        var plan = WeekdayPlan(mapOf(DayOfWeek.MONDAY to null, DayOfWeek.THURSDAY to null))
        compose.setContent { HyperlogTheme { WeekdayPlanSheetContent(plan, listOf(Gym(1, "Home", emptyList())), { plan = it }, {}) } }
        // One gym: the row holds only the day and its minutes, none set yet.
        compose.onAllNodesWithText("No limit").assertCountEquals(2)
        compose.onNodeWithContentDescription("Minutes on Monday: no limit").performClick()
        compose.onNodeWithText("120 min").assertExists()
        // The menu offers no limit first, by name.
        compose.onAllNodesWithText("No limit").assertCountEquals(3)
        compose.onNodeWithText("60 min").performClick()
        assertEquals(mapOf(DayOfWeek.MONDAY to 60), plan.minutes)
        assertEquals(mapOf(DayOfWeek.MONDAY to null, DayOfWeek.THURSDAY to null), plan.gyms)
    }
}
