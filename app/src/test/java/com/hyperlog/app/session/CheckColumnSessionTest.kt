package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
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

/** The check column (SPEC, The check column): a tap logs the set as shown, a tap on the last one takes it back. */
@RunWith(RobolectricTestRunner::class)
class CheckColumnSessionTest {
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
        programs.import(
            """
            { "format": 1, "title": "T", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 4, "days": [
              { "id": "a", "name": "Day A", "groups": [
                { "slots": [ { "id": "lat", "exercise": "db_lateral_raise", "sets": [ { "count": 2, "reps": "10-12", "rir": 1, "rest": 90 } ] } ] },
                { "slots": [ { "id": "curl", "exercise": "db_bicep_curl", "sets": [ { "reps": "8-10", "rir": 1, "rest": 90 } ] } ] }
              ] } ] } ] }
            """,
        )
    }

    @After
    fun close() = db.close()

    private suspend fun started(): SessionLogger {
        val logger = SessionLogger(db, clock = { now }, programs = programs)
        logger.load()
        logger.startDay(programs.active.first()!!, gymId = null)
        return logger
    }

    private suspend fun SessionLogger.type(text: String) = text.forEach { press(PadKey.Digit(it.digitToInt())) }

    private val SessionLogger.session get() = state.value!!

    @Test
    fun theCheckLogsTheSetAsShownFromTheWeightAndStartsRest() = runTest {
        val logger = started()
        logger.type("10")
        logger.focus(Field.Reps); logger.type("12"); logger.focus(Field.Weight)
        assertEquals(Field.Weight, logger.session.entry!!.field)
        logger.logShown()
        assertEquals(listOf(LoggedSet(10.0, 12, 0, 1)), logger.session.current!!.sets)
        assertEquals(now + 90_000, logger.session.restEndsAt)
    }

    @Test
    fun withNothingToLogTheCheckOpensThePadOnTheWeight() = runTest {
        val logger = started()
        logger.setPadVisible(false)
        logger.logShown()
        assertEquals(emptyList<LoggedSet>(), logger.session.current!!.sets)
        assertEquals(true, logger.session.padVisible)
        assertEquals(Field.Weight, logger.session.entry!!.field)
    }

    @Test
    fun theLastChecksTapTakesTheSetBackAndStopsRest() = runTest {
        val logger = started()
        logger.type("10"); logger.done(); logger.press(PadKey.Backspace); logger.press(PadKey.Backspace); logger.type("11")
        logger.logShown()
        val lateral = logger.session.current!!
        assertEquals((lateral.id to 0), logger.session.latestSet)
        // A stutter right after logging doesn't take it back.
        now += 300
        logger.undo(lateral.id)
        assertEquals(1, logger.session.current!!.sets.size)

        now += 5_000
        logger.undo(lateral.id)
        assertEquals(emptyList<LoggedSet>(), logger.session.current!!.sets)
        assertNull(logger.session.restEndsAt)
        val entry = logger.session.entry!!
        assertEquals("10", entry.weight)
        assertEquals("11", entry.full)
        assertEquals(1, entry.rir)
        assertNull(logger.session.latestSet)
    }

    @Test
    fun onlyTheSetLoggedLastCanBeTakenBack() = runTest {
        val logger = started()
        logger.type("10"); logger.focus(Field.Reps); logger.type("12"); logger.logShown()
        now += 5_000
        logger.type("12"); logger.logShown()
        now += 5_000
        val lateral = logger.session.exercises.first()
        assertEquals(2, lateral.sets.size)
        // The session moved on to the curl; the lateral raise's last set is still the one logged last.
        assertNotNull(logger.session.latestSet)
        assertEquals(false, logger.session.undoable(lateral, 0))
        logger.undo(lateral.id)
        assertEquals(1, logger.session.exercises.first().sets.size)
        assertEquals(lateral.id, logger.session.currentId)
    }
}
