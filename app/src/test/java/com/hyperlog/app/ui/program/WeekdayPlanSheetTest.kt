package com.hyperlog.app.ui.program

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.ui.theme.HyperlogTheme
import com.hyperlog.app.week.WeekdayPlan
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
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

    private val long = "Linnanmaan liikuntakeskuksen kuntosali"

    @Test
    fun aLongGymNameLeavesTheShortDayNameOnOneLineAndTheRowAsTallAsAShortOne() {
        val plan = WeekdayPlan(mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.SATURDAY to 2L))
        val gyms = listOf(Gym(1, "Home", emptyList()), Gym(2, long, emptyList()))
        compose.setContent { HyperlogTheme { WeekdayPlanSheetContent(plan, gyms, {}, {}) } }

        val sat = compose.onNodeWithText("Sat", useUnmergedTree = true)
        sat.assertExists()
        val mon = compose.onNodeWithText("Mon", useUnmergedTree = true)
        assertEquals(mon.getUnclippedBoundsInRoot().height, sat.getUnclippedBoundsInRoot().height)
        val satRow = compose.onNodeWithContentDescription("Gym on Saturday: $long").getUnclippedBoundsInRoot().height
        val monRow = compose.onNodeWithContentDescription("Gym on Monday: Home").getUnclippedBoundsInRoot().height
        assertEquals(monRow, satRow)
    }
}
