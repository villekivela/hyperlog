package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.rest.restNotice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Corrections during a session (SPEC, Corrections): any logged set, drop or mini-set loads into the pad. */
@RunWith(RobolectricTestRunner::class)
class CorrectionSessionTest {
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

    private val threeSets = """[ { "count": 3, "reps": "8-10", "rir": 2, "rest": 150 } ]"""

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

    /** Logs the active set as typed, keeping the weight past a weight check. */
    private suspend fun SessionLogger.log(kg: String, reps: Int, rir: Int) {
        now += 60_000
        focus(Field.Weight); type(kg); tick()
        type("$reps"); selectRir(rir); tick()
        if (session.weightCheck != null) tick()
    }

    private val SessionLogger.session get() = state.value!!

    @Test
    fun aCorrectedWeightGetsTheSameCheckAsALoggedOne() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.log("25", 10, 2)
        logger.log("25", 10, 2)
        val bench = logger.session.current!!
        logger.correct(bench.id, set = 1)
        logger.focus(Field.Weight); logger.type("250"); logger.tick()
        // The first ✓ on the reps warns, as logging would: 10× the set before.
        logger.tick()
        assertEquals("10× last set", logger.session.weightCheck!!.reason)
        assertEquals(25.0, logger.session.current!!.sets[1].weightKg, 0.0)
        // A second ✓ keeps it, as a kept weight.
        logger.tick()
        assertNull(logger.session.correction)
        assertEquals(250.0, logger.session.current!!.sets[1].weightKg, 0.0)
        assertEquals(true, logger.session.current!!.sets[1].kept)
    }

    @Test
    fun theLastSetLoggedIsOneFixAwayEvenAfterTheSessionMovedOn() = runTest {
        program(threeSets, second = """[ { "count": 1, "reps": "10-12", "rir": 1 } ]""")
        val logger = logger()
        logger.startNext()
        val bench = logger.session.current!!
        logger.log("25", 10, 2); logger.log("25", 10, 2)
        // A typo kept past the warning, on the last set: the session moves on to the lateral raise.
        logger.log("255", 10, 2)
        assertEquals("cable_lateral_raise", logger.session.current!!.exercise.id)
        assertEquals(bench.id to 2, logger.session.lastLogged)

        logger.fixLast()
        // The bench is on screen with its last set loaded, on the weight it was warned about.
        assertEquals(bench.id, logger.session.shown!!.id)
        assertEquals(2, logger.session.correction!!.set)
        assertEquals(Field.Weight, logger.session.correction!!.entry.field)
        logger.type("25"); logger.tick(); logger.tick()
        assertEquals(25.0, logger.session.exercises.first { it.id == bench.id }.sets[2].weightKg, 0.0)
        // Saved, and back on the lateral raise.
        assertNull(logger.session.correction)
        assertEquals(logger.session.current!!.id, logger.session.shown!!.id)
    }

    @Test
    fun correctingA255KgTypoTo25UpdatesTheSetAndRestKeepsRunning() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.log("25", 10, 2)
        logger.log("255", 10, 2)
        val bench = logger.session.current!!
        assertEquals(255.0, bench.sets[1].weightKg, 0.0)
        val restEnds = logger.session.restEndsAt!!
        val notice = logger.session.restNotice()

        now += 20_000
        logger.correct(bench.id, set = 1)
        // The set loads into the pad; the set to do keeps its own entry.
        assertEquals("255", logger.session.correction!!.entry.weight)
        logger.focus(Field.Weight); logger.type("25"); logger.tick()
        logger.tick()

        assertNull(logger.session.correction)
        val set = logger.session.current!!.sets[1]
        assertEquals(25.0, set.weightKg, 0.0)
        assertEquals(10, set.fullReps)
        assertEquals(false, set.kept)
        assertEquals(restEnds, logger.session.restEndsAt)
        assertEquals(notice!!.endsAt, logger.session.restNotice()!!.endsAt)
        assertEquals(2, logger.session.current!!.sets.size)
    }

    @Test
    fun theSetToDoIsPrefilledAgainFromACorrectionUnlessSomethingWasTypedIntoIt() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("db_bench_press")
        logger.log("25", 10, 2)
        logger.log("25", 1, 2)
        // An ad-hoc set follows the set before.
        assertEquals("1", logger.session.entry!!.full)
        val bench = logger.session.current!!.id
        logger.correct(bench, 1); logger.type("11"); logger.tick()
        assertEquals("11", logger.session.entry!!.full)

        // Once the set to do has been typed into, a correction leaves it alone.
        logger.type("9")
        logger.correct(bench, 0); logger.type("12"); logger.tick()
        assertEquals(12, logger.session.current!!.sets[0].fullReps)
        assertEquals("9", logger.session.entry!!.full)
    }

    @Test
    fun theHideKeyCancelsACorrectionAndTheSetKeepsWhatItHad() = runTest {
        program(threeSets)
        val logger = logger()
        logger.startNext()
        logger.log("25", 10, 2)
        val bench = logger.session.current!!.id
        logger.correct(bench, 0)
        logger.type("6"); logger.selectRir(0)
        logger.cancelCorrection()
        assertNull(logger.session.correction)
        val set = logger.session.current!!.sets[0]
        assertEquals(10, set.fullReps)
        assertEquals(2, set.rir)
        // The set to do is still on the pad.
        assertEquals("25", logger.session.entry!!.weight)
    }

    @Test
    fun aDropIsCorrectedOnItsOwnAndTheSetKeepsItsTechniqueAndOtherDrops() = runTest {
        program("""[ { "reps": "8-10", "rir": 1, "rest": 120, "technique": { "kind": "dropset", "drops": 2 } }, { "reps": "8-10", "rir": 1 } ]""")
        val logger = logger()
        logger.startNext()
        logger.type("25"); logger.tick(); logger.type("9"); logger.tick()
        logger.type("7"); logger.tick(); logger.tick()
        val bench = logger.session.current!!.id
        logger.correct(bench, 0, extension = 1)
        assertEquals("12", logger.session.correction!!.entry.weight)
        assertNull(logger.session.correction!!.entry.rir)
        logger.focus(Field.Weight); logger.type("14"); logger.tick(); logger.type("5"); logger.tick()

        val set = logger.session.current!!.sets[0]
        assertEquals(com.hyperlog.app.program.Technique.Dropset(drops = 2), set.technique)
        assertEquals(
            listOf(
                com.hyperlog.app.domain.Extension(com.hyperlog.app.domain.Extension.Kind.Drop, 18.0, 7),
                com.hyperlog.app.domain.Extension(com.hyperlog.app.domain.Extension.Kind.Drop, 14.0, 5),
            ),
            set.extensions,
        )
        assertEquals(25.0, set.weightKg, 0.0)
    }

    @Test
    fun miniSetsFollowTheirSetsCorrectedWeightAndTheirOwnWeightCantBeFocused() = runTest {
        program("""[ { "reps": "10-12", "rir": 1, "technique": { "kind": "myo_reps", "miniSetReps": 4, "maxMiniSets": 2 } }, { "reps": "10-12", "rir": 1 } ]""")
        val logger = logger()
        logger.startNext()
        logger.type("20"); logger.tick(); logger.type("12"); logger.tick()
        logger.tick(); logger.tick()
        val e = logger.session.current!!.id
        logger.correct(e, 0, extension = 0)
        logger.focus(Field.Weight)
        assertEquals(Field.Reps, logger.session.correction!!.entry.field)
        logger.cancelCorrection()

        // 22.5 is on the dumbbells, so the correction's weight check lets it through.
        logger.correct(e, 0); logger.focus(Field.Weight); logger.type("22.5"); logger.tick(); logger.tick()
        assertEquals(listOf(22.5, 22.5), logger.session.current!!.sets[0].extensions.map { it.weightKg })
    }

    @Test
    fun rowsCantBeCorrectedWhileASetIsOpenForItsDrops() = runTest {
        program("""[ { "reps": "8-10", "rir": 1, "technique": { "kind": "dropset", "drops": 2 } } ]""")
        val logger = logger()
        logger.startNext()
        logger.type("25"); logger.tick(); logger.type("9"); logger.tick()
        logger.correct(logger.session.current!!.id, 0)
        assertNull(logger.session.correction)
    }

    @Test
    fun aFinishedExerciseOpensFromTheListToCorrectWhileTheSessionStaysOnItsSet() = runTest {
        program("""[ { "reps": "8-10", "rir": 2 } ]""", second = """[ { "count": 2, "reps": "12-15", "rir": 1 } ]""")
        val logger = logger()
        logger.startNext()
        logger.log("25", 10, 2)
        val (bench, lateral) = logger.session.exercises.map { it.id }
        assertEquals(lateral, logger.session.currentId)
        val restEnds = logger.session.restEndsAt

        logger.open(bench)
        assertEquals(bench, logger.session.shown!!.id)
        assertEquals(lateral, logger.session.currentId)
        logger.correct(bench, 0); logger.type("8"); logger.tick()
        assertEquals(8, logger.session.shown!!.sets[0].fullReps)
        assertEquals(bench, logger.session.shown!!.id)
        assertEquals(restEnds, logger.session.restEndsAt)

        logger.closeView()
        assertEquals(lateral, logger.session.shown!!.id)
        assertNull(logger.session.viewingId)
    }
}
