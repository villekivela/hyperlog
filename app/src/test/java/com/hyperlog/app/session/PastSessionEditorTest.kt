package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.coach.ChangeJson
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.summary
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.progression.Suggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Past sessions are editable, and progression reruns from the corrected data (SPEC, Past sessions are editable). */
@RunWith(RobolectricTestRunner::class)
class PastSessionEditorTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000_000L

    private val program = """
        { "format": 1, "title": "P", "blocks": [ { "id": "b", "name": "Build", "weekStart": 1, "weekEnd": 4, "days": [
          { "id": "a", "name": "Day A", "groups": [
            { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "count": 2, "reps": "8-10", "rir": 2 } ] } ] },
            { "slots": [ { "id": "dips", "exercise": "db_bench_press", "sets": [ { "reps": "8-10", "rir": 2, "technique": { "kind": "dropset", "drops": 1 } } ] } ] } ] } ] } ] }
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

    private suspend fun type(text: String, press: suspend (PadKey) -> Unit) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private suspend fun SessionLogger.log(kg: String, reps: Int, rir: Int) {
        now += 200_000
        focus(Field.Weight); type(kg, ::press); done()
        type("$reps", ::press); selectRir(rir); done()
    }

    /** Week 1: bench 60 × 10, 10 @ 2, the top of 8–10, and the drop set. */
    private suspend fun SessionLogger.weekOne(): Long {
        startDay(programs.active.first()!!, gymId = null)
        log("60", 10, 2); log("60", 10, 2)
        log("25", 9, 2); now += 5_000; done()
        return finish()!!.sessionId
    }

    private suspend fun PastSessionEditor.tick() {
        now += 5_000
        done()
    }

    @Test
    fun correctingAPastSessionChangesTheNextSuggestion() = runTest {
        val logger = logger()
        val week1 = logger.weekOne()
        val editor = PastSessionEditor(db, programs)
        editor.open(week1)
        val bench = editor.state.value!!.exercises[0]
        assertEquals("Barbell Bench Press", bench.exercise.name)

        // Both sets were really 8 reps.
        editor.correct(bench.id, 0); type("8", editor::press); editor.tick()
        editor.correct(bench.id, 1); type("8", editor::press); editor.tick()
        assertEquals(listOf(8, 8), editor.state.value!!.exercises[0].sets.map { it.fullReps })
        assertNull(editor.state.value!!.correction)

        now += 7 * 86_400_000L
        logger.startDay(programs.active.first()!!, gymId = null)
        val s = logger.state.value!!.current!!.suggestion!!
        assertEquals(Suggestion.Kind.More, s.kind)
        assertEquals("60", logger.state.value!!.entry!!.weight)
    }

    @Test
    fun aPastSessionKeepsThePlanItWasDoneUnder() = runTest {
        // Found on the phone: a Change accepted after a session rewrote that session's targets.
        val week1 = logger().weekOne()
        now += 60_000
        programs.accept(ChangeJson.decode("""{"summary":"More bench","scope":"rest_of_program","operations":[{"op":"adjust_sets","slot":"bench","sets":3}]}"""))

        val editor = PastSessionEditor(db, programs)
        editor.open(week1)
        assertEquals(2, editor.state.value!!.exercises[0].prescription!!.size)
    }

    @Test
    fun aChangeRevertedAfterASessionStillHeldForIt() = runTest {
        programs.accept(ChangeJson.decode("""{"summary":"More bench","scope":"rest_of_program","operations":[{"op":"adjust_sets","slot":"bench","sets":3}]}"""))
        now += 60_000
        val week1 = logger().weekOne()
        now += 60_000
        programs.revert(db.planChanges().changes(db.programs().active()!!.id).single().id)

        val editor = PastSessionEditor(db, programs)
        editor.open(week1)
        assertEquals(3, editor.state.value!!.exercises[0].prescription!!.size)
    }

    @Test
    fun aRunningSessionTakesThePastCorrectionAtOnce() = runTest {
        val logger = logger()
        val week1 = logger.weekOne()
        now += 7 * 86_400_000L
        logger.startDay(programs.active.first()!!, gymId = null)
        assertEquals(Suggestion.Kind.Up, logger.state.value!!.current!!.suggestion!!.kind)
        assertEquals("62.5", logger.state.value!!.entry!!.weight)

        val editor = PastSessionEditor(db, programs, onChanged = logger::readAgain)
        editor.open(week1)
        val bench = editor.state.value!!.exercises[0].id
        editor.correct(bench, 0); type("8", editor::press); editor.tick()
        editor.correct(bench, 1); type("8", editor::press); editor.tick()

        assertEquals(Suggestion.Kind.More, logger.state.value!!.current!!.suggestion!!.kind)
        assertEquals("60", logger.state.value!!.entry!!.weight)
    }

    @Test
    fun thePastSessionListsFinishedSessionsNewestFirstWithTheirDay() = runTest {
        val logger = logger()
        val week1 = logger.weekOne()
        now += 86_400_000L
        logger.start()
        logger.addExercise("db_bench_press")
        logger.log("20", 10, 2)
        val adHoc = logger.finish()!!.sessionId

        val list = PastSessionEditor(db, programs).sessions()
        assertEquals(listOf(adHoc, week1), list.map { it.id })
        assertEquals(listOf(null, "Day A"), list.map { it.title })
        assertEquals("60 × 10 @ 2", list[1].exercises[0].sets[0].summary())
    }

    @Test
    fun aPastDropIsCorrectedAndAnExerciseOpensWithLastTimeAsItWasThen() = runTest {
        val logger = logger()
        val week1 = logger.weekOne()
        now += 7 * 86_400_000L
        logger.startDay(programs.active.first()!!, gymId = null)
        logger.log("62.5", 9, 2)
        logger.finish()

        val editor = PastSessionEditor(db, programs)
        editor.open(week1)
        // Week 1 had nothing before it, even though week 2 came later.
        assertEquals(emptyList<Any>(), editor.state.value!!.exercises[0].lastTime)
        val db1 = editor.state.value!!.exercises[1]
        editor.show(db1.id)
        assertEquals(db1.id, editor.state.value!!.shownId)
        editor.correct(db1.id, 0, extension = 0)
        editor.focus(Field.Weight); type("16", editor::press); editor.tick(); editor.tick()
        assertEquals(16.0, editor.state.value!!.exercises[1].sets[0].extensions[0].weightKg, 0.0)

        editor.show(null)
        assertNull(editor.state.value!!.shownId)
    }
}
