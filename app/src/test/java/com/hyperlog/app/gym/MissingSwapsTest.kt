package com.hyperlog.app.gym

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Swaps for equipment a gym doesn't have, on the starter program (SPEC, Gyms and equipment). */
class MissingSwapsTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val dayB = program.plannedDay(Position(1, 1))!!

    @Test
    fun aMissingExerciseSwapsToTheSlotsAlternativeThenTheMovementPattern() {
        assertEquals(
            listOf(SessionSwap("leg_extension", "leg_extension", "reverse_nordic")),
            MissingSwaps.forDay(dayB, library.exercises, setOf("leg_extension")),
        )
        // With the alternative missing too, another knee extension from the library.
        val other = MissingSwaps.forDay(dayB, library.exercises, setOf("leg_extension", "reverse_nordic")).single().replacement
        assertEquals(MovementPattern.KneeExtension, library.exercises.first { it.id == other }.pattern)
    }

    @Test
    fun theReplacementForOneExerciseIsTheSameSwap() {
        val slot = dayB.slot("leg_extension")
        assertEquals("reverse_nordic", MissingSwaps.replacement(slot, "leg_extension", library.exercises, setOf("leg_extension")))
        // With every knee extension missing, nothing fits.
        val knee = library.exercises.filter { it.pattern == MovementPattern.KneeExtension }.map { it.id }.toSet()
        assertNull(MissingSwaps.replacement(slot, "leg_extension", library.exercises, knee))
    }
}
