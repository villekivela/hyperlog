package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Supersets and the session's order (SPEC, Supersets and session order). */
@RunWith(RobolectricTestRunner::class)
class SupersetSessionTest {
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
    }

    @After
    fun close() = db.close()

    private fun sets(count: Int, rest: Int, technique: String? = null) =
        """[ { "count": $count, "reps": "8-10", "rir": 1, "rest": $rest${technique?.let { ", \"technique\": $it" } ?: ""} } ]"""

    /**
     * Day A: 1 bench (2 sets, 3:00), 2a curl + 2b French press (2 sets each; the curl's
     * [curlTechnique]), 3 lateral raise (2 sets).
     */
    private suspend fun program(curlTechnique: String? = null) {
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 4, "days": [
              { "id": "a", "name": "Day A", "groups": [
                { "slots": [ { "id": "bench", "exercise": "db_bench_press", "sets": ${sets(2, 180)} } ] },
                { "type": "superset", "slots": [
                  { "id": "curl", "exercise": "db_bicep_curl", "sets": ${sets(2, 60, curlTechnique)} },
                  { "id": "french", "exercise": "db_french_press", "sets": ${sets(2, 90)} } ] },
                { "slots": [ { "id": "lateral", "exercise": "db_lateral_raise", "sets": ${sets(2, 60)} } ] } ] } ] } ] }
            """,
        )
    }

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also {
        it.load()
        it.startDay(programs.active.first()!!, gymId = null)
    }

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    /** Logs the active set at [kg] × 8, with a pause first so it is never taken for a double tap. */
    private suspend fun SessionLogger.log(kg: String) {
        now += 5_000
        type(kg); done()
        type("8"); done()
    }

    private val SessionLogger.session get() = state.value!!
    private val SessionLogger.onScreen get() = session.current!!.exercise.id

    private suspend fun SessionLogger.switchTo(exerciseId: String) =
        switchTo(session.exercises.first { it.exercise.id == exerciseId }.id)

    @Test
    fun loggingA1GoesStraightToA2AndRestStartsOnlyAfterA2() = runTest {
        program()
        val logger = logger()
        logger.log("20"); logger.log("20")
        assertEquals("db_bicep_curl", logger.onScreen)

        logger.log("15")
        assertEquals("db_french_press", logger.onScreen)
        assertNull(logger.session.restEndsAt)

        logger.log("10")
        // Full rest after the round, the French press's 1:30, and the curl's second set is next.
        assertEquals("db_bicep_curl", logger.onScreen)
        assertEquals(now + 90_000, logger.session.restEndsAt)
        assertEquals(1, logger.session.current!!.sets.size)

        logger.log("15")
        assertEquals("db_french_press", logger.onScreen)
        assertNull(logger.session.restEndsAt)
        logger.log("10")
        assertEquals("db_lateral_raise", logger.onScreen)
        assertEquals(now + 90_000, logger.session.restEndsAt)
    }

    @Test
    fun theSecondOfASupersetPairOpensOnItsFirstWarmUp() = runTest {
        program()
        val first = logger()
        first.log("20"); first.log("20")
        first.log("15"); first.log("10"); first.log("15"); first.log("10")
        first.log("8"); first.log("8")
        first.finish()
        now += 86_400_000

        val logger = logger()
        listOf(1, 2).forEach { now += 5_000; logger.closeWarmup(); logger.type("8"); logger.done() }
        assertEquals("db_bicep_curl", logger.onScreen)
        now += 5_000; logger.closeWarmup(); logger.type("8"); logger.done()
        // The curl's set 1 is logged: the French press comes up on its warm-up, not on set 1.
        assertEquals("db_french_press", logger.onScreen)
        assertEquals(0, logger.session.warmupEdit!!.index)
    }

    @Test
    fun anotherExerciseStartedFromTheListReturnsToTheSkippedOneWhenItIsDone() = runTest {
        program()
        val logger = logger()
        logger.log("20")
        // The bench is taken: the lateral raise now, and the bench's last set afterwards.
        logger.switchTo("db_lateral_raise")
        logger.log("8")
        assertEquals("db_lateral_raise", logger.onScreen)
        logger.log("8")
        assertEquals("db_bench_press", logger.onScreen)
        assertEquals(1, logger.session.current!!.sets.size)
        logger.log("20")
        assertEquals("db_bicep_curl", logger.onScreen)
    }

    @Test
    fun aSupersetStartedOutOfOrderKeepsItsPartnerUntilItsSetsAreDone() = runTest {
        program()
        val logger = logger()
        logger.switchTo("db_bicep_curl")
        logger.log("15")
        assertEquals("db_french_press", logger.onScreen)
        logger.log("10")
        assertEquals("db_bicep_curl", logger.onScreen)
        logger.log("15"); logger.log("10")
        // Round done: back to the bench, which was skipped.
        assertEquals("db_bench_press", logger.onScreen)
        logger.log("20"); logger.log("20")
        assertEquals("db_lateral_raise", logger.onScreen)
    }

    @Test
    fun endingAMyoRepSetEarlyInASupersetStartedOutOfOrderGoesToThePartnersSet() = runTest {
        program(curlTechnique = """{ "kind": "myo_reps", "miniSetReps": 4 }""")
        val logger = logger()
        logger.switchTo("db_bicep_curl")
        logger.log("15")
        assertEquals(FollowUp.MiniSet(index = 0, target = 4), logger.session.followUp)

        now += 5_000
        logger.finishSet()
        assertEquals("db_french_press", logger.onScreen)
        assertNull(logger.session.followUp)
        assertNull(logger.session.restEndsAt)
        assertEquals(0, logger.session.current!!.sets.size)
    }

    @Test
    fun leavingAnOpenSupersetSetForAnotherExerciseKeepsItsRest() = runTest {
        program(curlTechnique = """{ "kind": "myo_reps", "miniSetReps": 4 }""")
        val logger = logger()
        logger.switchTo("db_bicep_curl")
        logger.log("15")

        now += 5_000
        // Not the partner: the round is broken off, so the curl's 1:00 rest runs.
        logger.switchTo("db_lateral_raise")
        assertEquals("db_lateral_raise", logger.onScreen)
        assertNull(logger.session.followUp)
        assertEquals(now + 60_000, logger.session.restEndsAt)
        assertEquals(false, logger.session.breathing)
    }

    @Test
    fun theNextLineNamesWhatComesAfterTheGroupOnScreen() = runTest {
        program()
        val logger = logger()
        assertEquals("db_bicep_curl", logger.session.next!!.exercise.id)
        logger.log("20")
        logger.switchTo("db_french_press")
        // The partner isn't next; the bench, skipped, comes back after the superset.
        assertEquals("db_bench_press", logger.session.next!!.exercise.id)
        logger.switchTo("db_lateral_raise")
        assertEquals("db_bench_press", logger.session.next!!.exercise.id)
    }
}
