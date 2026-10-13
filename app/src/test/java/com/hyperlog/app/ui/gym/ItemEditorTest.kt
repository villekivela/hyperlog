package com.hyperlog.app.ui.gym

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.LadderState
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w410dp-h914dp")
class ItemEditorTest {
    @get:Rule
    val compose = createComposeRule()

    private val ezBar = EquipmentItem(3, 1, "EZ bar", LadderSpec.Explicit(listOf(10.0)), kind = "bar:ez_bar")

    /** A gym with two pairs of 20 and two of 5: 100 kg on a bar. */
    private val gym = Gym(1, "Central", emptyList(), kit = listOf(ezBar), plates = listOf(20.0, 20.0, 5.0, 5.0))

    @Test
    fun aBarsWeightIsEditedOnTheGymsPlatesAndStaysABar() {
        var saved: EquipmentItem? = null
        compose.setContent {
            HyperlogTheme { ItemEditor(gym, ezBar, onSave = { saved = it }, onDelete = null, onClose = {}) }
        }
        // The gym's plates go on it: the bar alone, up to the bar and every plate.
        compose.onNodeWithText("10 kg + plates, to 110 kg", substring = true).assertExists()
        compose.onNodeWithText("Pairs of plates in kg: one number a pair, or 3×20").assertDoesNotExist()

        compose.onNodeWithText("Bar weight (kg)").performTextReplacement("8")
        compose.onNodeWithText("8 kg + plates, to 108 kg", substring = true).assertExists()
        compose.onNodeWithText("Save").performClick()
        assertEquals(ezBar.copy(ladder = LadderSpec.Explicit(listOf(8.0))), saved)
    }

    @Test
    fun aPlateLoadedMachinesStartingWeightIsEditedOnTheGymsPlates() {
        val legPress = EquipmentItem(5, 1, "Leg press", LadderSpec.Plates(0.0, emptyList()), state = LadderState.Unconfirmed)
        var saved: EquipmentItem? = null
        compose.setContent {
            HyperlogTheme { ItemEditor(gym.copy(items = listOf(legPress)), legPress, onSave = { saved = it }, onDelete = null, onClose = {}) }
        }
        compose.onNodeWithText("Plates, to 100 kg", substring = true).assertExists()
        compose.onNodeWithText("Starting weight (kg)").performTextReplacement("30")
        compose.onNodeWithText("30 kg + plates, to 130 kg", substring = true).assertExists()
        compose.onNodeWithText("Save").performClick()
        assertEquals(legPress.copy(ladder = LadderSpec.Plates(30.0, emptyList()), state = LadderState.Confirmed), saved)
    }
}
