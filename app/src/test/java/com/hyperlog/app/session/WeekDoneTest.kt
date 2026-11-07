package com.hyperlog.app.session

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
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

/** A session that closes a program week says so (#272). */
@RunWith(RobolectricTestRunner::class)
class WeekDoneTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000_000L

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, HyperlogDatabase::class.java).allowMainThreadQueries().build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context))
        programs = Programs(db, clock = { now })
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [
              { "id": "one", "name": "One", "weekStart": 1, "weekEnd": 1, "days": [ ${day("a", "Day A", "db_bench_press")}, ${day("b", "Day B", "db_lateral_raise")} ] },
              { "id": "two", "name": "Two", "weekStart": 2, "weekEnd": 2, "days": [ ${day("c", "Day A", "seated_db_shoulder_press")}, ${day("d", "Day B", "dumbbell_shoulder_press_standing")} ] } ] }
            """,
        )
    }

    /** Each day its own exercise, so none comes prefilled from another. */
    private fun day(id: String, name: String, exercise: String) = """{ "id": "$id", "name": "$name", "groups": [
        { "slots": [ { "id": "s$id", "exercise": "$exercise", "sets": [ { "count": 2, "reps": "8-10", "rir": 1, "rest": 90 } ] } ] } ] }"""

    @After
    fun close() = db.close()

    /** Trains the next day, two sets of it, and finishes. */
    private suspend fun trainNext(): SessionSummary {
        now += 86_400_000
        val logger = SessionLogger(db, clock = { now }, programs = programs).also {
            it.load()
            it.startDay(programs.active.first()!!, gymId = null)
        }
        repeat(2) {
            now += 60_000
            // After the first set the weight comes prefilled, with the cursor on the reps.
            if (logger.state.value?.entry?.field == Field.Weight) { logger.press(PadKey.Digit(2)); logger.press(PadKey.Digit(0)); logger.done() }
            logger.press(PadKey.Digit(8)); logger.done()
        }
        return logger.finish()!!
    }

    @Test
    fun aDayWithMoreOfItsWeekToComeFinishesNoWeek() = runTest {
        assertNull(trainNext().week)
    }

    @Test
    fun theLastDayOfAWeekFinishesIt() = runTest {
        trainNext()
        assertEquals(WeekDone(week = 1, days = 2, workSets = 4, lastWeekSets = null, streak = 1), trainNext().week)
    }

    /** Two sets on each of two days a week: the second week's card compares with the first. */
    @Test
    fun aWeekComparesWithTheOneBeforeAndCountsTheRun() = runTest {
        trainNext()
        val first = trainNext().week!!
        assertEquals(null, first.lastWeekSets)
        assertEquals(1, first.streak)
        trainNext()
        val second = trainNext().week!!
        assertEquals(4, second.lastWeekSets)
        assertEquals(2, second.streak)
    }

    @Test
    fun anAdHocSessionFinishesNoWeek() = runTest {
        val logger = SessionLogger(db, clock = { now }, programs = programs).also { it.load(); it.start() }
        logger.addExercise("leg_extension")
        logger.press(PadKey.Digit(6)); logger.done(); logger.press(PadKey.Digit(8)); logger.done()
        assertNull(logger.finish()!!.week)
    }
}
