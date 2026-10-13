package com.hyperlog.app.coach

import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.RepRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The plan for a date: the program as written with the accepted Changes whose scope covers it. */
class PlanTest {
    private val program: Program = (ProgramParser.parse(THREE_WEEKS, AnyId) as ProgramParser.Result.Valid).program
    private val oct13: LocalDate = LocalDate.of(2026, 10, 13)

    private fun accepted(json: String, at: Position = Position(1, 0), on: LocalDate = oct13, id: Long = 1) =
        Changes.accept(program, at, ChangeJson.decode(json), on).copy(id = id)

    private fun exercises(position: Position, date: LocalDate, changes: List<AcceptedChange>) =
        Changes.day(program, position, date, changes)!!.slots.map { it.exercise }

    @Test
    fun aTemporarySwapAppliesInsideItsDatesAndStopsAfterThem() {
        val swap = accepted(
            """{"summary":"No grip","scope":{"from":"2026-10-13","to":"2026-10-19"},
               "operations":[{"op":"swap","slot":"row","exercise":"chest_supported_db_row","reason":"no grip"}]}""",
        )
        assertEquals(listOf("barbell_bench_press", "chest_supported_db_row"), exercises(Position(1, 0), oct13, listOf(swap)))
        assertEquals(listOf("barbell_bench_press", "chest_supported_db_row"), exercises(Position(1, 0), oct13.plusDays(6), listOf(swap)))
        assertEquals(listOf("barbell_bench_press", "seated_cable_row"), exercises(Position(1, 0), oct13.plusDays(7), listOf(swap)))
        assertEquals(listOf("barbell_bench_press", "seated_cable_row"), exercises(Position(1, 0), oct13.minusDays(1), listOf(swap)))
    }

    @Test
    fun theRestOfTheBlockEndsWithTheBlock() {
        val swap = accepted("""{"summary":"Keep","scope":"rest_of_block","operations":[{"op":"swap","slot":"row","exercise":"chest_supported_db_row"}]}""")
        assertEquals("chest_supported_db_row", exercises(Position(2, 0), oct13.plusDays(30), listOf(swap))[1])
        // Week 3 is another block reusing the slot id: the edit was for the block it was made in.
        assertEquals("seated_cable_row", exercises(Position(3, 0), oct13.plusDays(30), listOf(swap))[1])
    }

    @Test
    fun theRestOfTheProgramStartsTheDayItIsAccepted() {
        val remove = accepted("""{"summary":"x","scope":"rest_of_program","operations":[{"op":"remove","slot":"row"}]}""")
        assertEquals(listOf("barbell_bench_press"), exercises(Position(3, 0), oct13.plusDays(30), listOf(remove)))
        assertEquals(listOf("barbell_bench_press", "seated_cable_row"), exercises(Position(1, 0), oct13.minusDays(1), listOf(remove)))
    }

    @Test
    fun setsRirLoadAndAddedExercisesChangeTheDay() {
        val change = accepted(
            """{"summary":"x","scope":"rest_of_program","operations":[
                {"op":"adjust_sets","slot":"bench","sets":2},
                {"op":"adjust_sets","slot":"row","sets":4},
                {"op":"adjust_rir","by":1},
                {"op":"adjust_load","slot":"bench","percent":-10},
                {"op":"add","day":"a","exercise":"cable_lateral_raise","sets":[{"count":2,"reps":"12-15","rir":1}],"after":"bench"}]}""",
        )
        val day = Changes.day(program, Position(2, 0), oct13, listOf(change))!!
        val (bench, added, row) = day.slots
        assertEquals(listOf(RepRange(4, 6), RepRange(6, 8)), bench.sets.map { it.reps })
        assertEquals(listOf(2, 2), bench.sets.map { it.rir })
        assertEquals(-10, bench.loadPercent)
        assertEquals("cable_lateral_raise", added.exercise)
        assertEquals("+", added.label)
        // Operations run in order: the RIR change came before the exercise was added.
        assertEquals(listOf(1, 1), added.sets.map { it.rir })
        assertEquals(4, row.sets.size)
        assertEquals(0, row.loadPercent)
    }

    @Test
    fun aDeloadWeekHalvesTheSetsAndEasesTheEffortOfThatWeekOnly() {
        val deload = accepted("""{"summary":"Deload","scope":"rest_of_program","operations":[{"op":"deload_week"}]}""", at = Position(2, 1))
        val week2 = Changes.day(program, Position(2, 0), oct13, listOf(deload))!!
        assertTrue(week2.deload)
        assertEquals(2, week2.slots[0].sets.size)
        assertEquals(listOf(3, 3), week2.slots[0].sets.map { it.rir })
        assertEquals(false, Changes.day(program, Position(3, 0), oct13, listOf(deload))!!.deload)
    }

