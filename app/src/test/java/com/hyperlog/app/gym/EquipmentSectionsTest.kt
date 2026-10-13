package com.hyperlog.app.gym

import com.hyperlog.app.domain.LadderSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentSectionsTest {
    private fun machine(name: String, ladder: LadderSpec = LadderSpec.DefaultPinStack, station: Boolean = false) =
        EquipmentItem(gymId = 1, name = name, ladder = ladder, cableStation = station)

    private val plates = LadderSpec.Plates(0.0, listOf(20.0))

    @Test
    fun aGymsEquipmentFallsIntoItsKindsEachSortedByName() {
        val gym = Gym(
            1, "Central",
            items = listOf(machine("pec deck"), machine("Leg press", plates), machine("Cable station", station = true), machine("Hack squat", plates), machine("Abductor")),
            kit = listOf(
                EquipmentItem(gymId = 1, name = "Trap bar", ladder = LadderSpec.Explicit(listOf(25.0)), kind = EquipmentItem.BAR + "trap_bar"),
                EquipmentItem(gymId = 1, name = "Squat rack", ladder = LadderSpec.Explicit(emptyList()), kind = EquipmentItem.RACK),
                EquipmentItem(gymId = 1, name = "EZ bar", ladder = LadderSpec.Explicit(listOf(10.0)), kind = EquipmentItem.BAR + "ez_bar"),
                EquipmentItem(gymId = 1, name = "Adjustable bench", ladder = LadderSpec.Explicit(emptyList()), kind = EquipmentItem.RACK),
            ),
        )
        assertEquals(
            mapOf(
                EquipmentKind.Stack to listOf("Abductor", "Cable station", "pec deck"),
                EquipmentKind.PlateLoaded to listOf("Hack squat", "Leg press"),
                EquipmentKind.Bar to listOf("EZ bar", "Trap bar"),
                EquipmentKind.RackBench to listOf("Adjustable bench", "Squat rack"),
            ),
            gym.sections().mapValues { (_, items) -> items.map { it.name } },
        )
        assertEquals(listOf(EquipmentKind.Stack, EquipmentKind.PlateLoaded, EquipmentKind.Bar, EquipmentKind.RackBench), gym.sections().keys.toList())
    }

    @Test
    fun aKindWithNothingHasNoSection() {
        val gym = Gym(1, "Central", items = listOf(machine("Leg press", plates)))
        assertEquals(listOf(EquipmentKind.PlateLoaded), gym.sections().keys.toList())
    }
}
