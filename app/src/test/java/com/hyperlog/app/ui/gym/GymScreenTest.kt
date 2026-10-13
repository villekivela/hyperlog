package com.hyperlog.app.ui.gym

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.Equipment
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
    fun theSpecialtyBarsShowTheirWeightUnderTheSharedPlates() {
        // Their weight dialog holds a text field, which Robolectric can't idle in a dialog window: checked on the phone.
        compose.setContent {
            HyperlogTheme {
                GymsScreen(
                    emptyList(), LadderSpec.DefaultDumbbells, LadderSpec.DefaultPlates,
                    bars = mapOf(Equipment.EzBar to 10.0, Equipment.TrapBar to 25.0, Equipment.SafetyBar to 30.0),
                    onAddGym = {}, onOpenGym = {}, onDumbbells = {}, onPlates = {},
                    onBar = { _, _ -> }, onBack = {},
                )
            }
        }
        compose.onNodeWithText("EZ bar").assertHasClickAction()
        compose.onNodeWithText("Trap bar").assertExists()
        compose.onNodeWithText("25 kg bar, the shared plates").assertExists()
        compose.onNodeWithText("30 kg bar, the shared plates").assertExists()
    }

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
