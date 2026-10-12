package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.WarmupSet
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

/** Warm-ups and the plate calculator in a session (SPEC, Screens → Today). */
@RunWith(RobolectricTestRunner::class)
class WarmupSessionTest {
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
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 4, "days": [
              { "id": "a", "name": "Day A", "groups": [
                { "slots": [ { "id": "lat", "exercise": "db_lateral_raise", "sets": [ { "reps": "10-12", "rir": 1, "rest": 90 } ] } ] },
                { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [
                  { "reps": "4-6", "rir": 1, "rest": 180 }, { "count": 2, "reps": "6-8", "rir": 1, "rest": 180, "backoff": {} } ] } ] }
              ] } ] } ] }
            """,
        )
    }

    @After
    fun close() = db.close()

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private suspend fun SessionLogger.tick() {
        now += 5_000
        done()
    }

    private val SessionLogger.session get() = state.value!!

    /** Logs the lateral raise, so the bench comes up with its rest running. */
    private suspend fun benchUp(): SessionLogger {
        val logger = logger()
        logger.startDay(programs.active.first()!!, gymId = null)
        logger.type("10"); logger.done(); logger.type("12"); logger.selectRir(1); logger.tick()
        assertEquals("Barbell Bench Press", logger.session.current!!.exercise.name)
        return logger
    }

    @Test
    fun aNinetyKgBenchTopSetGetsARampOfWarmUpsOnThePlates() = runTest {
        val logger = benchUp()
        logger.type("90")
        assertEquals(
            listOf(WarmupSet(40.0, 8), WarmupSet(55.0, 5), WarmupSet(65.0, 3), WarmupSet(75.0, 2)),
            logger.session.warmups,
        )
        assertEquals(LadderSpec.DefaultPlates, logger.session.current!!.plates)
    }

    @Test
    fun tickingWarmUpsLeavesRestAndTheWorkSetsAlone() = runTest {
        val logger = benchUp()
        val rest = logger.session.restEndsAt!!
        logger.type("90"); logger.done()
        logger.tickWarmup(1)
        assertEquals(2, logger.session.current!!.warmupsDone)
        assertEquals(rest, logger.session.restEndsAt)
        assertEquals(emptyList<Any>(), logger.session.current!!.sets)
        assertEquals("90", logger.session.entry!!.weight)

        // Ticking the last ticked one takes it back; ticks survive the app being killed.
        logger.tickWarmup(1)
        assertEquals(1, logger.logger().session.current!!.warmupsDone)
    }

    @Test
    fun warmUpsGoOnceTheFirstWorkSetIsLoggedAndDontCountAsSets() = runTest {
        val logger = benchUp()
        logger.type("90"); logger.done(); logger.type("5")
        logger.tickWarmup(3)
        logger.tick()
        assertEquals(1, logger.session.current!!.sets.size)
        assertEquals(emptyList<WarmupSet>(), logger.session.warmups)
    }

    @Test
    fun dumbbellsHaveNoPlateCalculator() = runTest {
        val logger = logger()
        logger.startDay(programs.active.first()!!, gymId = null)
        assertNull(logger.session.current!!.plates)
    }

    private suspend fun SessionLogger.logger() = this@WarmupSessionTest.logger()
}
