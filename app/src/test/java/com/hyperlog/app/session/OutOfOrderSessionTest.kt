package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.progression.Performance
import com.hyperlog.app.progression.Progression
import com.hyperlog.app.rest.restNotice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A later, unlogged set tapped to log it out of order (SPEC, Out of order). */
@RunWith(RobolectricTestRunner::class)
class OutOfOrderSessionTest {
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

    private suspend fun program(sets: String, second: String? = null) {
        val other = second?.let { """, { "slots": [ { "id": "t", "exercise": "cable_lateral_raise", "sets": $it } ] }""" }.orEmpty()
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 4, "days": [
              { "id": "a", "name": "Day A", "groups": [ { "slots": [ { "id": "s", "exercise": "db_bench_press", "sets": $sets } ] }$other ] } ] } ] }
            """,
        )
    }

    /** Three sets with their own targets and rests, so a set's prefill and rest show which set it is. */
    private val threeSets = """[
        { "reps": "6-8", "rir": 1, "rest": 180 },
        { "reps": "8-10", "rir": 2, "rest": 150 },
        { "reps": "10-12", "rir": 3, "rest": 90 } ]"""

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

    /** Logs the row on the pad as typed (not a warm-up), keeping the weight past a weight check. */
    private suspend fun SessionLogger.log(kg: String, reps: Int, rir: Int) {
        now += 60_000
        closeWarmup()
        focus(Field.Weight); type(kg); tick()
        type("$reps"); if (pad.rir != rir) selectRir(rir); tick()
        if (session.weightCheck != null) tick()
    }

    private val SessionLogger.session get() = state.value!!

    /** What the pad holds: the picked set's entry, else the set to do's. */
    private val SessionLogger.pad get() = session.pickedSet?.entry ?: session.entry!!

    @Test
    fun aTappedLaterSetComesOnThePadWithItsPrefill() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.log("30", 7, 1)
        // A weight typed for set 2 is what set 3's row shows, as planned from the set before it.
        logger.focus(Field.Weight); logger.type("32.5")
        val toDo = logger.session.entry

        logger.pickSet(2)

        val picked = logger.session.pickedSet!!
        assertEquals(2, picked.index)
        // Set 3's own target RIR, and the weight its row shows.
        assertEquals(3, picked.entry.rir)
        assertEquals("32.5", picked.entry.weight)
        assertEquals("", picked.entry.full)
        // The set to do keeps its entry.
        assertEquals(toDo, logger.session.entry)
        assertEquals(1, logger.session.current!!.toDo)
    }

    @Test
    fun loggingAPickedSetLogsItInItsPlaceStartsItsRestAndGoesBackToTheSetToDo() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.log("30", 7, 1)
        logger.focus(Field.Weight); logger.type("32.5")
        val toDo = logger.session.entry

        logger.pickSet(2)
        logger.log("25", 11, 3)

        val bench = logger.session.current!!
        assertEquals(listOf(0, 2), bench.positions)
        assertEquals(25.0, bench.setAt(2)!!.weightKg, 0.0)
        assertEquals(11, bench.setAt(2)!!.fullReps)
        assertNull(bench.setAt(1))
        // Set 3's rest, 1:30.
        assertEquals(90_000L, logger.session.restTotalMs)
        assertEquals(now + 90_000L, logger.session.restEndsAt)
        // The skipped set is still waiting, with what was typed for it.
        assertNull(logger.session.pickedSet)
        assertEquals(1, bench.toDo)
        assertEquals(toDo, logger.session.entry)
        assertEquals(bench.id, logger.session.currentId)
    }

    @Test
    fun loggingTheLastSetOutOfOrderStaysOnTheExercise() = runTest {
        program(threeSets, second = """[ { "count": 2, "reps": "12-15", "rir": 2 } ]""")
        val logger = logger()
        logger.startNext()
        val bench = logger.session.current!!.id

        logger.pickSet(2)
        logger.log("25", 11, 3)

        // Sets 1 and 2 are still to do: the session stays on the bench.
        assertEquals(bench, logger.session.currentId)
        assertEquals(0, logger.session.current!!.toDo)
        assertEquals(90_000L, logger.session.restTotalMs)
    }

    @Test
    fun closingAPickedSetGoesBackToTheSetToDoAndLogsNothing() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.log("30", 7, 1)
        val toDo = logger.session.entry

        logger.pickSet(2)
        logger.focus(Field.Weight); logger.type("40")
        logger.closePickedSet()

        assertNull(logger.session.pickedSet)
        assertEquals(listOf(0), logger.session.current!!.positions)
        assertEquals(toDo, logger.session.entry)
        // Typing goes to the set to do again.
        logger.focus(Field.Weight); logger.type("35")
        assertEquals("35", logger.session.entry!!.weight)
    }

    @Test
    fun takingBackASetLoggedOutOfOrderPutsItBackOnThePad() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.log("30", 7, 1)
        val toDo = logger.session.entry
        logger.pickSet(2)
        logger.log("25", 11, 3)
        val bench = logger.session.current!!
        assertEquals(true, logger.session.undoable(bench, 2))
        assertEquals(false, logger.session.undoable(bench, 0))

        now += 5_000
        logger.undo(bench.id)

        assertEquals(listOf(0), logger.session.current!!.positions)
        assertNull(logger.session.restEndsAt)
        // Back on the pad as it was logged, in its own place; the set to do keeps its entry.
        val picked = logger.session.pickedSet!!
        assertEquals(2, picked.index)
        assertEquals("25", picked.entry.weight)
        assertEquals("11", picked.entry.full)
        assertEquals(toDo, logger.session.entry)
    }

    @Test
    fun theSkippedSetLoggedLastIsTheOneTakenBack() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.log("30", 7, 1)
        logger.pickSet(2)
        logger.log("25", 11, 3)
        logger.log("27.5", 9, 2)
        val bench = logger.session.current!!
        assertEquals(listOf(0, 1, 2), bench.positions)
        assertEquals(true, logger.session.undoable(bench, 1))
        assertEquals(false, logger.session.undoable(bench, 2))

        now += 5_000
        logger.undo(bench.id)

        val after = logger.session.current!!
        assertEquals(listOf(0, 2), after.positions)
        assertEquals(25.0, after.setAt(2)!!.weightKg, 0.0)
        assertNull(logger.session.pickedSet)
        assertEquals("27.5", logger.session.entry!!.weight)
        assertEquals("9", logger.session.entry!!.full)
    }

    @Test
    fun aSetLoggedOutOfOrderIsCorrectedInItsOwnPlace() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.log("30", 7, 1)
        logger.pickSet(2)
        logger.log("25", 11, 3)
        val bench = logger.session.current!!.id

        // Set 2 isn't logged, so there is nothing to correct.
        logger.correct(bench, set = 1)
        assertNull(logger.session.correction)

        logger.correct(bench, set = 2)
        assertEquals("25", logger.session.correction!!.entry.weight)
        assertEquals("11", logger.session.correction!!.entry.full)
        logger.type("12"); logger.tick()

        val after = logger.session.current!!
        assertNull(logger.session.correction)
        assertEquals(12, after.setAt(2)!!.fullReps)
        assertEquals(7, after.setAt(0)!!.fullReps)
        assertEquals(listOf(0, 2), after.positions)
    }

    @Test
    fun aRecordSetLoggedOutOfOrderKeepsItsMarkWhenTheSkippedSetBeatsIt() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        repeat(3) { logger.log("25", 8, 2) }
        logger.finish()
        now += 86_400_000L
        logger.startNext()
        logger.log("25", 8, 2)

        logger.pickSet(2)
        logger.log("25", 10, 2)
        assertNotNull(logger.session.current!!.recordAt(2))
        // Set 2, logged after it, beats it too; set 3 was the record when it was logged and stays one.
        logger.log("25", 12, 2)

        val bench = logger.session.current!!
        assertNull(bench.recordAt(0))
        assertNotNull(bench.recordAt(1))
        assertNotNull(bench.recordAt(2))
    }

    @Test
    fun progressionReadsASetLoggedOutOfOrderAgainstItsOwnTarget() = runTest {
        program(threeSets)
        var history: List<Performance> = emptyList()
        val logger = SessionLogger(db, clock = { now }, programs = programs, suggest = { plan, today, past, ladder ->
            history = past
            Progression.suggest(plan, today, past, ladder)
        }).also { it.load() }
        logger.startNext()
        logger.log("30", 7, 1)
        logger.pickSet(2)
        logger.log("25", 11, 3)
        // Set 2 is never done.
        logger.finish()
        now += 86_400_000L
        logger.startNext()

        val last = history.last().sets
        assertEquals(listOf("6–8", "10–12"), last.map { it.target?.reps.toString() })
    }

    @Test
    fun aDropSetLoggedOutOfOrderTakesItsDropsThenGoesBackToTheSetToDo() = runTest {
        program("""[
            { "count": 2, "reps": "8-10", "rir": 2, "rest": 150 },
            { "reps": "10-12", "rir": 0, "rest": 90, "technique": { "kind": "dropset", "drops": 2 } } ]""")
        val logger = logger()
        logger.startNext()
        logger.log("30", 9, 2)

        logger.pickSet(2)
        logger.log("25", 10, 0)
        // The drop follows its own set: no rest, and the pad on the first drop.
        assertEquals(2, logger.session.openIndex)
        assertNull(logger.session.restEndsAt)
        repeat(2) {
            now += 20_000
            logger.type("8"); logger.tick()
        }

        val bench = logger.session.current!!
        assertNull(logger.session.followUp)
        assertEquals(2, bench.setAt(2)!!.extensions.size)
        assertNull(bench.setAt(1))
        assertEquals(1, bench.toDo)
        assertEquals(90_000L, logger.session.restTotalMs)
    }

    @Test
    fun aSkippedSetCantBeTakenOutFromUnderASetLoggedAfterIt() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.pickSet(2)
        logger.log("25", 11, 3)
        val bench = logger.session.current!!.id

        logger.removeSet(bench, 1)

        val after = logger.session.current!!
        assertEquals(3, after.prescription!!.size)
        assertEquals(3, after.prescription!![2].rir)
        assertEquals(25.0, after.setAt(2)!!.weightKg, 0.0)
    }

    @Test
    fun aCorrectionOrAWarmUpTakesThePadFromAPickedSet() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        // 30 kg for set 1 gives it warm-ups.
        logger.closeWarmup()
        logger.focus(Field.Weight); logger.type("30"); logger.tick()
        logger.pickSet(2)
        logger.editWarmup(0)
        assertNull(logger.session.pickedSet)
        assertEquals(0, logger.session.warmupEdit!!.index)

        logger.closeWarmup()
        logger.log("30", 7, 1)
        logger.pickSet(2)
        logger.correct(logger.session.current!!.id, set = 0)
        assertNull(logger.session.pickedSet)
        assertEquals(0, logger.session.correction!!.set)
    }

    @Test
    fun theRestNoticeAfterAPickedSetNamesTheSetToDo() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.log("30", 7, 1)
        logger.pickSet(2)
        logger.log("25", 11, 3)

        val next = logger.session.restNotice()!!.next!!
        assertEquals(2, next.set)
        assertEquals("8–10", next.reps)
        assertEquals(2, next.rir)
    }
}
