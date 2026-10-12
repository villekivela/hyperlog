package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionLoggerTest {
    private lateinit var db: HyperlogDatabase
    private var now = 1_000_000L

    @Before
    fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = db.close()

    private suspend fun logger(): SessionLogger {
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        return SessionLogger(db, clock = { now }).also { it.load() }
    }

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private val SessionLogger.session get() = state.value!!

    @Test
    fun logsThreeSetsWithWeightFullAndPartialRepsAndRirUsingOnlyThePad() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("db_bench_press")

        logger.type("25"); logger.done()
        logger.type("10"); logger.selectRir(2); logger.done()
        // The next set comes prefilled from the last one: just ✓, after resting.
        now += 120_000
        logger.done()
        now += 120_000
        logger.type("9"); logger.partialMode(true); logger.type("4"); logger.selectRir(0); logger.done()

        val exercise = logger.session.exercises.single()
        assertEquals("DB Bench Press", exercise.exercise.name)
        assertEquals(
            listOf(LoggedSet(25.0, 10, 0, 2), LoggedSet(25.0, 10, 0, 2), LoggedSet(25.0, 9, 4, 0)),
            exercise.sets,
        )
    }

    @Test
    fun aReopenedLoggerRestoresTheSessionExactly() = runTest {
        val first = logger()
        first.start()
        first.addExercise("leg_extension")
        first.type("60"); first.done(); first.type("12"); first.selectRir(1); first.done()
        first.type("1"); first.partialMode(true); first.type("5")
        first.setPadVisible(false)

        val reopened = logger()

        assertEquals(first.session, reopened.session)
        val entry = reopened.session.entry!!
        assertEquals("1", entry.full)
        assertEquals("5", entry.partial)
        assertEquals(false, reopened.session.padVisible)
    }

    @Test
    fun theFirstSetStartsOnTheWeightAndLaterSetsFollowTheLastOne() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        assertEquals(Field.Weight, logger.session.entry!!.field)

        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()

        val entry = logger.session.entry!!
        assertEquals(Field.Reps, entry.field)
        assertEquals("60", entry.weight)
        assertEquals("12", entry.full)
        assertEquals(1, entry.rir)
    }

    @Test
    fun lastTimeComesFromThePreviousSessionWithThatExercise() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()
        logger.finish()

        now += 86_400_000
        logger.start()
        logger.addExercise("leg_extension")

        val exercise = logger.session.exercises.single()
        assertEquals(listOf(LoggedSet(60.0, 12, 0, 1)), exercise.lastTime)
        assertEquals(emptyList<LoggedSet>(), exercise.sets)
        assertEquals("60", logger.session.entry!!.weight)
    }

    @Test
    fun aSecondTapOnTheDoneKeyRightAfterLoggingLogsNothing() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("cable_lateral_raise")
        logger.type("7.5"); logger.done(); logger.type("12"); logger.selectRir(2); logger.done()

        // A stutter on ✓, 150 ms after the set was logged.
        now += 150
        logger.done()
        assertEquals(1, logger.session.current!!.sets.size)

        // A deliberate ✓ a moment later logs the next set as usual.
        now += 1_000
        logger.done()
        assertEquals(2, logger.session.current!!.sets.size)
    }

    @Test
    fun loggingASetStartsRest() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        assertNull(logger.session.restEndsAt)

        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()

        assertEquals(now + SessionLogger.DEFAULT_REST_MS, logger.session.restEndsAt)
    }

    @Test
    fun theSessionListSwitchesBetweenExercisesKeepingEachOnesSets() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()
        logger.addExercise("seated_leg_curl")
        assertEquals("seated_leg_curl", logger.session.current!!.exercise.id)

        logger.switchTo(logger.session.exercises.first().id)

        assertEquals("leg_extension", logger.session.current!!.exercise.id)
        assertEquals("60", logger.session.entry!!.weight)
    }

    @Test
    fun finishingEndsTheSessionAndSummarisesIt() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()
        logger.addExercise("seated_leg_curl")

        val summary = logger.finish()!!

        assertNull(logger.state.value)
        assertNull(logger().state.value)
        // Exercises with nothing logged are left out.
        assertEquals(listOf(ExerciseSummary("Leg Extension", listOf(LoggedSet(60.0, 12, 0, 1)))), summary.exercises)
    }

    @Test
    fun finishingWithNothingLoggedDiscardsTheSession() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")

        assertNull(logger.finish())
        assertNull(logger.state.value)
    }
}
