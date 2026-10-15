package com.hyperlog.app.coach

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import com.hyperlog.app.session.SessionLogger
import com.hyperlog.app.session.keptEdits
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
import java.time.LocalDate
import java.time.ZoneId

/** Changes through the program store: previewed as a diff, applied on accept, kept, reverted. */
@RunWith(RobolectricTestRunner::class)
class ChangesTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private val zone = ZoneId.systemDefault()
    private var now = LocalDate.of(2026, 10, 13).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { now })
        val imported = programs.import(PROGRAM)
        check(imported is com.hyperlog.app.program.ProgramParser.Result.Valid) { imported.toString() }
    }

    @After
    fun close() = db.close()

    private fun change(json: String) = ChangeJson.decode(json)
    private suspend fun active() = programs.active.first()!!
    private fun days(n: Long) { now += n * 86_400_000L }

    private suspend fun SessionLogger.logAndFinish(kg: String) {
        focus(Field.Weight)
        kg.forEach { press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt())) }
        done(); press(PadKey.Digit(8)); if (state.value?.entry?.rir != 1) selectRir(1); now += 2_000; done()
        now += 2_000
        now += 2_000
        finish()
    }

    @Test
    fun shiftingTheProgramByOneWeekShowsADiffAndAppliesOnlyAfterAccept() = runTest {
        db.programs().setPosition(active().id, 3, 0)
        val shift = change("""{"summary":"One week later","scope":"rest_of_program","operations":[{"op":"shift_program","weeks":1}]}""")

        val diff = programs.preview(shift)!!
        assertEquals(Position(3, 0) to Position(2, 0), diff.next)
        assertEquals(Position(3, 0), active().position)

        programs.accept(shift)
        assertEquals(Position(2, 0), active().position)
        assertEquals("One week later", active().history.single().change.summary)
    }

    @Test
    fun aTemporarySwapIsTrainedInsideItsScopeAndStopsAfterIt() = runTest {
        programs.accept(
            change(
                """{"summary":"No grip","scope":{"from":"2026-10-13","to":"2026-10-19"},
                   "operations":[{"op":"swap","slot":"row","exercise":"chest_supported_db_row"}]}""",
            ),
        )
        assertEquals("chest_supported_db_row", active().next!!.slots[1].exercise)
        val logger = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        logger.startDay(active(), null)
        assertEquals("chest_supported_db_row", logger.state.value!!.exercises[1].exercise.id)
        logger.logAndFinish("60")

        days(7)
        db.programs().setPosition(active().id, 2, 0)
        assertEquals("seated_cable_row", active().next!!.slots[1].exercise)
        logger.startDay(active(), null)
        assertEquals("seated_cable_row", logger.state.value!!.exercises[1].exercise.id)
    }

    @Test
    fun aReplyThatOnlyEndsAChangeRevertsItOnAccept() = runTest {
        val before = active()
        programs.accept(change("""{"summary":"No rows","scope":"rest_of_program","operations":[{"op":"remove","slot":"row"},{"op":"skip_day"}]}"""))
        val id = active().history.single().id
        val ends = change("""{"summary":"Hands fine now","scope":"rest_of_program","replaces":[$id],"operations":[]}""")
        assertEquals(listOf(id), ends.replaces)

        // The diff shows the plan going back.
        val diff = programs.preview(ends)!!
        assertEquals(Position(1, 1) to Position(1, 0), diff.next)
        assertEquals("seated_cable_row", diff.days.first().slots.single().after!!.exercise)

        programs.accept(ends)
        val after = active()
        assertEquals(before.position, after.position)
        assertEquals(before.next, after.next)
        assertNotNull(after.history.first { it.id == id }.revertedAt)
        // The replaced one reads as replaced; the ending one does nothing, so it isn't in force.
        assertEquals(setOf(id), after.replaced)
        assertEquals(emptyList<Long>(), after.inForce.map { it.id })
    }

    @Test
    fun aReplyThatReplacesAChangeEndsItAndAppliesItsOwn() = runTest {
        programs.accept(change("""{"summary":"No rows","scope":"rest_of_program","operations":[{"op":"remove","slot":"row"}]}"""))
        val id = active().history.single().id
        programs.accept(
            change("""{"summary":"Chest-supported rows","scope":"rest_of_program","replaces":[$id],"operations":[{"op":"swap","slot":"row","exercise":"chest_supported_db_row"}]}"""),
        )
        val after = active()
        assertEquals("chest_supported_db_row", after.next!!.slots[1].exercise)
        assertEquals(listOf("Chest-supported rows"), after.inForce.map { it.change.summary })
        assertEquals(setOf(id), after.replaced)
    }

    @Test
    fun revertingAnAcceptedChangeRestoresThePlan() = runTest {
        val before = active()
        programs.accept(
            change(
                """{"summary":"Easier","scope":"rest_of_program","operations":[
                    {"op":"remove","slot":"row"},{"op":"adjust_sets","slot":"bench","sets":1},{"op":"skip_day"}]}""",
            ),
        )
        val changed = active()
        assertEquals(Position(1, 1), changed.position)
        assertEquals(listOf("hack_squat"), changed.next!!.slots.map { it.exercise })

        programs.revert(changed.history.single().id)
        val reverted = active()
        assertEquals(before.position, reverted.position)
        assertEquals(before.next, reverted.next)
        assertNotNull(reverted.history.single().revertedAt)
        // Reverting twice does nothing.
        programs.revert(changed.history.single().id)
        assertEquals(before.position, active().position)
    }

    @Test
    fun aLoadChangeLightensTheSuggestionForItsScope() = runTest {
        val logger = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        logger.startDay(active(), null)
        logger.logAndFinish("100")
        days(1)
        logger.startDay(active(), null)
        logger.finish()
        days(1)
        db.programs().setPosition(active().id, 2, 0)
        logger.startDay(active(), null)
        val written = logger.state.value!!.exercises[0].suggestion!!
        logger.finish()

        programs.accept(
            change("""{"summary":"Lighter","scope":{"from":"2026-10-15","to":"2026-10-15"},"operations":[{"op":"adjust_load","slot":"bench","percent":-10}]}"""),
        )
        logger.startDay(active(), null)
        val lighter = logger.state.value!!.exercises[0].suggestion!!
        assertEquals(100.0, written.kg, 0.01)
        assertEquals(90.0, lighter.kg, 0.01)
        assertEquals("90", logger.state.value!!.entry!!.weight)
        assertEquals(true, lighter.why!!.endsWith("· 10% lighter for a Change"))
        logger.finish()

        days(1)
        logger.startDay(active(), null)
        assertEquals(100.0, logger.state.value!!.exercises[0].suggestion!!.kg, 0.01)
    }

    @Test
    fun keepingASessionsEditsForTheBlockCarriesThemIntoItsNextSessions() = runTest {
        val logger = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        logger.startDay(active(), null)
        val session = logger.state.value!!
        logger.swap(session.exercises[1].id, "chest_supported_db_row")
        logger.removeSet(session.exercises[0].id, 1)
        val change = logger.state.value!!.keptEdits("Keep my edits")!!
        assertEquals(
            listOf(Operation.AdjustSets("bench", 1), Operation.Swap("row", "chest_supported_db_row")),
            change.operations,
        )
        programs.accept(change)
        logger.readAgain()
        // Accepted, the session's edits are the plan: nothing more to keep.
        assertNull(logger.state.value!!.keptEdits("Keep my edits"))
        logger.logAndFinish("60")

        days(2)
        db.programs().setPosition(active().id, 2, 0)
        logger.startDay(active(), null)
        val next = logger.state.value!!.exercises
        assertEquals("chest_supported_db_row", next[1].exercise.id)
        assertEquals(1, next[0].prescription!!.size)
    }

    companion object {
        val PROGRAM = """
            { "format": 1, "title": "Changes", "blocks": [
              { "id": "one", "name": "One", "weekStart": 1, "weekEnd": 3, "days": [
                { "id": "a", "name": "Day A", "groups": [
                  { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "count": 2, "reps": 8, "rir": 1 } ] } ] },
                  { "slots": [ { "id": "row", "exercise": "seated_cable_row", "sets": [ { "count": 2, "reps": "8-10", "rir": 1 } ] } ] } ] },
                { "id": "b", "name": "Day B", "groups": [
                  { "slots": [ { "id": "squat", "exercise": "hack_squat", "sets": [ { "count": 2, "reps": "6-8", "rir": 1 } ] } ] } ] } ] } ] }
        """
    }
}
