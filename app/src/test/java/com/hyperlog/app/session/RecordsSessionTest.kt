package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.progression.Record
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Records as sets log and afterwards (SPEC, Records): marked on the running session, Session done
 * and a past session, per machine for machines, and moved by a correction.
 */
@RunWith(RobolectricTestRunner::class)
class RecordsSessionTest {
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

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }

    private suspend fun type(text: String, press: suspend (PadKey) -> Unit) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private suspend fun SessionLogger.log(kg: String, reps: Int) {
        now += 200_000
        closeWarmup()
        focus(Field.Weight); type(kg, ::press); done()
        type("$reps", ::press); if (state.value?.entry?.rir != 2) selectRir(2); done()
    }

    /** A session of [exercise] at [gymId], its sets logged as [kg] × reps @ 2, finished a day later than the last. */
    private suspend fun SessionLogger.session(exercise: String, vararg sets: Pair<String, Int>, gymId: Long? = null): SessionSummary {
        now += 86_400_000L
        start(gymId)
        addExercise(exercise)
        sets.forEach { (kg, reps) -> log(kg, reps) }
        return finish()!!
    }

    private val SessionLogger.current get() = state.value!!.current!!

    @Test
    fun aRecordSetIsMarkedAsItLogsAndListedOnSessionDone() = runTest {
        val logger = logger()
        logger.session("barbell_bench_press", "60" to 8)
        now += 86_400_000L
        logger.start()
        logger.addExercise("barbell_bench_press")
        logger.log("62.5", 8)
        assertEquals(listOf(Record.Estimate), logger.current.records)
        // 60 × 9 is one more rep than last time's 60 × 8.
        logger.log("60", 9)
        assertEquals(listOf(Record.Estimate, Record.Reps), logger.current.records)
        logger.log("60", 8)
        val done = logger.finish()!!
        assertEquals(listOf(Record.Estimate, Record.Reps, null), done.exercises.single().records)
    }

    @Test
    fun aPastSessionKeepsItsMarks() = runTest {
        val logger = logger()
        logger.session("barbell_bench_press", "60" to 8)
        val second = logger.session("barbell_bench_press", "60" to 10, "60" to 8)
        // A later, heavier session doesn't take the earlier one's record away.
        logger.session("barbell_bench_press", "65" to 8)
        val editor = PastSessionEditor(db, programs)
        editor.open(second.sessionId)
        assertEquals(listOf(Record.Estimate, null), editor.state.value!!.exercises.single().records)
        assertEquals(listOf(Record.Estimate, null), editor.state.value!!.summary.exercises.single().records)
    }

    @Test
    fun machinesAndCablesHaveRecordsPerMachine() = runTest {
        val gyms = Gyms(db)
        val harbour = gyms.addGym("Harbour")
        gyms.addItem(EquipmentItem(gymId = harbour, name = "Cable station", ladder = LadderSpec.lbStack(), cableStation = true))
        val hill = gyms.addGym("Hill")
        gyms.addItem(EquipmentItem(gymId = hill, name = "Cable station", ladder = LadderSpec.lbStack(), cableStation = true))
        val logger = logger()
        logger.session("cable_lateral_raise", "5.7" to 12, gymId = harbour)
        logger.session("cable_lateral_raise", "5.7" to 8, gymId = hill)
        // 5.7 × 10 beats Hill's 5.7 × 8, though not Harbour's 5.7 × 12.
        val done = logger.session("cable_lateral_raise", "5.7" to 10, gymId = hill)
        assertEquals(listOf(Record.Estimate), done.exercises.single().records)
    }

    @Test
    fun correctingAPastSetMovesTheRecords() = runTest {
        val logger = logger()
        val first = logger.session("barbell_bench_press", "60" to 8)
        val second = logger.session("barbell_bench_press", "62.5" to 8)
        val editor = PastSessionEditor(db, programs)
        editor.open(first.sessionId)
        // The first session's set was really 65 × 8.
        val bench = editor.state.value!!.exercises.single()
        editor.correct(bench.id, 0)
        editor.focus(Field.Weight); type("65", editor::press); editor.done()
        now += 5_000
        editor.done()
        assertEquals(65.0, editor.state.value!!.exercises.single().sets.single().weightKg, 0.001)
        editor.open(second.sessionId)
        assertEquals(listOf<Record?>(null), editor.state.value!!.exercises.single().records)
    }
}
