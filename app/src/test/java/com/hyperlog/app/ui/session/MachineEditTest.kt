package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The exercise sheet opens its machine's setup, so a machine can be fixed mid-session. */
@RunWith(RobolectricTestRunner::class)
class MachineEditTest {
    @get:Rule
    val compose = createComposeRule()

    private val press = Exercise("leg_press", "Leg Press", Equipment.Machine, MovementPattern.Squat, GripDemand.Low, listOf("Quads"))

    @Test
    fun editOpensTheMachinesSetup() {
        var edited = false
        compose.setContent {
            HyperlogTheme { ExerciseSheet(press, machine = "Leg press · Harbour", onChangeMachine = null, onEditMachine = { edited = true }) }
        }
        compose.onNodeWithContentDescription("Edit machine").performClick()
        assertTrue(edited)
    }

    @Test
    fun noMachineNoEdit() {
        compose.setContent { HyperlogTheme { ExerciseSheet(press, machine = null, onChangeMachine = null, onEditMachine = {}) } }
        compose.onAllNodesWithContentDescription("Edit machine").assertCountEquals(0)
    }
}
