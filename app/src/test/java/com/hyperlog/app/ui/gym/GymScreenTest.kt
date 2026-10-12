package com.hyperlog.app.ui.gym

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.ui.theme.HyperlogTheme
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class GymScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theUsualDaysToggleOneWeekdayAtATime() {
        var days: Set<DayOfWeek>? = null
        compose.setContent {
            HyperlogTheme {
                GymScreen(
                    Gym(1, "Kallio", emptyList(), setOf(DayOfWeek.MONDAY)),
                    onRename = {}, onDelete = {}, onWeekdays = { days = it }, onOpenItem = {}, onAddItem = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Starting a session on these days picks Kallio.").assertExists()
        compose.onNodeWithContentDescription("Monday").assertIsOn()
        compose.onNodeWithContentDescription("Wednesday").assertIsOff().performClick()
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), days)
        compose.onNodeWithContentDescription("Monday").performClick()
        assertEquals(emptySet<DayOfWeek>(), days)
    }
}
