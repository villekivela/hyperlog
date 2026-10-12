package com.hyperlog.app.program

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramParserTest {
    private val library = setOf(
        "barbell_bench_press", "smith_machine_bench_press", "db_bench_press", "hack_squat", "leg_press",
        "cable_lateral_raise", "overhead_cable_triceps_extension", "barbell_row",
    )
    private val merged = mapOf("flat_db_press" to "db_bench_press")

    private fun parse(json: String) = ProgramParser.parse(json, library, merged)

    private fun errors(json: String): List<String> {
        val result = parse(json)
        assertTrue("expected errors, got $result", result is ProgramParser.Result.Invalid)
        return (result as ProgramParser.Result.Invalid).errors
    }

    private fun valid(json: String): Program {
        val result = parse(json)
        assertTrue("expected a program, got $result", result is ProgramParser.Result.Valid)
        return (result as ProgramParser.Result.Valid).program
    }

    /** A one-block program around [slots] (the groups array's contents). */
    private fun program(groups: String, blockExtra: String = "", dayExtra: String = "") = """
        { "format": 1, "title": "Test", "blocks": [
          { "id": "build", "name": "Build", "weekStart": 1, "weekEnd": 4 $blockExtra,
            "days": [ { "id": "a", "name": "Day A" $dayExtra, "groups": [ $groups ] } ] } ] }
    """

    private val bench = """
        { "slots": [ { "id": "bench", "exercise": "barbell_bench_press",
            "alternatives": ["smith_machine_bench_press"],
            "note": "Same grip every rep.",
            "sets": [ { "reps": "4-6", "rir": 1, "rest": 180 } ] } ] }
    """

    @Test
    fun aValidProgramParsesWithItsBlocksDaysSlotsAndSets() {
        val program = valid(program(bench))
        assertEquals("Test", program.title)
        val block = program.blocks.single()
        assertEquals(1 to 4, block.weekStart to block.weekEnd)
        val slot = block.days.single().groups.single().slots.single()
        assertEquals("bench", slot.id)
        assertEquals("barbell_bench_press", slot.exercise)
        assertEquals(listOf(Alternative("smith_machine_bench_press")), slot.alternatives)
        assertEquals("Same grip every rep.", slot.note)
        assertEquals(listOf(SetSpec(reps = RepRange(4, 6), rir = 1, rest = 180)), slot.sets)
    }

    @Test
    fun anUnknownExerciseIsNamedWithItsExactPath() {
        val json = program("$bench, " + """{ "slots": [ { "id": "row", "exercise": "bb_row", "sets": [ { "reps": 8 } ] } ] }""")
        assertEquals(listOf("""blocks[0].days[0].groups[1].slots[0].exercise: unknown exercise "bb_row""""), errors(json))
    }

    @Test
    fun everyProblemIsReportedAtOnceWithItsPath() {
        val json = """
            { "format": 1, "title": "Test", "colour": "red", "blocks": [
              { "id": "build", "name": "Build", "weekStart": 1, "weekEnd": 4, "days": [
                { "id": "a", "name": "Day A", "groups": [
                  { "type": "superset", "slots": [
                    { "id": "lat", "exercise": "cable_lateral_raise", "sets": [ { "reps": "12-10" } ] } ] },
                  { "slots": [ { "id": "squat", "exercise": "hack_squat", "alternatives": ["flat_db_press"],
                      "sets": [ { "reps": 8, "rir": 11, "technique": { "kind": "rest_pause" } },
                                { "reps": 8, "backoff": { "from": 1 } } ] } ] } ] } ] },
              { "id": "build", "name": "Peak", "weekStart": 6, "weekEnd": 5, "days": [] } ] }
        """
        assertEquals(
            listOf(
                """unknown field "colour"""",
                """blocks[0].days[0].groups[0].slots[0].sets[0].reps: "12-10": the range must go up; write a fixed number as 12""",
                """blocks[0].days[0].groups[0].slots: a superset has 2 slots, not 1""",
                """blocks[0].days[0].groups[1].slots[0].alternatives[0]: unknown exercise "flat_db_press" (the library calls it "db_bench_press")""",
                """blocks[0].days[0].groups[1].slots[0].sets[0].rir: must be from 0 to 10""",
                """blocks[0].days[0].groups[1].slots[0].sets[0].technique.kind: unknown technique "rest_pause" (dropset, myo_reps, lengthened_partials, iso_hold, failure)""",
                """blocks[0].days[0].groups[1].slots[0].sets[1].backoff.from: must point at an earlier line (0 to 0)""",
                """blocks[1].weekEnd: must not be before weekStart (6)""",
                """blocks[1].days: must not be empty""",
                """blocks[1].weekStart: must be 5, the week after the previous block""",
                """blocks[1].id: "build" is used by another block""",
            ),
            errors(json),
        )
    }

    @Test
    fun aFileThatIsNotJsonOrHasTheWrongFormatIsRejected() {
        assertTrue(errors("{ not json").single().startsWith("not valid JSON"))
        assertEquals(
            listOf("format: must be 1", "blocks: is required"),
            errors("""{ "format": 2, "title": "X" }"""),
        )
    }

    @Test
    fun aWeekOverrideOutsideItsBlockIsNamed() {
        val json = program(
            """{ "slots": [ { "id": "bench", "exercise": "barbell_bench_press",
                "sets": [ { "reps": "4-6", "rir": 1, "weeks": { "5": { "rir": 0 }, "2-3": { "sets": 2 } } } ] } ] }""",
        )
        assertEquals(
            listOf(
                """blocks[0].days[0].groups[0].slots[0].sets[0].weeks."5": week 5 is outside the block's weeks 1–4""",
                """blocks[0].days[0].groups[0].slots[0].sets[0].weeks."2-3": unknown field "sets"""",
            ),
            errors(json),
        )
    }

    @Test
    fun techniquesParseWithTheirDefaults() {
        val json = program(
            """{ "slots": [ { "id": "lat", "exercise": "cable_lateral_raise", "sets": [
                { "reps": "10-12", "rir": 0, "technique": { "kind": "dropset" } },
                { "reps": "10-12", "rir": 0, "technique": { "kind": "myo_reps", "miniSetReps": 4 } },
                { "reps": "10-12", "rir": 0, "technique": { "kind": "lengthened_partials" } },
                { "reps": "10-12", "rir": 0, "technique": { "kind": "iso_hold", "seconds": 30, "position": "stretch" } },
                { "reps": "10-12", "rir": 0, "technique": { "kind": "failure" } } ] } ] }""",
        )
        assertEquals(
            listOf(
                Technique.Dropset(drops = 2, percent = 25),
                Technique.MyoReps(miniSetReps = 4, maxMiniSets = 5),
                Technique.LengthenedPartials(partials = null),
                Technique.IsoHold(30, HoldPosition.Stretch),
                Technique.Failure,
            ),
            valid(json).blocks[0].days[0].groups[0].slots[0].sets.map { it.technique },
        )
    }
}
