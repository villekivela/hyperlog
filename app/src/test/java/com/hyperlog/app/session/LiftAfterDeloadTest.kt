package com.hyperlog.app.session

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A lift after a deload isn't up on the deload: it compares with the last session that wasn't one (round 4). */
@RunWith(RobolectricTestRunner::class)
class LiftAfterDeloadTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000_000_000L

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, HyperlogDatabase::class.java).allowMainThreadQueries().build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(context))
        programs = Programs(db, clock = { now })
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 3,
              "weeks": { "2": { "deload": true } },
              "days": [ { "id": "a", "name": "Day A", "groups": [
                { "slots": [ { "id": "bench", "exercise": "db_bench_press", "sets": [ { "count": 2, "reps": "8-10", "rir": 1, "rest": 90 } ] } ] } ] } ] } ] }
            """,
        )
    }

    @After
    fun close() = db.close()

    /** The program's week [week] in the books at [kg], and the program moved on past it. */
    private suspend fun trained(week: Int, kg: Double) {
        val program = programs.active.first()!!
        now += 86_400_000
        val dao = db.sessions()
        val session = dao.insertSession(SessionEntity(startedAt = now, finishedAt = now + 3_600_000, programId = program.id, week = week, dayIndex = 0))
        val exercise = dao.insertSessionExercise(SessionExerciseEntity(sessionId = session, exerciseId = "db_bench_press", position = 0, slotId = "bench"))
        dao.insertSet(LoggedSetEntity(sessionExerciseId = exercise, position = 0, weightKg = kg, fullReps = 8, partialReps = 0, rir = 1, loggedAt = now))
        programs.advance(program.id, Position(week, 0))
    }

    private suspend fun current(): SessionExercise {
        now += 86_400_000
        val logger = SessionLogger(db, clock = { now }, programs = programs).also {
            it.load()
            it.startDay(programs.active.first()!!, gymId = null)
        }
        return logger.state.value!!.current!!
    }

    @Test
    fun aLiftAfterADeloadComparesWithTheSessionBeforeIt() = runTest {
        trained(1, 30.0)
        trained(2, 18.0)
        assertEquals(30.0, current().summary.lastTop?.weightKg)
    }

    @Test
    fun aLiftWithNoDeloadBehindItComparesWithLastTime() = runTest {
        trained(1, 30.0)
        assertEquals(30.0, current().summary.lastTop?.weightKg)
    }

    /** Nothing before the deload: there is no honest comparison, so none is made. */
    @Test
    fun aDeloadWithNothingBeforeItComparesWithNothing() = runTest {
        programs.advance(programs.active.first()!!.id, Position(1, 0))
        trained(2, 18.0)
        assertEquals(null, current().summary.lastTop)
    }
}
