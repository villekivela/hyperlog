package com.hyperlog.app.program

import com.hyperlog.app.coach.AcceptedChange
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.Operation
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.library.LibrarySeed
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** The Changes Coach shows as cards: those covering today or a later day (SPEC, Coaching → The screen). */
class InForceTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), library.exercises.map { it.id }.toSet(), library.merged) as ProgramParser.Result.Valid).program
    private val oct13 = LocalDate.of(2026, 10, 13)

    private fun record(id: Long, scope: Scope, acceptedOn: LocalDate = oct13.minusDays(3), block: String? = program.blockAt(1)!!.id, reverted: Boolean = false) =
        ChangeRecord(
            AcceptedChange(id, Change("Change $id", scope, listOf(Operation.AdjustRir(null, 1))), acceptedOn, Position(1, 0), Position(1, 0), block),
            acceptedAt = id, revertedAt = if (reverted) 1L else null,
        )

    @Test
    fun aChangeIsInForceUntilItsLastDateUnlessReverted() {
        val history = listOf(
            record(1, Scope.Dates(oct13.minusDays(7), oct13.minusDays(1))), // over yesterday
            record(2, Scope.Dates(oct13.minusDays(3), oct13)), // ends today
            record(3, Scope.RestOfProgram),
            record(4, Scope.RestOfProgram, reverted = true),
            record(5, Scope.Dates(oct13.plusDays(2), oct13.plusDays(9))), // starts later
        )
        val active = ActiveProgram(1, program, Position(1, 1), history, today = oct13)
        // Newest first.
        assertEquals(listOf(5L, 3L, 2L), active.inForce.map { it.id })
    }

    @Test
    fun restOfBlockEndsWithItsBlock() {
        val history = listOf(record(1, Scope.RestOfBlock))
        assertEquals(1, ActiveProgram(1, program, Position(1, 1), history, today = oct13).inForce.size)
        val laterBlock = program.blocks[1].weekStart
        assertEquals(0, ActiveProgram(1, program, Position(laterBlock, 0), history, today = oct13).inForce.size)
    }

    @Test
    fun aFinishedProgramHasNothingInForce() {
        val history = listOf(record(1, Scope.RestOfProgram))
        assertEquals(0, ActiveProgram(1, program, Position(program.weeks + 1, 0), history, today = oct13).inForce.size)
    }

    @Test
    fun aChangeInForceShowsWhatItDoesFromHereOn() {
        val swap = AcceptedChange(
            7, Change("Hands", Scope.Dates(oct13.minusDays(2), oct13.plusDays(5)), listOf(Operation.Swap("bench", "smith_machine_bench_press"))),
            oct13.minusDays(2), Position(1, 0), Position(1, 0), program.blockAt(1)!!.id,
        )
        val diff = com.hyperlog.app.coach.Changes.inForceDiff(program, Position(1, 0), oct13, listOf(swap), swap)
        val slot = diff.days.first().slots.single()
        assertEquals("barbell_bench_press", slot.before!!.exercise)
        assertEquals("smith_machine_bench_press", slot.after!!.exercise)
        assertEquals(null, diff.next)

        // A move of the position shows as it was made.
        val shift = AcceptedChange(8, Change("Back a week", Scope.RestOfProgram, listOf(Operation.ShiftProgram(1))), oct13, Position(3, 0), Position(2, 0), null)
        assertEquals(Position(3, 0) to Position(2, 0), com.hyperlog.app.coach.Changes.inForceDiff(program, Position(2, 0), oct13, listOf(shift), shift).next)
    }
}
