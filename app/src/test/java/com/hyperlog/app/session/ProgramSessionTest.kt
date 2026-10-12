package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.TestPrograms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Today runs the program's next day, and finishing it moves the position on. */
@RunWith(RobolectricTestRunner::class)
class ProgramSessionTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000_000L

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { now })
        programs.import(TestPrograms.twoDays("Test"))
    }

    @After
    fun close() = db.close()

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }

    private suspend fun SessionLogger.startNext() = startDay(programs.active.first()!!, gymId = null)

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private val SessionLogger.session get() = state.value!!

    /** Logs the active set as prefilled, after resting. */
    private suspend fun SessionLogger.logAsShown() {
        now += 200_000
        done()
    }

    @Test
    fun theNextDaysSlotsBecomeTheSessionsExercisesWithThisWeeksSets() = runTest {
        val logger = logger()
        logger.startNext()
        val session = logger.session
        assertEquals(listOf("barbell_bench_press", "cable_lateral_raise"), session.exercises.map { it.exercise.id })
        assertEquals("Day A", session.day!!.day.name)
        val bench = session.current!!
        assertEquals("bench", bench.slot!!.id)
        // Week 1 is the ramp-in: one RIR easier and no techniques.
        assertEquals(listOf(RepRange(4, 6), RepRange(6, 8), RepRange(6, 8)), bench.plan!!.map { it.reps })
        assertEquals(listOf(2, 2, 2), bench.plan!!.map { it.rir })
        assertNull(session.exercises[1].plan!!.single().technique)
        // No history: an empty weight, the top of the range and the target RIR.
        assertEquals("", session.entry!!.weight)
        assertEquals("6", session.entry!!.full)
        assertEquals(2, session.entry!!.rir)
    }

    @Test
    fun theBackOffsArePrefilledFromTheTopSetAndRestIsThePrograms() = runTest {
        val logger = logger()
        logger.startNext()
        logger.type("92.5"); logger.done()
        logger.done()
        assertEquals(LoggedSet(92.5, 6, 0, 2), logger.session.current!!.sets.single())
        assertEquals(now + 180_000, logger.session.restEndsAt)
        // 92.5 − 10% = 83.25, rounded down on the shared plates.
        assertEquals("82.5", logger.session.entry!!.weight)
        assertEquals("8", logger.session.entry!!.full)
    }

    @Test
    fun afterAnExercisesLastPlannedSetTheNextExerciseComesUp() = runTest {
        val logger = logger()
        logger.startNext()
        logger.type("80"); logger.done()
        logger.logAsShown(); logger.logAsShown(); logger.logAsShown()
        assertEquals("cable_lateral_raise", logger.session.current!!.exercise.id)
        logger.type("10"); logger.done()
        logger.logAsShown()
        // Every planned set is done: nothing left to log.
        assertNull(logger.session.entry)
    }

    @Test
    fun finishingTheDayMovesThePositionToTheNextDayOnce() = runTest {
        val logger = logger()
        logger.startNext()
        logger.type("80"); logger.done(); logger.logAsShown()
        val summary = logger.finish()!!
        assertEquals(Position(1, 1), programs.active.first()!!.position)

        // Finishing it again after a resume doesn't skip a day.
        logger.resume(summary.sessionId)
        logger.finish()
        assertEquals(Position(1, 1), programs.active.first()!!.position)

        logger.startNext()
        assertEquals("Day B", logger.session.day!!.day.name)
        assertEquals(listOf("leg_extension"), logger.session.exercises.map { it.exercise.id })
    }

    @Test
    fun aSessionWithNothingLoggedIsDiscardedAndTheDayStaysNext() = runTest {
        val logger = logger()
        logger.startNext()
        assertNull(logger.finish())
        assertEquals(Position(1, 0), programs.active.first()!!.position)
    }

    @Test
    fun theDayKeepsRunningAfterTheAppIsReopened() = runTest {
        logger().apply { startNext(); type("80"); done(); done() }
        val reopened = logger()
        assertEquals("Day A", reopened.session.day!!.day.name)
        // 80 − 10% = 72: the rung below on the shared plates is 70.
        assertEquals("70", reopened.session.entry!!.weight)
    }
}
