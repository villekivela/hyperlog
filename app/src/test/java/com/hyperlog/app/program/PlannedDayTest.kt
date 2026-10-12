package com.hyperlog.app.program

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Days as they are trained in a given week: week overrides, back-offs, alternatives, position. */
class PlannedDayTest {
    private val library = setOf(
        "barbell_bench_press", "smith_machine_bench_press", "db_bench_press",
        "cable_lateral_raise", "machine_lateral_raise", "overhead_cable_triceps_extension",
    )

    /** Like Hyperlog Full Body: a ramp-in week, techniques from week 2, a deload in week 4. */
    private val json = """
        { "format": 1, "title": "Test", "blocks": [
          { "id": "main", "name": "Main", "weekStart": 1, "weekEnd": 4,
            "weeks": { "1": { "rirAdd": 1, "technique": null } },
            "days": [
              { "id": "a", "name": "Day A", "weeks": { "4": { "rir": 3, "technique": null, "fewerSets": 1 } }, "groups": [
                { "slots": [ { "id": "bench", "exercise": "barbell_bench_press",
                    "alternatives": [ "smith_machine_bench_press", { "exercise": "db_bench_press", "sets": [ { "count": 2, "reps": "8-10", "rir": 1 } ] } ],
                    "sets": [
                      { "reps": "4-6", "rir": 1, "rest": 180, "weeks": { "3": { "reps": "3-5" } } },
                      { "count": 2, "reps": "6-8", "rir": 1, "rest": 180, "backoff": {} } ] } ] },
                { "type": "superset", "slots": [
                  { "id": "lateral", "exercise": "cable_lateral_raise", "sets": [
                      { "reps": "10-12", "rir": 0, "rest": 90 },
                      { "reps": "10-12", "rir": 0, "rest": 90, "technique": { "kind": "myo_reps", "miniSetReps": 4 } } ] },
                  { "id": "triceps", "exercise": "overhead_cable_triceps_extension", "sets": [
                      { "reps": "10-12", "rir": 0, "technique": { "kind": "dropset" } } ] } ] } ] },
              { "id": "b", "name": "Day B", "groups": [
                { "slots": [ { "id": "lateral_b", "exercise": "machine_lateral_raise", "sets": [ { "reps": 12 } ] } ] } ] } ] } ] }
    """

    private val program = (ProgramParser.parse(json, library) as ProgramParser.Result.Valid).program

    private fun day(week: Int, day: Int = 0) = program.plannedDay(Position(week, day))!!

    @Test
    fun asWrittenWeekHasTheSetsTheirLabelsAndTheBackOffsPointingAtTheTopSet() {
        val day = day(2)
        assertEquals(listOf("1", "2a", "2b"), day.slots.map { it.label })
        assertEquals(
            listOf(
                PlannedSet(RepRange(4, 6), 1, 180),
                PlannedSet(RepRange(6, 8), 1, 180, backoff = Backoff(from = 0, percent = 10)),
                PlannedSet(RepRange(6, 8), 1, 180, backoff = Backoff(from = 0, percent = 10)),
            ),
            day.slot("bench")!!.sets,
        )
        assertEquals(Technique.MyoReps(4, 5), day.slot("lateral")!!.sets[1].technique)
    }

    @Test
    fun weekOneIsOneRirEasierWithNoTechniques() {
        val day = day(1)
        assertEquals(listOf(2, 2, 2), day.slot("bench")!!.sets.map { it.rir })
        assertEquals(listOf(1, 1), day.slot("lateral")!!.sets.map { it.rir })
        assertEquals(listOf(null, null), day.slot("lateral")!!.sets.map { it.technique })
        assertNull(day.slot("triceps")!!.sets.single().technique)
    }

    @Test
    fun aSetsOwnWeekOverrideChangesOnlyThatWeek() {
        assertEquals(RepRange(3, 5), day(3).slot("bench")!!.sets[0].reps)
        assertEquals(RepRange(4, 6), day(2).slot("bench")!!.sets[0].reps)
    }

    @Test
    fun theDeloadWeekHasOneSetFewerAtThreeRirAndNoTechniques() {
        val bench = day(4).slot("bench")!!.sets
        assertEquals(2, bench.size)
        assertEquals(listOf(3, 3), bench.map { it.rir })
        assertEquals(1, day(4).slot("triceps")!!.sets.size)
        assertNull(day(4).slot("lateral")!!.sets.single().technique)
    }

    @Test
    fun anAlternativeDoesTheSlotsSetsUnlessItHasItsOwn() {
        val bench = day(2).slot("bench")!!
        assertEquals(bench.sets, bench.setsFor("smith_machine_bench_press"))
        assertEquals(listOf(PlannedSet(RepRange(8, 10), 1, 120), PlannedSet(RepRange(8, 10), 1, 120)), bench.setsFor("db_bench_press"))
        // Week overrides apply to an alternative's own sets too.
        assertEquals(listOf(2, 2), day(1).slot("bench")!!.setsFor("db_bench_press").map { it.rir })
    }

    @Test
    fun theProgramRunsAsASequenceOfDaysThenWeeks() {
        assertEquals(Position(1, 1), program.next(Position(1, 0)))
        assertEquals(Position(2, 0), program.next(Position(1, 1)))
        assertNull(program.next(Position(4, 1)))
        assertEquals("Day B", day(1, 1).day.name)
    }
}
