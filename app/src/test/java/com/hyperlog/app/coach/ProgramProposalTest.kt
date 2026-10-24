package com.hyperlog.app.coach

import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.library.MovementPattern
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A new program from Claude (SPEC, Coaching → New program): a program file, the swaps it made to
 * library exercises, and the exercises it adds, checked as import and the library editor check them.
 */
class ProgramProposalTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val names = library.exercises.associate { it.id to it.name }

    private fun read(json: String) = ProgramProposal.read(Json.parseToJsonElement(json).jsonObject, ids, names, library.merged)

    /** A one-block program of one day: a row, then [second] (a library id or a new exercise's). */
    private fun program(second: String = "seated_cable_row") = """
        {"format": 1, "title": "Nippard Upper/Lower", "blocks": [{"id": "build", "name": "Build", "weekStart": 1, "weekEnd": 4, "days": [
          {"id": "upper", "name": "Upper", "groups": [
            {"slots": [{"id": "row", "exercise": "barbell_row", "sets": [{"count": 3, "reps": "6-8", "rir": 1, "rest": 180}]}]},
            {"slots": [{"id": "second", "exercise": "$second", "sets": [{"count": 2, "reps": "10-12", "rir": 1, "rest": 90}]}]}
          ]}]}]}
    """.trimIndent()

    private val newExercise = """
        {"id": "kneeling_cable_y_raise", "name": "Kneeling Cable Y-Raise", "equipment": "cable", "pattern": "lateral_raise",
         "grip": "low", "muscles": ["Side Delts"], "secondary": ["Traps"]}
    """.trimIndent()

    @Test
    fun aProgramWithItsSwapsAndNewExercisesReads() {
        val read = read(
            """{"program": ${program("kneeling_cable_y_raise")}, "swaps": [{"from": "Deficit Snatch-Grip Row", "to": "barbell_row"}],
               "newExercises": [$newExercise]}""",
        ) as ProgramProposal.Result.Valid
        val proposal = read.proposal
        assertEquals("Nippard Upper/Lower", proposal.program.title)
        assertEquals(listOf(ProgramProposal.Swap("Deficit Snatch-Grip Row", "barbell_row")), proposal.swaps)
        val added = proposal.newExercises.single()
        assertEquals("kneeling_cable_y_raise", added.id)
        assertEquals("Kneeling Cable Y-Raise", added.name)
        assertEquals(Equipment.Cable, added.equipment)
        assertEquals(MovementPattern.LateralRaise, added.pattern)
        assertEquals(GripDemand.Low, added.grip)
        assertEquals(listOf("Side Delts"), added.muscles)
        assertEquals(listOf("Traps"), added.secondary)
        // The file Start program imports is the program itself, which reads as import reads it.
        assertEquals(Json.parseToJsonElement(program("kneeling_cable_y_raise")), Json.parseToJsonElement(proposal.source))
    }

    @Test
    fun swapsAndNewExercisesMayBeLeftOut() {
        val proposal = (read("""{"program": ${program()}}""") as ProgramProposal.Result.Valid).proposal
        assertEquals(emptyList<ProgramProposal.Swap>(), proposal.swaps)
        assertEquals(emptyList<Any>(), proposal.newExercises)
    }

    @Test
    fun theProgramsProblemsHavePathsUnderProgram() {
        val errors = (read("""{"program": ${program("no_such_row")}, "swaps": [], "newExercises": []}""") as ProgramProposal.Result.Invalid).errors
        assertEquals(listOf("program.blocks[0].days[0].groups[1].slots[0].exercise: unknown exercise \"no_such_row\""), errors)
    }

    @Test
    fun aNewExerciseMustPassTheLibraryEditorsRules() {
        val bad = """
            {"id": "Y Raise", "name": " ", "equipment": "kettlebell", "pattern": "raise", "grip": "medium", "muscles": [], "secondary": ["Delts"]}
        """.trimIndent()
        val errors = (read("""{"program": ${program()}, "swaps": [], "newExercises": [$bad]}""") as ProgramProposal.Result.Invalid).errors
        assertEquals(
            listOf(
                "newExercises[0].id: lowercase letters, digits and _ only",
                "newExercises[0].name: must not be blank",
                "newExercises[0].equipment: must be one of ${Equipment.entries.joinToString { it.key }}, not \"kettlebell\"",
                "newExercises[0].pattern: must be one of ${MovementPattern.entries.joinToString { it.key }}, not \"raise\"",
                "newExercises[0].grip: must be one of none, low, high, not \"medium\"",
                "newExercises[0].muscles: name at least one primary muscle",
                "newExercises[0].secondary[0]: unknown muscle \"Delts\"",
            ),
            errors,
        )
    }

    @Test
    fun aNewExerciseTheLibraryHasOrTheProgramDoesntUseIsTurnedDown() {
        val taken = """{"id": "barbell_row", "name": "Barbell Row", "equipment": "barbell", "pattern": "horizontal_pull", "grip": "high", "muscles": ["Lats"], "secondary": []}"""
        val sameName = """{"id": "my_row", "name": "seated cable row", "equipment": "cable", "pattern": "horizontal_pull", "grip": "high", "muscles": ["Lats"], "secondary": []}"""
        val errors = (read("""{"program": ${program("kneeling_cable_y_raise")}, "swaps": [], "newExercises": [$taken, $sameName, $newExercise, $newExercise]}""") as ProgramProposal.Result.Invalid).errors
        assertEquals(
            listOf(
                "newExercises[0].id: the library already has \"barbell_row\" (Barbell Row): use it",
                "newExercises[1].name: the library already has \"Seated Cable Row\" (seated_cable_row): use it",
                "newExercises[3].id: \"kneeling_cable_y_raise\" is added twice",
                "newExercises[1]: the program doesn't use \"my_row\"",
            ),
            errors,
        )
    }

    @Test
    fun aSwapNamesWhatTheFileHadAndALibraryExercise() {
        val errors = (read("""{"program": ${program()}, "swaps": [{"from": "", "to": "bb_row"}], "newExercises": []}""") as ProgramProposal.Result.Invalid).errors
        assertEquals(listOf("swaps[0].from: must not be blank", "swaps[0].to: unknown exercise \"bb_row\""), errors)
    }

    @Test
    fun unknownFieldsAndAMissingProgramAreProblems() {
        val errors = (read("""{"swaps": [], "plan": {}}""") as ProgramProposal.Result.Invalid).errors
        assertEquals(listOf("plan: unknown field", "program: is required"), errors)
    }
}
