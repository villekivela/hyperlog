package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * "Last time" in a program session is the session progression judges (same slot and rep range),
 * so the Last column, the first set's prefill and the why-line name the same sets (SPEC, Automatic
 * progressive overload). Ad hoc it is the latest session.
 */
@RunWith(RobolectricTestRunner::class)
class LastTimeSessionTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000_000L

    /** Day A benches heavy (4–6, then 6–8), Day B for volume (6–8, then 8–10), Day C a 10–12 it never did. */
    private val program = """
        { "format": 1, "title": "Two benches", "blocks": [
          { "id": "build", "name": "Build", "weekStart": 1, "weekEnd": 4,
            "days": [
              { "id": "a", "name": "Day A", "groups": [ { "slots": [ { "id": "bench", "exercise": "barbell_bench_press",
                "sets": [ { "reps": "4-6", "rir": 1 }, { "reps": "6-8", "rir": 1, "backoff": {} } ] } ] } ] },
              { "id": "b", "name": "Day B", "groups": [ { "slots": [ { "id": "bench_volume", "exercise": "barbell_bench_press",
                "sets": [ { "reps": "6-8", "rir": 1 }, { "reps": "8-10", "rir": 1, "backoff": {} } ] } ] } ] },
              { "id": "c", "name": "Day C", "groups": [ { "slots": [ { "id": "bench_pump", "exercise": "barbell_bench_press",
                "sets": [ { "reps": "10-12", "rir": 1 } ] } ] } ] } ] } ] }
    """

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { now })
        programs.import(program)
    }

    @After
    fun close() = db.close()

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }

    private val SessionLogger.session get() = state.value!!

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private suspend fun SessionLogger.log(kg: String, reps: Int, rir: Int) {
        now += 200_000
        focus(Field.Weight); type(kg); done()
        type("$reps"); selectRir(rir); done()
    }

    private suspend fun SessionLogger.trainDay(vararg sets: Pair<String, Int>) {
        now += 2 * 86_400_000L
        startDay(programs.active.first()!!, gymId = null)
        sets.forEach { (kg, reps) -> log(kg, reps, 1) }
        finish()
    }

    @Test
    fun dayAsBenchShowsDayAsSetsNotDayBsThatCameAfter() = runTest {
        val logger = logger()
        logger.trainDay("80" to 5, "72.5" to 7)
        logger.trainDay("70" to 8, "62.5" to 10)
        logger.trainDay("60" to 12)

        // Day A again: its last time is Day A's 4–6 session, though Days B and C came after.
        now += 2 * 86_400_000L
        logger.startDay(programs.active.first()!!, gymId = null)
        val bench = logger.session.current!!
        assertEquals(listOf(LoggedSet(80.0, 5, 0, 1), LoggedSet(72.5, 7, 0, 1)), bench.lastTime)
        assertTrue(bench.suggestion!!.why!!, bench.suggestion!!.why!!.startsWith("Last time 80 kg × 5 @ 1"))
        assertEquals(bench.suggestion!!.kg, logger.session.entry!!.weightKg!!, 0.0)
    }

    @Test
    fun aRepRangeNeverDoneFallsBackToTheLatestSession() = runTest {
        val logger = logger()
        logger.trainDay("80" to 5, "72.5" to 7)
        logger.trainDay("70" to 8, "62.5" to 10)

        // Day C's 10–12 has no session of its own: Last shows the latest one, Day B's.
        now += 2 * 86_400_000L
        logger.startDay(programs.active.first()!!, gymId = null)
        assertEquals(listOf(70.0, 62.5), logger.session.current!!.lastTime.map { it.weightKg })
    }

    @Test
    fun adHocTheLatestSessionIsLastTime() = runTest {
        val logger = logger()
        logger.trainDay("80" to 5, "72.5" to 7)
        logger.trainDay("70" to 8, "62.5" to 10)

        now += 2 * 86_400_000L
        logger.start()
        logger.addExercise("barbell_bench_press")
        assertEquals(listOf(70.0, 62.5), logger.session.current!!.lastTime.map { it.weightKg })
    }
}
