package com.hyperlog.app.coach

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A workout's JSON (SPEC, Coaching → Workout): read and checked as program import does, paths and all. */
class WorkoutTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()

    private val hotel = """
        { "workout": {
            "name": "Hotel dumbbells, 45 min",
            "summary": "Upper body with dumbbells only. Back first, since it's behind this week.",
            "groups": [
              { "slots": [ { "id": "row", "exercise": "chest_supported_db_row",
                  "sets": [ { "count": 3, "reps": "8-12", "rir": 1, "rest": 120 } ] } ] },
              { "type": "superset", "slots": [
                  { "id": "press", "exercise": "db_bench_press", "sets": [ { "count": 2, "reps": "8-10", "rir": 2 } ] },
                  { "id": "curl", "exercise": "db_bicep_curl", "sets": [ { "reps": "10-12", "rir": 0, "technique": { "kind": "dropset" } } ] } ] }
            ] } }
    """

    private fun read(json: String) = ProgramParser.workout(json, ids)

    @Test
    fun aWorkoutReadsAsADayOfGroupsSlotsAndSetLines() {
        val workout = (read(hotel) as ProgramParser.WorkoutResult.Valid).workout
        assertEquals("Hotel dumbbells, 45 min", workout.name)
        assertTrue(workout.summary.startsWith("Upper body"))
        val day = workout.day()
        assertEquals("Hotel dumbbells, 45 min", day.day.name)
        assertEquals(listOf("1", "2a", "2b"), day.slots.map { it.label })
        assertEquals(listOf("chest_supported_db_row", "db_bench_press", "db_bicep_curl"), day.slots.map { it.exercise })
        assertEquals(List(3) { RepRange(8, 12) }, day.slot("row")!!.sets.map { it.reps })
        assertEquals(Technique.Dropset(), day.slot("curl")!!.sets.single().technique)
        assertEquals(false, day.deload)
    }

    @Test
    fun everyProblemIsNamedByItsPath() {
        val bad = """
            { "workout": { "name": " ", "groups": [
                { "slots": [ { "id": "row", "exercise": "bb_row", "sets": [ { "reps": "8-12", "rir": 11 } ] } ] },
                { "slots": [ { "id": "row", "exercise": "db_bench_press", "sets": [ { "reps": 8, "weeks": { "1": { "rir": 2 } } } ] } ] }
            ], "extra": 1 } }
        """
        val errors = (read(bad) as ProgramParser.WorkoutResult.Invalid).errors
        assertEquals(
            listOf(
                "workout: unknown field \"extra\"",
                "workout.name: must not be empty",
                "workout.summary: is required",
                "workout.groups[0].slots[0].exercise: unknown exercise \"bb_row\"",
                "workout.groups[0].slots[0].sets[0].rir: must be from 0 to 10",
                "workout.groups[1].slots[0].sets[0]: unknown field \"weeks\"",
                "workout.groups[1].slots[0].id: \"row\" is used by another slot in this workout",
            ),
            errors,
        )
    }

    @Test
    fun onlyAnObjectHoldingAWorkoutIsOne() {
        assertEquals(listOf("unknown field \"name\"", "workout: is required"), (read("""{ "name": "x" }""") as ProgramParser.WorkoutResult.Invalid).errors)
        assertTrue((read("[1]") as ProgramParser.WorkoutResult.Invalid).errors.single().startsWith("must be an object"))
    }

    @Test
    fun aWorkoutWrittenOutReadsBackTheSame() {
        val workout = (read(hotel) as ProgramParser.WorkoutResult.Valid).workout
        val again = (read(WorkoutJson.encode(workout)) as ProgramParser.WorkoutResult.Valid).workout
        assertEquals(workout, again)
    }

    @Test
    fun theExampleIsBuiltFromTheLibraryAndReadsBack() {
        val example = WorkoutFormat.example(library.exercises)
        val read = (read(WorkoutJson.encode(example)) as ProgramParser.WorkoutResult.Valid).workout
        assertEquals(example, read)
        assertTrue(read.groups.size >= 2)
        // Every id is a visible library exercise.
        val visible = library.exercises.filter { !it.hidden }.map { it.id }.toSet()
        assertTrue(read.day().slots.all { it.exercise in visible })
    }
}
