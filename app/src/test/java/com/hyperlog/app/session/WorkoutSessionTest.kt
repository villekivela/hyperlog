package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.coach.Workout
import com.hyperlog.app.coach.Workouts
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.history.History
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.TestPrograms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/**
 * A workout from the coach (SPEC, Coaching → Workout): it waits on Today for the day it was added,
 * and its session logs like a program day without moving the position.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutSessionTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private lateinit var workouts: Workouts
    private val zone = ZoneId.systemDefault()
    private val oct15 = LocalDate.of(2026, 10, 15)
    private var now = oct15.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private val hotel by lazy {
        val ids = db.exercises().let { kotlinx.coroutines.runBlocking { it.all() } }.map { it.id }.toSet()
        (ProgramParser.workout(
            """
            { "workout": { "name": "Hotel dumbbells, 45 min", "summary": "Bench and rows.", "groups": [
              { "slots": [ { "id": "press", "exercise": "barbell_bench_press", "sets": [ { "count": 2, "reps": "8-10", "rir": 2, "rest": 150 } ] } ] },
              { "slots": [ { "id": "row", "exercise": "chest_supported_db_row", "sets": [ { "count": 3, "reps": "10-12", "rir": 1 } ] } ] } ] } }
            """,
            ids,
        ) as ProgramParser.WorkoutResult.Valid).workout
    }

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { now })
        workouts = Workouts(db, clock = { now })
    }

    @After
    fun close() = db.close()

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private suspend fun SessionLogger.log(kg: String, reps: String) {
        now += 200_000
        focus(com.hyperlog.app.domain.Field.Weight); type(kg); done()
        type(reps); done()
    }

    private suspend fun today(): Workouts.Waiting? = workouts.waiting.first().firstOrNull { it.addedOn == programs.dateOf(now) }

    @Test
    fun anAddedWorkoutWaitsForTheDayItWasAddedOnly() = runTest {
        workouts.add(hotel, gymId = null)
        val waiting = today()!!
        assertEquals("Hotel dumbbells, 45 min", waiting.workout.name)
        assertEquals(oct15, waiting.addedOn)
        now += 24 * 3_600_000L
        assertNull(today())
    }

    @Test
    fun addingAnotherReplacesItAndRemoveDropsIt() = runTest {
        workouts.add(hotel, gymId = null)
        workouts.add(hotel.copy(name = "Legs at home"), gymId = null)
        assertEquals(listOf("Legs at home"), workouts.waiting.first().map { it.workout.name })
        workouts.remove(today()!!.id)
        assertEquals(emptyList<Workouts.Waiting>(), workouts.waiting.first())
    }

    @Test
    fun itsSessionLogsWithTargetsAndLeavesThePositionAlone() = runTest {
        programs.import(TestPrograms.twoDays("Test"))
        val before = programs.active.first()!!.position
        workouts.add(hotel, gymId = null)
        val logger = logger()
        logger.startWorkout(today()!!.id, gymId = null)
        // Started: it no longer waits on Today.
        assertNull(today())
        val session = logger.state.value!!
        assertNotNull(session.workoutId)
        assertEquals("Hotel dumbbells, 45 min", session.day!!.day.name)
        assertEquals(listOf("barbell_bench_press", "chest_supported_db_row"), session.exercises.map { it.exercise.id })
        val press = session.current!!
        assertEquals("press", press.slot!!.id)
        assertEquals(listOf(RepRange(8, 10), RepRange(8, 10)), press.prescription!!.map { it.reps })
        assertEquals(2, session.entry!!.rir)
        logger.log("80", "9")
        // Rest is the workout's.
        assertEquals(now + 150_000, logger.state.value!!.restEndsAt)
        logger.finish()
        assertEquals(before, programs.active.first()!!.position)
    }

    @Test
    fun itCountsTowardEstimatesWeeklySetsAndShowsInHistoryUnderItsName() = runTest {
        workouts.add(hotel, gymId = null)
        val logger = logger()
        logger.startWorkout(today()!!.id, gymId = null)
        logger.log("80", "10")
        logger.log("80", "10")
        logger.finish()

        val past = PastSessionEditor(db, programs).sessions().single()
        assertEquals("Hotel dumbbells, 45 min", past.title)
        val week = History(db, programs, zone, today = { oct15 }).weeklySets(1).single()
        assertEquals(2.0, week.sets["Chest"]!!, 0.0)

        // Next time the bench comes up, it builds on the workout's sets, with a suggestion.
        now += 2 * 24 * 3_600_000L
        workouts.add(hotel, gymId = null)
        logger.startWorkout(today()!!.id, gymId = null)
        val press = logger.state.value!!.current!!
        assertEquals(listOf(80.0, 80.0), press.lastTime.map { it.weightKg })
        assertTrue(press.suggestion != null)
    }

    @Test
    fun undoPutsARemovedWorkoutBackAsItWas() = runTest {
        val gym = com.hyperlog.app.gym.Gyms(db).addGym("Itis")
        val id = workouts.add(hotel, gymId = gym)
        val before = db.workouts().workout(id)
        val removed = workouts.remove(id)!!
        assertEquals(emptyList<Workouts.Waiting>(), workouts.waiting.first())
        now += 3_600_000
        workouts.restore(removed)
        assertEquals(before, db.workouts().workout(id))
        assertEquals(listOf(Workouts.Waiting(id, hotel, oct15, gym)), workouts.waiting.first())
    }

    @Test
    fun aStoredWorkoutReadsBackForItsSession() = runTest {
        val id = workouts.add(hotel, gymId = null)
        assertEquals(hotel, programs.workout(id))
        assertEquals(Position(1, 0), hotel.day().position)
    }
}
