package com.hyperlog.app.program

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgramsTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { 5_000L })
    }

    @After
    fun close() = db.close()

    @Test
    fun aValidProgramImportsAndStartsAtWeekOneDayOne() = runTest {
        val result = programs.import(TestPrograms.twoDays("First"))
        assertTrue(result is ProgramParser.Result.Valid)
        val active = programs.active.first()!!
        assertEquals("First", active.program.title)
        assertEquals(Position(1, 0), active.position)
        assertEquals("Day A", active.next!!.day.name)
    }

    @Test
    fun anInvalidProgramIsRejectedWithItsErrorsAndNothingIsImported() = runTest {
        programs.import(TestPrograms.twoDays("First"))
        val result = programs.import(TestPrograms.twoDays("Broken").replace("\"barbell_bench_press\"", "\"bb_bench\""))
        assertEquals(
            listOf("""blocks[0].days[0].groups[0].slots[0].exercise: unknown exercise "bb_bench""""),
            (result as ProgramParser.Result.Invalid).errors,
        )
        assertEquals("First", programs.active.first()!!.program.title)
    }

    @Test
    fun importingAnotherProgramReplacesTheOneRunningFromItsStart() = runTest {
        programs.import(TestPrograms.twoDays("First"))
        val first = programs.active.first()!!
        programs.advance(first.id, Position(1, 0))
        programs.import(TestPrograms.twoDays("Second"))
        val active = programs.active.first()!!
        assertEquals("Second", active.program.title)
        assertEquals(Position(1, 0), active.position)
    }

    @Test
    fun finishingADayMovesThePositionOnAndTheLastDayFinishesTheProgram() = runTest {
        programs.import(TestPrograms.twoDays("First"))
        val id = programs.active.first()!!.id
        programs.advance(id, Position(1, 0))
        assertEquals(Position(1, 1), programs.active.first()!!.position)
        // A day that isn't the next one (a resumed session finished again) doesn't move it.
        programs.advance(id, Position(1, 0))
        assertEquals(Position(1, 1), programs.active.first()!!.position)
        programs.advance(id, Position(1, 1))
        programs.advance(id, Position(2, 0))
        programs.advance(id, Position(2, 1))
        val done = programs.active.first()!!
        assertTrue(done.finished)
        assertNull(done.next)
    }
}

/** Program files for tests, with exercise ids from the shipped library. */
object TestPrograms {
    /** Two weeks of Day A (bench top set and back-offs, cable lateral raise) and Day B (leg extension). */
    fun twoDays(title: String) = """
        { "format": 1, "title": "$title", "blocks": [
          { "id": "main", "name": "Main", "weekStart": 1, "weekEnd": 2,
            "weeks": { "1": { "rirAdd": 1, "technique": null } },
            "days": [
              { "id": "a", "name": "Day A", "groups": [
                { "slots": [ { "id": "bench", "exercise": "barbell_bench_press",
                    "alternatives": ["smith_machine_bench_press"],
                    "note": "Same grip, setup and pause every rep.",
                    "sets": [ { "reps": "4-6", "rir": 1, "rest": 180 },
                              { "count": 2, "reps": "6-8", "rir": 1, "rest": 180, "backoff": {} } ] } ] },
                { "slots": [ { "id": "lateral", "exercise": "cable_lateral_raise",
                    "sets": [ { "reps": "10-12", "rir": 0, "rest": 90,
                                "technique": { "kind": "myo_reps", "miniSetReps": 4 } } ] } ] } ] },
              { "id": "b", "name": "Day B", "groups": [
                { "slots": [ { "id": "extension", "exercise": "leg_extension", "sets": [ { "count": 2, "reps": "10-12", "rir": 0 } ] } ] } ] } ] } ] }
    """
}
