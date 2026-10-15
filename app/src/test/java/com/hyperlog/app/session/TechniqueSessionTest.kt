package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.Technique
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Drop sets, myo-reps, lengthened partials and iso holds as a session logs them (SPEC, Intensity techniques). */
@RunWith(RobolectricTestRunner::class)
class TechniqueSessionTest {
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

    /** A one-day program whose only slot is [exercise] with [sets] (set lines as JSON). */
    private suspend fun program(exercise: String, sets: String) {
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 4, "days": [
              { "id": "a", "name": "Day A", "groups": [ { "slots": [ { "id": "s", "exercise": "$exercise", "sets": $sets } ] } ] } ] } ] }
            """,
        )
    }

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }

    private suspend fun SessionLogger.startNext() = startDay(programs.active.first()!!, gymId = null)

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    /** ✓ after a pause, so it is never taken for a double tap. */
    private suspend fun SessionLogger.tick() {
        now += 5_000
        done()
    }

    private val SessionLogger.session get() = state.value!!

    private val dropSet = """[ { "reps": "8-10", "rir": 1, "rest": 120, "technique": { "kind": "dropset", "drops": 2 } } ]"""

    @Test
    fun aDropSetLogsItsDropsWithNoRestThenRestsAfterTheLastDrop() = runTest {
        program("db_bench_press", dropSet)
        val logger = logger()
        logger.startNext()
        assertEquals(Technique.Dropset(drops = 2), logger.session.current!!.techniqueAt(0))
        logger.type("25"); logger.done()
        logger.type("9"); logger.tick()

        // The set is logged, rest doesn't start, and the first drop comes up: 25 − 25% on the dumbbells.
        assertNull(logger.session.restEndsAt)
        assertEquals(FollowUp.Drop(index = 0, count = 2), logger.session.followUp)
        assertEquals("17.5", logger.session.entry!!.weight)
        assertEquals("6", logger.session.entry!!.full)
        assertNull(logger.session.entry!!.rir)

        logger.type("7"); logger.tick()
        assertNull(logger.session.restEndsAt)
        assertEquals(FollowUp.Drop(index = 1, count = 2), logger.session.followUp)
        assertEquals("12.5", logger.session.entry!!.weight)
        logger.tick()

        // After the last drop the set closes and the set's rest starts.
        assertNull(logger.session.followUp)
        assertEquals(now + 120_000, logger.session.restEndsAt)
        val set = logger.session.current!!.sets.single()
        assertEquals(listOf(Extension(Extension.Kind.Drop, 17.5, 7), Extension(Extension.Kind.Drop, 12.5, 6)), set.extensions)
        assertEquals(Technique.Dropset(drops = 2), set.technique)
    }

    @Test
    fun dropRepsArePrefilledFromLastTimesDrops() = runTest {
        program("db_bench_press", dropSet)
        logger().apply {
            startNext(); type("25"); done(); type("9"); tick()
            type("8"); tick(); type("5"); tick()
            finish()
        }
        val logger = logger()
        logger.startNext()
        logger.type("9"); logger.tick()
        assertEquals("8", logger.session.entry!!.full)
        logger.tick()
        assertEquals("5", logger.session.entry!!.full)
        assertEquals(listOf(Extension(Extension.Kind.Drop, 17.5, 8), Extension(Extension.Kind.Drop, 12.5, 5)), logger.session.current!!.lastTime.single().extensions)
    }

    private val myo = """[ { "reps": "10-12", "rir": 0, "rest": 90, "technique": { "kind": "myo_reps", "miniSetReps": 4, "maxMiniSets": 3 } } ]"""

    @Test
    fun myoRepsRunActivationThenMiniSetsWithABreathingCountdownAndStopOnAMiss() = runTest {
        program("leg_extension", myo)
        val logger = logger()
        logger.startNext()
        logger.type("60"); logger.done(); logger.type("12"); logger.tick()

        // The activation set is logged; a 15 s breathing pause comes before the first mini-set.
        assertEquals(FollowUp.MiniSet(index = 0, max = 3, target = 4), logger.session.followUp)
        assertEquals(true, logger.session.breathing)
        assertEquals(now + 15_000, logger.session.restEndsAt)
        assertEquals("60", logger.session.entry!!.weight)
        assertEquals("4", logger.session.entry!!.full)
        assertNull(logger.session.entry!!.rir)
        // Mini-sets are done at the activation set's weight.
        logger.focus(com.hyperlog.app.domain.Field.Weight)
        assertEquals(com.hyperlog.app.domain.Field.Reps, logger.session.entry!!.field)

        logger.tick()
        assertEquals(FollowUp.MiniSet(index = 1, max = 3, target = 4), logger.session.followUp)
        assertEquals(now + 15_000, logger.session.restEndsAt)

        // A mini-set short of its target ends the set, and full rest starts.
        logger.press(PadKey.Backspace); logger.type("3"); logger.tick()
        assertNull(logger.session.followUp)
        assertEquals(false, logger.session.breathing)
        assertEquals(now + 90_000, logger.session.restEndsAt)
        assertEquals(
            listOf(Extension(Extension.Kind.MiniSet, 60.0, 4), Extension(Extension.Kind.MiniSet, 60.0, 3)),
            logger.session.current!!.sets.single().extensions,
        )
    }

    @Test
    fun aMiniSetTypedShortOfItsTargetClosesTheSet() = runTest {
        program("leg_extension", myo)
        val logger = logger()
        logger.startNext()
        logger.type("60"); logger.done(); logger.type("12"); logger.tick()
        logger.tick()
        // The mini-set's reps are typed straight over the prefilled target, with no backspace first.
        logger.type("3"); logger.tick()
        assertNull(logger.session.followUp)
        assertEquals(listOf(4, 3), logger.session.current!!.sets.single().extensions.map { it.reps })
    }

    @Test
    fun aFirstMiniSetShortOfItsTargetClosesTheSet() = runTest {
        program("leg_extension", myo)
        val logger = logger()
        logger.startNext()
        logger.type("60"); logger.done(); logger.type("12"); logger.tick()
        logger.type("2"); logger.tick()
        assertNull(logger.session.followUp)
        assertEquals(listOf(2), logger.session.current!!.sets.single().extensions.map { it.reps })
    }

    @Test
    fun myoRepsStopAtTheMostMiniSetsAllowed() = runTest {
        program("leg_extension", myo)
        val logger = logger()
        logger.startNext()
        logger.type("60"); logger.done(); logger.type("12"); logger.tick()
        logger.tick(); logger.tick(); logger.tick()
        assertNull(logger.session.followUp)
        assertEquals(3, logger.session.current!!.sets.single().extensions.size)
    }

    @Test
    fun lengthenedPartialsLogAsFullPlusPartialRepsAndNeverMoveProgression() = runTest {
        program("leg_extension", """[ { "reps": "10-12", "rir": 0, "rest": 90, "technique": { "kind": "lengthened_partials" } } ]""")
        logger().apply {
            startNext()
            type("60"); done(); type("10")
            // The first ✓ moves to the partials instead of logging.
            tick()
            assertEquals(true, session.entry!!.partialMode)
            assertEquals(0, session.current!!.sets.size)
            type("6"); tick()
            assertEquals(listOf(10 to 6), session.current!!.sets.map { it.fullReps to it.partialReps })
            finish()
        }
        // 10 full reps @ 0 against 10–12: the weight stays. Counting the 6 partials would move it up.
        val next = logger().apply { startNext() }.session.current!!
        assertEquals(60.0, next.suggestion!!.kg, 0.001)
    }

    @Test
    fun aTargetPartialCountIsPrefilledWhenThePartialsComeUp() = runTest {
        program("leg_extension", """[ { "reps": "10-12", "rir": 0, "technique": { "kind": "lengthened_partials", "partials": 6 } } ]""")
        val logger = logger()
        logger.startNext()
        logger.type("60"); logger.done(); logger.type("12"); logger.tick()
        assertEquals("6", logger.session.entry!!.partial)
        logger.tick()
        assertEquals(12 to 6, logger.session.current!!.sets.single().let { it.fullReps to it.partialReps })
    }

    @Test
    fun withThePadHiddenTickLogsALengthenedPartialsSetAsShown() = runTest {
        // The hidden pad's ✓ logs the set as shown: there is no pad to type the partials into.
        program("leg_extension", """[ { "reps": "10-12", "rir": 0, "technique": { "kind": "lengthened_partials", "partials": 6 } } ]""")
        logger().apply {
            startNext()
            type("60"); done(); type("12")
            setPadVisible(false)
            tick()
            assertEquals(listOf(12 to 6), session.current!!.sets.map { it.fullReps to it.partialReps })
        }
    }

    @Test
    fun withThePadHiddenTickLogsPartialsToFailureWithNone() = runTest {
        program("leg_extension", """[ { "reps": "10-12", "rir": 0, "technique": { "kind": "lengthened_partials" } } ]""")
        logger().apply {
            startNext()
            type("60"); done(); type("12")
            setPadVisible(false)
            tick()
            assertEquals(listOf(12 to 0), session.current!!.sets.map { it.fullReps to it.partialReps })
        }
    }

    @Test
    fun anIsoHoldFollowsTheSetAndItsTimerRecordsTheSecondsHeld() = runTest {
        program("leg_extension", """[ { "reps": "10-12", "rir": 0, "technique": { "kind": "iso_hold", "seconds": 20, "position": "stretch" } } ]""")
        val logger = logger()
        logger.startNext()
        logger.type("60"); logger.done(); logger.type("12"); logger.tick()
        assertEquals(FollowUp.Hold(20, com.hyperlog.app.program.HoldPosition.Stretch), logger.session.followUp)
        assertNull(logger.session.restEndsAt)
        assertEquals("20", logger.session.entry!!.full)

        logger.startHold()
        now += 23_400
        // ✓ stops the timer and logs the whole seconds held.
        logger.done()
        assertNull(logger.session.followUp)
        assertNull(logger.session.holdStartedAt)
        assertEquals(listOf(Extension(Extension.Kind.Hold, 60.0, 23)), logger.session.current!!.sets.single().extensions)
        assertEquals(now + 120_000, logger.session.restEndsAt)
    }

    @Test
    fun aSetCanBeSwitchedToADropSetMidSession() = runTest {
        program("db_bench_press", """[ { "count": 2, "reps": "8-10", "rir": 1 } ]""")
        val logger = logger()
        logger.startNext()
        logger.type("25"); logger.done(); logger.type("10"); logger.tick()
        assertNull(logger.session.followUp)

        logger.setTechnique(1, Technique.Dropset())
        assertEquals(Technique.Dropset(), logger.session.current!!.techniqueAt(1))
        // The change is saved with the session.
        assertEquals(Technique.Dropset(), logger().session.current!!.techniqueAt(1))

        now += 120_000
        logger.type("10"); logger.done()
        assertEquals(FollowUp.Drop(0, 2), logger.session.followUp)
        assertEquals("17.5", logger.session.entry!!.weight)
        // A logged set keeps what it was done with.
        logger.setTechnique(0, Technique.Failure)
        assertNull(logger.session.current!!.techniqueAt(0))
    }

    @Test
    fun switchingASetToFailureKeepsItsRirAtZeroEvenWhenItAlreadyWas() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("db_bench_press")
        logger.selectRir(0)
        logger.setTechnique(0, Technique.Failure)
        assertEquals(0, logger.session.entry!!.rir)
    }

    @Test
    fun aProgramsTechniqueCanBeTakenOffASetAndAnAdHocSetCanGetOne() = runTest {
        program("db_bench_press", dropSet)
        val logger = logger()
        logger.startNext()
        logger.setTechnique(0, null)
        logger.type("25"); logger.done(); logger.type("9"); logger.tick()
        assertNull(logger.session.followUp)
        logger.finish()

        logger.start()
        logger.addExercise("db_bench_press")
        logger.setTechnique(0, Technique.Failure)
        assertEquals(0, logger.session.entry!!.rir)
        logger.setTechnique(0, Technique.MyoReps(miniSetReps = 3))
        logger.type("9"); logger.tick()
        assertEquals(FollowUp.MiniSet(0, 5, 3), logger.session.followUp)
    }

    @Test
    fun aUnilateralSetCanBeSplitIntoLeftAndRightAndTheNextSetStaysSplit() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("single_arm_db_row")
        assertEquals(true, logger.session.current!!.exercise.unilateral)
        logger.type("30"); logger.done(); logger.type("10"); logger.selectRir(1)
        logger.split(true)
        logger.done()
        logger.type("9"); logger.tick()
        assertEquals(com.hyperlog.app.domain.LoggedSet(30.0, 10, 0, 1, rightReps = 9), logger.session.current!!.sets.single())
        assertEquals("9", logger.session.entry!!.right)
        // Exercises done with both sides at once can't be split.
        logger.addExercise("db_bench_press")
        assertEquals(false, logger.session.current!!.exercise.unilateral)
        logger.split(true)
        assertNull(logger.session.entry!!.right)
    }

    @Test
    fun finishSetClosesADropSetEarly() = runTest {
        program("db_bench_press", dropSet)
        val logger = logger()
        logger.startNext()
        logger.type("25"); logger.done(); logger.type("9"); logger.tick()
        logger.tick()
        logger.finishSet()
        assertNull(logger.session.followUp)
        assertEquals(1, logger.session.current!!.sets.single().extensions.size)
        assertEquals(now + 120_000, logger.session.restEndsAt)
    }

    @Test
    fun plusAddsADropToADropSetAlreadyUnderWay() = runTest {
        program("db_bench_press", dropSet)
        val logger = logger()
        logger.startNext()
        logger.type("25"); logger.done(); logger.type("9"); logger.tick()
        logger.tick()
        assertEquals(FollowUp.Drop(index = 1, count = 2), logger.session.followUp)
        logger.addDropOrMiniSet(0)
        assertEquals(FollowUp.Drop(index = 1, count = 3), logger.session.followUp)
        logger.tick(); logger.tick()
        // The third drop is the last: the set closes and rest starts.
        assertNull(logger.session.followUp)
        assertEquals(now + 120_000, logger.session.restEndsAt)
        val set = logger.session.current!!.sets.single()
        assertEquals(3, set.extensions.size)
        assertEquals(Technique.Dropset(drops = 3), set.technique)
    }

    @Test
    fun plusAddsAMiniSetToMyoRepsBeforeAndDuringTheSet() = runTest {
        program("leg_extension", myo)
        val logger = logger()
        logger.startNext()
        logger.addDropOrMiniSet(0)
        assertEquals(Technique.MyoReps(miniSetReps = 4, maxMiniSets = 4), logger.session.current!!.techniqueAt(0))
        logger.type("60"); logger.done(); logger.type("12"); logger.tick()
        logger.tick(); logger.tick(); logger.tick()
        assertEquals(FollowUp.MiniSet(index = 3, max = 4, target = 4), logger.session.followUp)
        logger.addDropOrMiniSet(0)
        assertEquals(FollowUp.MiniSet(index = 3, max = 5, target = 4), logger.session.followUp)
        logger.tick(); logger.tick()
        assertNull(logger.session.followUp)
        assertEquals(5, logger.session.current!!.sets.single().extensions.size)
    }

    @Test
    fun aSetWithALoggedDropCantBeTakenBack() = runTest {
        program("db_bench_press", dropSet)
        val logger = logger()
        logger.startNext()
        logger.type("25"); logger.done(); logger.type("9"); logger.tick()
        // Before its first drop the set can still be taken back.
        assertEquals(true, logger.session.undoable(logger.session.current!!, 0))
        logger.tick()
        assertEquals(false, logger.session.undoable(logger.session.current!!, 0))
        now += 5_000
        logger.undo(logger.session.current!!.id)
        assertEquals(1, logger.session.current!!.sets.single().extensions.size)
    }
}
