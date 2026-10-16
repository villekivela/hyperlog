package com.hyperlog.app.ui.session

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.ui.theme.HyperlogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The exercise sheet's set table draws RIR as the chip every other screen uses (SPEC, RIR chip). */
@RunWith(RobolectricTestRunner::class)
class ExerciseSheetRirTest {
    @get:Rule
    val compose = createComposeRule()

    private val bench = Exercise("barbell_bench_press", "Barbell Bench Press", Equipment.Barbell, MovementPattern.HorizontalPush, GripDemand.Low, listOf("Chest"))

    @Test
    fun theSetTableShowsTargetsAsOutlinedChipsAndLastTimeAsABadge() {
        val plan = listOf(SetPrescription(RepRange(4, 6), 1, 180), SetPrescription(RepRange(6, 8), 2, 180), SetPrescription(RepRange(6, 8), 2, 180))
        compose.setContent {
            HyperlogTheme {
                ExerciseSheet(bench, machine = null, onChangeMachine = null, plan = plan, why = "Last time 97.5 kg × 6 @ 1, top of 4–6 → 100 kg" to Direction.Up)
            }
        }
        compose.onAllNodesWithTag(RirChipTargetTag, useUnmergedTree = true).assertCountEquals(3)
        compose.onAllNodesWithTag(RirChipLoggedTag, useUnmergedTree = true).assertCountEquals(1)
    }
}
