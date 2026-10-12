package com.hyperlog.app.coach

import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.RepRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Keep for the rest of the block": a session's edits as a Change. */
class KeepEditsTest {
    private val program = (ProgramParser.parse(PlanTest.THREE_WEEKS, AnyId) as ProgramParser.Result.Valid).program
    private val dayA = program.plannedDay(Position(1, 0))!!

    private fun set(kg: Double, reps: Int, rir: Int) = LoggedSet(weightKg = kg, fullReps = reps, partialReps = 0, rir = rir)

    @Test
    fun aSessionAsWrittenHasNothingToKeep() {
        val rows = dayA.slots.map { EditedExercise(it.id, it.exercise, removed = false, sets = null, logged = emptyList()) }
        assertNull(KeepEdits.change(dayA, rows, "Keep"))
    }

    @Test
    fun swapsRemovalsSetCountsRirAndAddedExercisesAreKept() {
        val bench = dayA.slot("bench")!!
        val rows = listOf(
            EditedExercise("bench", "barbell_bench_press", removed = false, sets = bench.sets.take(2).map { it.copy(rir = it.rir!! + 1) }, logged = emptyList()),
            EditedExercise("row", "chest_supported_db_row", removed = true, sets = null, logged = emptyList()),
            EditedExercise(null, "cable_lateral_raise", removed = false, sets = null, logged = listOf(set(8.0, 12, 1), set(8.0, 10, 0))),
        )
        val change = KeepEdits.change(dayA, rows, "Keep")!!
        assertEquals(Scope.RestOfBlock, change.scope)
        assertEquals(
            listOf(
                Operation.AdjustSets("bench", 2),
                Operation.AdjustRir("bench", 1),
                Operation.Remove("row"),
                Operation.Add("a", "cable_lateral_raise", listOf(ChangeSet(count = 2, reps = RepRange(10, 12), rir = 0)), after = "bench"),
            ),
            change.operations,
        )
    }

    @Test
    fun aSwappedExerciseIsKept() {
        val rows = listOf(
            EditedExercise("bench", "barbell_bench_press", removed = false, sets = null, logged = emptyList()),
            EditedExercise("row", "chest_supported_db_row", removed = false, sets = null, logged = emptyList()),
        )
        assertEquals(listOf(Operation.Swap("row", "chest_supported_db_row")), KeepEdits.change(dayA, rows, "Keep")!!.operations)
    }

    private object AnyId : Set<String> by emptySet() {
        override fun contains(element: String) = true
    }
}
