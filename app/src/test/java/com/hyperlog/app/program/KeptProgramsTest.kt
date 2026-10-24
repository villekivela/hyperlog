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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Programs list (SPEC, Program → Programs): programs saved for later, started, run again and deleted. */
@RunWith(RobolectricTestRunner::class)
class KeptProgramsTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000L

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { now })
    }

    @After
    fun close() = db.close()

    @Test
    fun saveForLaterKeepsTheProgramAndLeavesTheRunningOneUntouched() = runTest {
        programs.import(TestPrograms.twoDays("Running"))
        val running = programs.active.first()!!
        programs.advance(running.id, Position(1, 0))
        now = 2_000L
        val result = programs.save(TestPrograms.twoDays("For later"))
        assertTrue(result.toString(), result is ProgramParser.Result.Valid)
        val active = programs.active.first()!!
        assertEquals(listOf("Running", Position(1, 1)), listOf(active.program.title, active.position))
        assertEquals(
            listOf("Running" to KeptProgram.Status.Running(Position(1, 1), finished = false), "For later" to KeptProgram.Status.Saved(2_000L)),
            programs.kept.first().map { it.title to it.status },
        )
    }

    private val yRaise = com.hyperlog.app.library.Exercise(
        "kneeling_cable_y_raise", "Kneeling Cable Y-Raise", com.hyperlog.app.library.Equipment.Cable,
        com.hyperlog.app.library.MovementPattern.LateralRaise, com.hyperlog.app.library.GripDemand.Low, listOf("Side Delts"), listOf("Traps"),
    )

    private val withYRaise = TestPrograms.twoDays("With Y-raise").replace("\"barbell_bench_press\"", "\"kneeling_cable_y_raise\"")

    private suspend fun inLibrary() = ExerciseLibrary(db).exercises.first().any { it.id == yRaise.id }

    @Test
    fun startingASavedProgramReplacesTheRunningOneFromWeekOneAndAddsItsNewExercises() = runTest {
        programs.import(TestPrograms.twoDays("Running"))
        val running = programs.active.first()!!
        programs.advance(running.id, Position(1, 0))
        now = 2_000L
        programs.save(withYRaise, listOf(yRaise))
        // Saved, its new exercise waits with it.
        assertEquals(false, inLibrary())
        val saved = programs.kept.first().first { it.title == "With Y-raise" }
        now = 3_000L
        val result = programs.start(saved.id)
        assertTrue(result.toString(), result is ProgramParser.Result.Valid)
        val active = programs.active.first()!!
        assertEquals(listOf(saved.id, Position(1, 0)), listOf(active.id, active.position))
        assertEquals(yRaise, ExerciseLibrary(db).exercises.first().single { it.id == yRaise.id })
        assertEquals(
            listOf(
                "With Y-raise" to KeptProgram.Status.Running(Position(1, 0), finished = false),
                "Running" to KeptProgram.Status.Past(startedAt = 1_000L, endedAt = 3_000L),
            ),
            programs.kept.first().map { it.title to it.status },
        )
    }

    @Test
    fun aSavedProgramWhoseNewExerciseTheLibraryGainedSinceShowsTheProblemsAndStartsNothing() = runTest {
        programs.import(TestPrograms.twoDays("Running"))
        programs.save(withYRaise, listOf(yRaise))
        ExerciseLibrary(db).addNew(listOf(yRaise.copy(name = "Y-Raise (mine)")))
        val saved = programs.kept.first().first { it.title == "With Y-raise" }
        val result = programs.start(saved.id)
        assertEquals(listOf("newExercises[0].id: the library already has \"kneeling_cable_y_raise\""), (result as ProgramParser.Result.Invalid).errors)
        assertEquals("Running", programs.active.first()!!.program.title)
        assertEquals(KeptProgram.Status.Saved(1_000L), programs.kept.first().first { it.id == saved.id }.status)
        assertEquals("Y-Raise (mine)", ExerciseLibrary(db).exercises.first().single { it.id == yRaise.id }.name)
    }

    /** A finished session of day [position] of program [id]. */
    private suspend fun trained(id: Long, position: Position): Long = db.sessions().insertSession(
        com.hyperlog.app.data.SessionEntity(startedAt = now, finishedAt = now + 1, programId = id, week = position.week, dayIndex = position.day),
    )

    @Test
    fun runAgainStartsAPastProgramFromWeekOneAndItsOldSessionsStayWithTheOldRun() = runTest {
        programs.import(TestPrograms.twoDays("First"))
        val first = programs.active.first()!!.id
        val session = trained(first, Position(1, 0))
        programs.advance(first, Position(1, 0))
        now = 2_000L
        programs.import(TestPrograms.twoDays("Second"))
        now = 3_000L
        val result = programs.runAgain(first)
        assertTrue(result.toString(), result is ProgramParser.Result.Valid)
        val active = programs.active.first()!!
        assertEquals(listOf("First", Position(1, 0)), listOf(active.program.title, active.position))
        assertTrue(active.id != first)
        // The old run keeps its session and its dates.
        assertEquals(first, db.sessions().session(session)!!.programId)
        assertEquals(
            listOf(
                "First" to KeptProgram.Status.Running(Position(1, 0), finished = false),
                "Second" to KeptProgram.Status.Past(startedAt = 2_000L, endedAt = 3_000L),
                "First" to KeptProgram.Status.Past(startedAt = 1_000L, endedAt = 2_000L),
            ),
            programs.kept.first().map { it.title to it.status },
        )
        assertEquals(listOf(false, false, true), programs.kept.first().map { it.trained })
    }

    @Test
    fun pastRunsListTheLastEndedFirst() = runTest {
        programs.import(TestPrograms.twoDays("First"))
        now = 2_000L
        programs.save(TestPrograms.twoDays("Saved early"))
        now = 3_000L
        programs.import(TestPrograms.twoDays("Second"))
        now = 4_000L
        programs.start(programs.kept.first().single { it.title == "Saved early" }.id)
        now = 5_000L
        programs.import(TestPrograms.twoDays("Third"))
        assertEquals(listOf("Third", "Saved early", "Second", "First"), programs.kept.first().map { it.title })
    }

    @Test
    fun deleteRemovesASavedProgramButNeverOneASessionTrained() = runTest {
        programs.import(TestPrograms.twoDays("First"))
        val first = programs.active.first()!!.id
        trained(first, Position(1, 0))
        programs.import(TestPrograms.twoDays("Second"))
        programs.save(TestPrograms.twoDays("Saved"))
        val saved = programs.kept.first().first { it.title == "Saved" }.id
        assertEquals(true, programs.delete(saved))
        assertEquals(false, programs.delete(first))
        assertEquals(listOf("Second", "First"), programs.kept.first().map { it.title })
    }
}
