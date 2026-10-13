package com.hyperlog.app.session

import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Which machine?" offers the machines that fit the exercise first, the rest after. */
class MachineChoicesTest {
    private fun exercise(name: String) = Exercise(name, name, Equipment.Machine, MovementPattern.entries.first(), GripDemand.None, listOf("Quads"))
    private val stack = LadderSpec.Steps(5.0, 5.0, WeightUnit.Kg, 20)
    private val plates = LadderSpec.Plates(40.0, listOf(20.0))
    private val items = listOf(
        EquipmentItem(1, 1, "Leg press", plates),
        EquipmentItem(2, 1, "Machine pulldown", stack),
        EquipmentItem(3, 1, "Leg curl", stack),
        EquipmentItem(4, 1, "Hack squat", plates),
    )

    @Test
    fun aStackExerciseGetsStackMachinesFirstThoseSharingAWordAtTheTop() {
        val (fit, other) = machineChoices(exercise("Lying Leg Curl"), items)
        assertEquals(listOf("Leg curl", "Machine pulldown"), fit.map { it.name })
        assertEquals(listOf("Leg press", "Hack squat"), other.map { it.name })
    }

    @Test
    fun aPlateLoadedExerciseGetsPlateLoadedMachinesFirst() {
        val (fit, other) = machineChoices(exercise("Leg Press"), items)
        assertEquals(listOf("Leg press", "Hack squat"), fit.map { it.name })
        assertEquals(listOf("Machine pulldown", "Leg curl"), other.map { it.name })
    }
}
