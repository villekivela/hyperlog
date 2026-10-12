package com.hyperlog.app.coach

import com.hyperlog.app.program.Program
import com.hyperlog.app.program.ProgramParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChangeValidatorTest {
    private val exercises = setOf("barbell_bench_press", "seated_cable_row", "hack_squat", "chest_supported_db_row", "cable_lateral_raise")
    private val program: Program = (ProgramParser.parse(PlanTest.THREE_WEEKS, exercises) as ProgramParser.Result.Valid).program

    private fun errors(json: String) = (ChangeValidator.validate(json, program, exercises) as ChangeValidator.Result.Invalid).errors

    @Test
    fun specsExampleReadsAndWritesBack() {
        val json = """
            {
              "summary": "Hands need a week off grip work. Train legs and machines, then resume week 10.",
              "scope": { "from": "2026-10-13", "to": "2026-10-19" },
              "operations": [
                { "op": "swap", "slot": "row", "exercise": "chest_supported_db_row", "reason": "no grip" },
                { "op": "remove", "slot": "bench", "reason": "grip" },
                { "op": "adjust_sets", "slot": "squat", "sets": 2, "reason": "returning after 11 days off" },
                { "op": "shift_program", "weeks": 1 }
              ]
            }
        """
        val change = (ChangeValidator.validate(json, program, exercises) as ChangeValidator.Result.Valid).change
        assertEquals(Scope.Dates(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 19)), change.scope)
        assertEquals(Operation.Swap("row", "chest_supported_db_row", "no grip"), change.operations[0])
        assertEquals(Operation.ShiftProgram(1), change.operations[3])
        assertEquals(change, ChangeJson.decode(ChangeJson.encode(change)))
        assertTrue(ChangeJson.encode(change).contains(""""scope":{"from":"2026-10-13","to":"2026-10-19"}"""))
        assertTrue(ChangeJson.encode(change).contains("""{"op":"shift_program","weeks":1}"""))
    }

    @Test
    fun unknownSlotsExercisesAndBadValuesAreNamedByPath() {
        assertEquals(
            listOf(
                """operations[0].slot: unknown slot "rows"""",
                """operations[1].exercise: unknown exercise "bb_row"""",
                "operations[2].sets: must be 1 to 10",
                "operations[3].percent: must be −50 to 50, not 0",
                """operations[4].day: unknown day "z"""",
                "operations[5].weeks: must not be 0",
                "operations[6].week: must be 1 to 3",
            ),
            errors(
                """{"summary":"x","scope":"rest_of_program","operations":[
                    {"op":"remove","slot":"rows"},
                    {"op":"swap","slot":"row","exercise":"bb_row"},
                    {"op":"adjust_sets","slot":"row","sets":0},
                    {"op":"adjust_load","percent":0},
                    {"op":"add","day":"z","exercise":"seated_cable_row","sets":[{"reps":10}]},
                    {"op":"shift_program","weeks":0},
                    {"op":"deload_week","week":4}]}""",
            ),
        )
    }

    @Test
    fun aChangeThatIsntOneSaysWhy() {
        assertEquals(listOf("summary: a summary is required"), errors("""{"summary":" ","scope":"rest_of_program","operations":[{"op":"skip_day"}]}"""))
        assertEquals(listOf("operations: at least one operation is required"), errors("""{"summary":"x","scope":"rest_of_program","operations":[]}"""))
        assertEquals(listOf("scope: \"to\" is before \"from\""), errors("""{"summary":"x","scope":{"from":"2026-10-13","to":"2026-10-12"},"operations":[{"op":"skip_day"}]}"""))
        assertTrue(errors("""{"summary":"x","scope":"rest_of_program","operations":[{"op":"fly"}]}""").single().contains("fly"))
        assertTrue(errors("not json").single().startsWith("not a Change"))
    }
}
