package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.history.Bodyweights
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.progression.Suggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

/** The latest bodyweight is used for weighted bodyweight exercises (SPEC, Bodyweight). */
@RunWith(RobolectricTestRunner::class)
class WeightedBodyweightSessionTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_790_000_000_000L

    /** Weighted pull-ups for 8–10 in week 1, then a heavier block of 4–6. */
    private val program = """
        { "format": 1, "title": "Pull", "blocks": [
          { "id": "build", "name": "Build", "weekStart": 1, "weekEnd": 1, "days": [ { "id": "a", "name": "Day A", "groups": [
              { "slots": [ { "id": "pull", "exercise": "weighted_pullup", "sets": [ { "reps": "8-10", "rir": 1 } ] } ] } ] } ] },
          { "id": "heavy", "name": "Heavy", "weekStart": 2, "weekEnd": 2, "days": [ { "id": "a", "name": "Day A", "groups": [
              { "slots": [ { "id": "pull", "exercise": "weighted_pullup", "sets": [ { "reps": "4-6", "rir": 1 } ] } ] } ] } ] } ] }
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

    private fun today() = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()

    @Test
    fun aNewRangeIsWorkedOutFromTheBodyAndTheBeltTogether() = runTest {
        Bodyweights(db).log(today(), 80.0)
        val logger = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        logger.startDay(programs.active.first()!!, gymId = null)
        now += 200_000
        logger.focus(Field.Weight); logger.press(PadKey.Digit(1)); logger.press(PadKey.Digit(0)); logger.done()
        logger.press(PadKey.Digit(8)); if (logger.state.value?.entry?.rir != 1) logger.selectRir(1); logger.done()
        logger.finish()

        now += 7 * 86_400_000L
        logger.startDay(programs.active.first()!!, gymId = null)

        // 10 kg on the belt at 80 kg, 8 @ 1, is 117 kg; 4–6 @ 1 is 100 kg in all, so 20 kg on the belt.
        val suggestion = logger.state.value!!.exercises.single().suggestion!!
        assertEquals(Suggestion.Kind.Estimated, suggestion.kind)
        assertEquals(20.0, suggestion.kg, 0.001)
    }
}