    @Test
    fun positionOperationsMoveThePositionOnceWhenAccepted() {
        fun after(op: String, at: Position) = accepted("""{"summary":"x","scope":"rest_of_program","operations":[$op]}""", at).after
        assertEquals(Position(1, 1), after("""{"op":"skip_day"}""", Position(1, 0)))
        assertEquals(Position(2, 0), after("""{"op":"skip_day"}""", Position(1, 1)))
        assertEquals(Position(1, 1), after("""{"op":"shift_program","weeks":1}""", Position(2, 1)))
        assertEquals(Position(1, 0), after("""{"op":"shift_program","weeks":3}""", Position(2, 1)))
        assertEquals(Position(3, 0), after("""{"op":"shift_program","weeks":-1}""", Position(2, 0)))
        assertEquals(Position(2, 0), after("""{"op":"repeat_week"}""", Position(2, 1)))
        assertEquals(Position(1, 0), after("""{"op":"repeat_week"}""", Position(2, 0)))
    }

    @Test
    fun revertingAPositionChangeTakesItsMoveBack() {
        val shift = accepted("""{"summary":"x","scope":"rest_of_program","operations":[{"op":"shift_program","weeks":1}]}""", Position(2, 1))
        assertEquals(Position(2, 1), Changes.revert(program, Position(1, 1), shift))
        // Trained a day since: that day still counts.
        assertEquals(Position(3, 0), Changes.revert(program, Position(2, 0), shift))
    }

    @Test
    fun theDiffShowsTheMovedPositionAndTheChangedSlots() {
        val shift = ChangeJson.decode("""{"summary":"x","scope":"rest_of_program","operations":[{"op":"shift_program","weeks":1}]}""")
        val diff = Changes.diff(program, Position(2, 0), oct13, emptyList(), shift)
        assertEquals(Position(2, 0) to Position(1, 0), diff.next)
        assertEquals(4 to 6, diff.daysLeft)
        assertTrue(diff.days.isEmpty())

        val swap = ChangeJson.decode(
            """{"summary":"x","scope":{"from":"2026-10-13","to":"2026-10-19"},"operations":[{"op":"swap","slot":"row","exercise":"chest_supported_db_row"}]}""",
        )
        val swapped = Changes.diff(program, Position(1, 0), oct13, emptyList(), swap)
        assertNull(swapped.next)
        assertEquals(listOf("Day A"), swapped.days.map { it.day.day.name })
        val slot = swapped.days.single().slots.single()
        assertEquals("seated_cable_row", slot.before!!.exercise)
        assertEquals("chest_supported_db_row", slot.after!!.exercise)
    }

    private object AnyId : Set<String> by emptySet() {
        override fun contains(element: String) = true
    }

    companion object {
        /** Weeks 1–2 one block, week 3 another reusing the slot ids; two days a week. */
        val THREE_WEEKS = """
            { "format": 1, "title": "Three", "blocks": [
              { "id": "one", "name": "One", "weekStart": 1, "weekEnd": 2, "days": [
                { "id": "a", "name": "Day A", "groups": [
                  { "slots": [ { "id": "bench", "exercise": "barbell_bench_press",
                      "sets": [ { "reps": "4-6", "rir": 1 }, { "count": 2, "reps": "6-8", "rir": 1, "backoff": {} } ] } ] },
                  { "slots": [ { "id": "row", "exercise": "seated_cable_row", "sets": [ { "count": 2, "reps": "8-10", "rir": 1 } ] } ] } ] },
                { "id": "b", "name": "Day B", "groups": [
                  { "slots": [ { "id": "squat", "exercise": "hack_squat", "sets": [ { "count": 2, "reps": "6-8", "rir": 1 } ] } ] } ] } ] },
              { "id": "two", "name": "Two", "weekStart": 3, "weekEnd": 3, "days": [
                { "id": "a", "name": "Day A", "groups": [
                  { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "reps": "4-6", "rir": 0 } ] } ] },
                  { "slots": [ { "id": "row", "exercise": "seated_cable_row", "sets": [ { "reps": "8-10", "rir": 0 } ] } ] } ] },
                { "id": "b", "name": "Day B", "groups": [
                  { "slots": [ { "id": "squat", "exercise": "hack_squat", "sets": [ { "reps": "6-8", "rir": 0 } ] } ] } ] } ] } ] }
        """
    }
}
