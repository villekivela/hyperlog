package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.history.LiftTrend
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The exercise on screen carries how it has moved, as Progress and the coach line read it (GLOSSARY, Lift trend). */
@RunWith(RobolectricTestRunner::class)
class LiftTrendSessionTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000_000L

    private val program = """
        { "format": 1, "title": "Bench", "blocks": [
          { "id": "build", "name": "Build", "weekStart": 1, "weekEnd": 8,
            "days": [
              { "id": "a", "name": "Day A", "groups": [ { "slots": [ { "id": "bench", "exercise": "barbell_bench_press",
                "sets": [ { "reps": "4-6", "rir": 1 }, { "reps": "6-8", "rir": 1, "backoff": {} } ] } ] } ] } ] } ] }
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

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private suspend fun SessionLogger.log(kg: String, reps: Int) {
        now += 200_000
        closeWarmup()
        focus(Field.Weight); type(kg); done()
        type("$reps"); if (state.value?.entry?.rir != 1) selectRir(1); done()
    }

    private suspend fun SessionLogger.trainDay(vararg sets: Pair<String, Int>) {
        now += 2 * 86_400_000L
        startDay(programs.active.first()!!, gymId = null)
        sets.forEach { (kg, reps) -> log(kg, reps) }
        finish()
    }

    private val SessionLogger.trend get() = state.value!!.current!!.trend

    @Test
    fun aLiftGoingUpSaysByHowMuch() = runTest {
        val logger = logger()
        logger.trainDay("80" to 5, "72.5" to 7)
        logger.trainDay("85" to 5, "75" to 7)

        now += 2 * 86_400_000L
        logger.startDay(programs.active.first()!!, gymId = null)
        val trend = logger.trend as LiftTrend.Moved
        assertEquals(true, trend.toKg > trend.fromKg)
    }

    @Test
    fun adHocItReadsTheSameSessions() = runTest {
        val logger = logger()
        logger.trainDay("80" to 5, "72.5" to 7)
        logger.trainDay("85" to 5, "75" to 7)

        now += 2 * 86_400_000L
        logger.start()
        logger.addExercise("barbell_bench_press")
        assertEquals(true, logger.trend is LiftTrend.Moved)
    }

    @Test
    fun oneSessionBeforeIsntEnough() = runTest {
        val logger = logger()
        logger.trainDay("80" to 5, "72.5" to 7)
        now += 2 * 86_400_000L
        logger.startDay(programs.active.first()!!, gymId = null)
        assertEquals(null, logger.trend)
    }
}
