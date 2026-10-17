package com.hyperlog.app.ui.program

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Training days sheet's weekday toggles (SPEC, Training days). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class WeekdayTogglesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun noTwoDaysShareALabelAndTalkBackKeepsTheirNames() {
        compose.setContent { HyperlogTheme { WeekdayToggles(emptySet()) { _, _ -> } } }
        listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        compose.onAllNodesWithText("T", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("S", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Tuesday").assertExists()
        compose.onNodeWithContentDescription("Thursday").assertExists()
    }
}
