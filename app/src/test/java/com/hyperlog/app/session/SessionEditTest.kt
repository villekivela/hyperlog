package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.RepRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Editing today's session on the fly (SPEC, Today → Edit on the fly). */
@RunWith(RobolectricTestRunner::class)
class SessionEditTest {
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
        // One day a week, so the next session is the same day again.
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 4, "days": [
              { "id": "a", "name": "Day A", "groups": [
                { "slots": [ { "id": "bench", "exercise": "barbell_bench_press",
                  "alternatives": [ "db_bench_press", { "exercise": "floor_press", "sets": [ { "count": 3, "reps": 5, "rir": 2 } ] } ],
                  "sets": [ { "count": 2, "reps": "6-8", "rir": 1, "rest": 180 } ] } ] },
                { "type": "superset", "slots": [
                  { "id": "curl", "exercise": "db_bicep_curl", "sets": [ { "count": 2, "reps": "8-10", "rir": 1, "rest": 60 } ] },
                  { "id": "french", "exercise": "db_french_press", "sets": [ { "count": 2, "reps": "8-10", "rir": 1, "rest": 90 } ] } ] },
                { "slots": [ { "id": "lateral", "exercise": "db_lateral_raise", "sets": [ { "count": 2, "reps": "10-12", "rir": 0, "rest": 60 } ] } ] } ] } ] } ] }
            """,
        )
    }

    @After
    fun close() = db.close()

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
    private fun SessionLogger.exercise(id: String) = session.exercises.first { it.exercise.id == id }

    @Test
    fun aSwappedSlotIsLoggedAsTheAlternativeAndTheNextSessionHasTheProgramsExercise() = runTest {
        val logger = logger()
        val bench = logger.session.current!!
        logger.swap(bench.id, "db_bench_press")

        val swapped = logger.session.current!!
        assertEquals("db_bench_press", swapped.exercise.id)
        assertEquals("bench", swapped.slot!!.id)
        assertEquals(listOf(RepRange(6, 8), RepRange(6, 8)), swapped.prescription!!.map { it.reps })
        logger.log("30"); logger.log("30")
        assertEquals(listOf(30.0, 30.0), logger.exercise("db_bench_press").sets.map { it.weightKg })
        logger.finish()

        val next = logger()
        assertEquals("barbell_bench_press", next.onScreen)
        assertEquals(2, next.session.current!!.prescription!!.size)
    }

    @Test
    fun anAlternativeWithItsOwnSetsBringsThem() = runTest {
        val logger = logger()
        logger.swap(logger.session.current!!.id, "floor_press")
        assertEquals(listOf(RepRange(5, 5), RepRange(5, 5), RepRange(5, 5)), logger.session.current!!.prescription!!.map { it.reps })
    }

    @Test
    fun aSwappedSupersetPartnerKeepsItsPartner() = runTest {
        val logger = logger()
        logger.swap(logger.exercise("db_french_press").id, "overhead_db_triceps_extension")
        logger.switchTo(logger.exercise("db_bicep_curl").id)
        logger.log("15")
        assertEquals("overhead_db_triceps_extension", logger.onScreen)
    }

    @Test
    fun anExerciseWithASetLoggedCantBeSwapped() = runTest {
        val logger = logger()
        logger.log("60")
        logger.swap(logger.session.current!!.id, "db_bench_press")
        assertEquals("barbell_bench_press", logger.onScreen)
    }

    @Test
    fun anAddedSetIsLikeTheLastPlannedOneAndTheExerciseIsDoneAfterIt() = runTest {
        val logger = logger()
        logger.addSet(logger.session.current!!.id)
        assertEquals(3, logger.session.current!!.prescription!!.size)
        assertEquals(SetPrescription(RepRange(6, 8), 1, 180), logger.session.current!!.prescription!![2])
        logger.log("60"); logger.log("60")
        assertEquals("barbell_bench_press", logger.onScreen)
        logger.log("60")
        assertEquals("db_bicep_curl", logger.onScreen)
    }

    @Test
    fun aSetAddedToAFinishedExerciseBringsItBack() = runTest {
        val logger = logger()
        logger.log("60"); logger.log("60")
        assertEquals("db_bicep_curl", logger.onScreen)
        logger.addSet(logger.exercise("barbell_bench_press").id)
        assertEquals(false, logger.exercise("barbell_bench_press").done)
        assertEquals("barbell_bench_press", logger.session.next!!.exercise.id)
    }

    @Test
    fun aPlannedSetCanBeRemovedButNotALoggedOneOrTheLastOne() = runTest {
        val logger = logger()
        val bench = logger.session.current!!.id
        logger.removeSet(bench, 1)
        assertEquals(1, logger.session.current!!.prescription!!.size)
        logger.removeSet(bench, 0)
        assertEquals(1, logger.session.current!!.prescription!!.size)
        logger.addSet(bench)
        logger.log("60")
        logger.removeSet(bench, 0)
        assertEquals(2, logger.session.current!!.prescription!!.size)
        logger.removeSet(bench, 1)
        // Its last planned set is gone, so the session moves on.
        assertEquals(1, logger.exercise("barbell_bench_press").prescription!!.size)
        assertEquals("db_bicep_curl", logger.onScreen)
    }

    @Test
    fun aSetsTargetRepsAndRirCanChangeAndTheActiveSetTakesTheRirFromIt() = runTest {
        val logger = logger()
        val bench = logger.session.current!!.id
        logger.setTarget(bench, 0, RepRange(3, 5), rir = 2)
        val current = logger.session.current!!
        assertEquals(SetPrescription(RepRange(3, 5), 2, 180), current.prescription!![0])
        assertEquals(SetPrescription(RepRange(6, 8), 1, 180), current.prescription!![1])
        assertEquals("", logger.session.entry!!.full)
        assertEquals(2, logger.session.entry!!.rir)
    }

    @Test
    fun editsSurviveTheAppBeingKilled() = runTest {
        val logger = logger()
        val bench = logger.session.current!!.id
        logger.addSet(bench)
        logger.setTarget(bench, 2, RepRange(10, 12), rir = 0)
        logger.swap(logger.exercise("db_lateral_raise").id, "cable_lateral_raise")

        val again = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        assertEquals(SetPrescription(RepRange(10, 12), 0, 180), again.session.current!!.prescription!![2])
        assertEquals("cable_lateral_raise", again.session.exercises.last().exercise.id)
    }

    @Test
    fun aRemovedExerciseLeavesTheSessionButNotTheProgram() = runTest {
        val logger = logger()
        logger.removeExercise(logger.exercise("db_lateral_raise").id)
        assertEquals(listOf("barbell_bench_press", "db_bicep_curl", "db_french_press"), logger.session.exercises.map { it.exercise.id })
        logger.log("60"); logger.log("60")
        logger.log("15"); logger.log("10"); logger.log("15"); logger.log("10")
        assertEquals(true, logger.session.exercises.all { it.done })
        logger.finish()
        assertEquals("db_lateral_raise", logger().session.exercises.last().exercise.id)
    }

    @Test
    fun removingTheExerciseOnScreenBringsUpTheNextOne() = runTest {
        val logger = logger()
        logger.removeExercise(logger.session.current!!.id)
        assertEquals("db_bicep_curl", logger.onScreen)
    }

    @Test
    fun anExerciseWithASetLoggedIsntRemoved() = runTest {
        val logger = logger()
        logger.log("60")
        logger.removeExercise(logger.session.current!!.id)
        assertEquals(4, logger.session.exercises.size)
    }

    @Test
    fun exercisesMoveInTheSessionsOrderAndASupersetMovesAsAPair() = runTest {
        val logger = logger()
        logger.move(logger.exercise("db_lateral_raise").id, -1)
        assertEquals(
            listOf("barbell_bench_press", "db_lateral_raise", "db_bicep_curl", "db_french_press"),
            logger.session.exercises.map { it.exercise.id },
        )
        logger.move(logger.exercise("db_french_press").id, -1)
        assertEquals(
            listOf("barbell_bench_press", "db_bicep_curl", "db_french_press", "db_lateral_raise"),
            logger.session.exercises.map { it.exercise.id },
        )
        logger.move(logger.exercise("barbell_bench_press").id, 1)
        assertEquals(
            listOf("db_bicep_curl", "db_french_press", "barbell_bench_press", "db_lateral_raise"),
            logger.session.exercises.map { it.exercise.id },
        )
        // The session follows the new order: the bench's sets lead on to the lateral raise.
        logger.switchTo(logger.exercise("barbell_bench_press").id)
        logger.log("60"); logger.log("60")
        assertEquals("db_bicep_curl", logger.onScreen)
    }

    @Test
    fun anExerciseAddedToAProgramDayHasOpenEndedSets() = runTest {
        val logger = logger()
        logger.addExercise("cable_crunch")
        val added = logger.session.current!!
        assertEquals("cable_crunch", added.exercise.id)
        assertEquals(null, added.slot)
        // With no history the first set needs its RIR; later ones take the set before's.
        logger.selectRir(1)
        logger.log("30"); logger.log("30"); logger.log("30")
        assertEquals(3, logger.session.current!!.sets.size)
        assertEquals("cable_crunch", logger.onScreen)
    }
}
