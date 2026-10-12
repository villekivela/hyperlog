package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.progression.Suggestion
import com.hyperlog.app.program.Programs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A program day's first sets are suggested from the exercise's history, and say why. */
@RunWith(RobolectricTestRunner::class)
class ProgressionSessionTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private var now = 1_000_000L
    private var gym = 0L

    /** One day a week: bench and cable lateral raises. Week 4 is a deload; week 5 starts a heavier block. */
    private val program = """
        { "format": 1, "title": "Progression", "blocks": [
          { "id": "build", "name": "Build", "weekStart": 1, "weekEnd": 4,
            "weeks": { "4": { "rir": 3, "deload": true } },
            "days": [ { "id": "a", "name": "Day A", "groups": [
              { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "count": 2, "reps": "8-10", "rir": 2 } ] } ] },
              { "slots": [ { "id": "lateral", "exercise": "cable_lateral_raise", "sets": [ { "count": 2, "reps": "10-12", "rir": 0 } ] } ] } ] } ] },
          { "id": "heavy", "name": "Heavy", "weekStart": 5, "weekEnd": 5,
            "days": [ { "id": "a", "name": "Day A", "groups": [
              { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "reps": "4-6", "rir": 1 } ] } ] } ] } ] } ] }
    """

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { now })
        programs.import(program)
        val gyms = Gyms(db)
        gym = gyms.addGym("Kallio")
        gyms.saveItem(EquipmentItem(gymId = gym, name = "Cable station", ladder = LadderSpec.lbStack()))
    }

    @After
    fun close() = db.close()

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }

    private val SessionLogger.session get() = state.value!!

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    /** Logs the active set with this weight, reps and RIR, whatever was prefilled. */
    private suspend fun SessionLogger.log(kg: String, reps: Int, rir: Int) {
        now += 200_000
        focus(com.hyperlog.app.domain.Field.Weight); type(kg); done()
        type("$reps"); selectRir(rir); done()
    }

    private suspend fun SessionLogger.startWeek() {
        now += 7 * 86_400_000L
        startDay(programs.active.first()!!, gym)
        // The cable station is remembered after the first week.
        if (session.exercises.any { it.needsMachine }) {
            switchTo(session.exercises[1].id)
            chooseMachine(session.gym!!.items.single().id)
            switchTo(session.exercises[0].id)
        }
    }

    private val SessionLogger.bench get() = session.exercises[0]

    @Test
    fun benchProgressesUpDownThroughADeloadAndIntoANewRepRange() = runTest {
        val logger = logger()
        // Week 1: both sets at the top of 8–10 @ 2.
        logger.startWeek()
        logger.log("60", 10, 2); logger.log("60", 10, 2)
        logger.finish()

        // Week 2: one rung up, and why.
        logger.startWeek()
        var s = logger.bench.suggestion!!
        assertEquals(Suggestion.Kind.Up, s.kind)
        assertEquals("Last time 60 kg × 10, 10 @ 2, top of 8–10 → 62.5 kg", s.why)
        assertEquals("62.5", logger.session.entry!!.weight)
        assertEquals("${s.reps}", logger.session.entry!!.full)
        logger.log("62.5", 6, 2); logger.log("62.5", 6, 2)
        logger.finish()

        // Week 3: two short of the range, so one rung (about 5%) down.
        logger.startWeek()
        s = logger.bench.suggestion!!
        assertEquals(Suggestion.Kind.Down, s.kind)
        assertEquals("60", logger.session.entry!!.weight)
        logger.log("60", 9, 2); logger.log("60", 9, 2)
        logger.finish()

        // Week 4, the deload: last time's weight at 3 in reserve. A bad day doesn't lower the estimate.
        logger.startWeek()
        assertEquals(Suggestion.Kind.Deload, logger.bench.suggestion!!.kind)
        assertEquals("60", logger.session.entry!!.weight)
        logger.log("60", 5, 3)
        logger.finish()

        // Week 5, a new rep range: 60 × 9 @ 2 estimates 82 kg, so 4–6 @ 1 starts at 70 (70.3 rounded down).
        logger.startWeek()
        s = logger.bench.suggestion!!
        assertEquals(true, s.estimated)
        assertEquals("Estimated from 60 kg × 9 @ 2, check on the first set", s.why)
        assertEquals("70", logger.session.entry!!.weight)
    }

    @Test
    fun aCableStackJumpTooBigForTheRangeHoldsTheWeightAndAddsARep() = runTest {
        val logger = logger()
        logger.startWeek()
        logger.switchTo(logger.session.exercises[1].id)
        logger.log("5.7", 12, 0); logger.log("5.7", 12, 0)
        logger.finish()

        logger.startWeek()
        logger.switchTo(logger.session.exercises[1].id)
        val lateral = logger.session.current!!
        assertEquals(Suggestion.Kind.More, lateral.suggestion!!.kind)
        assertEquals("5.7", logger.session.entry!!.weight)
        assertEquals("13", logger.session.entry!!.full)
    }
}
