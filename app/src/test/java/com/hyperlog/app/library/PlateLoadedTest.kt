package com.hyperlog.app.library

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which machines take plates, so a new one opens on the plates rather than a stack. */
class PlateLoadedTest {
    private fun exercise(name: String, equipment: Equipment) =
        Exercise(id = name, name = name, equipment = equipment, pattern = MovementPattern.entries.first(), grip = GripDemand.None, muscles = listOf("Quads"))

    @Test
    fun legPressesSledsHackSquatsAndSmithMachinesTakePlates() {
        listOf("Leg Press", "45° Leg Press", "Hack Squat", "Pendulum Squat", "Sled Push", "Plate-Loaded Row").forEach {
            assertEquals(it, true, exercise(it, Equipment.Machine).plateLoaded)
        }
        assertEquals(true, exercise("Smith Machine Squat", Equipment.SmithMachine).plateLoaded)
    }

    @Test
    fun stackMachinesCablesAndFreeWeightsDont() {
        assertEquals(false, exercise("Leg Extension", Equipment.Machine).plateLoaded)
        assertEquals(false, exercise("Machine Chest Press", Equipment.Machine).plateLoaded)
        assertEquals(false, exercise("Cable Lateral Raise", Equipment.Cable).plateLoaded)
        assertEquals(false, exercise("DB Bench Press", Equipment.Dumbbell).plateLoaded)
    }
}
