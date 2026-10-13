package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.legacy.LegacyBackup
import com.hyperlog.app.legacy.StartingWeights
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import com.hyperlog.app.progression.Suggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Starting weights from a legacy backup (SPEC, Fresh start), as a session uses them. Synthetic data. */
@RunWith(RobolectricTestRunner::class)
class StartingWeightSessionTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private lateinit var seed: LibrarySeed
    private var now = 1_800_000_000_000L
    private var gym = 0L

    private val program = """
        { "format": 1, "title": "Start", "blocks": [
          { "id": "b", "name": "Build", "weekStart": 1, "weekEnd": 4,
            "days": [ { "id": "a", "name": "Day A", "groups": [
              { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "count": 2, "reps": "6-8", "rir": 2 } ] } ] },
              { "slots": [ { "id": "db", "exercise": "db_incline_press", "sets": [ { "reps": "8-10", "rir": 1 } ] } ] },
              { "slots": [ { "id": "ext", "exercise": "leg_extension", "sets": [ { "reps": "10-12", "rir": 1 } ] } ] } ] } ] } ] }
    """

    private fun backup(benchKg: Int, benchReps: Int) = """
        { "type": "full_backup", "version": "7.0", "exportedAt": "2026-10-12T10:00:00.000Z", "settings": [],
          "workoutLogs": [
            { "id": 1, "date": "2026-09-12T12:00:00.000Z", "exercises": [
              { "slotId": "a", "exerciseId": "barbell_bench_press", "exerciseName": "x", "sets": [
                { "setType": "warmup", "weight": 40, "reps": 8, "completed": true },
                { "setType": "work", "weight": $benchKg, "reps": $benchReps, "completed": true, "rpe": 8 } ] },
              { "slotId": "b", "exerciseId": "incline_db_press", "exerciseName": "x", "sets": [
                { "setType": "work", "weight": 50, "reps": 9, "completed": true, "rpe": 9 } ] },
              { "slotId": "c", "exerciseId": "leg_extension", "exerciseName": "x", "sets": [
                { "setType": "work", "weight": 45, "reps": 12, "completed": true, "rpe": 9, "machineWeightMode": "added" } ] } ] } ] }
    """

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seed = LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext())
        ExerciseLibrary(db).sync(seed)
        programs = Programs(db, clock = { now })
        programs.import(program)
        val gyms = Gyms(db)
        gym = gyms.addGym("Central")
        gyms.saveItem(EquipmentItem(gymId = gym, name = "Leg extension", ladder = LadderSpec.Steps(5.0, 5.0, com.hyperlog.app.domain.WeightUnit.Kg, 20)))
    }

    @After
    fun close() = db.close()

    private suspend fun import(json: String) = StartingWeights(db).import(json, seed)

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }

    private val SessionLogger.session get() = state.value!!

    private suspend fun SessionLogger.startDay() {
        now += 86_400_000L
        startDay(programs.active.first()!!, gym)
    }

    private suspend fun SessionLogger.log(kg: String, reps: Int, rir: Int) {
        now += 200_000
        focus(Field.Weight); kg.forEach { press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt())) }; done()
        "$reps".forEach { press(PadKey.Digit(it.digitToInt())) }; selectRir(rir); done()
    }

    @Test
    fun aFreeWeightsFirstSuggestionIsItsOldTopSetLabelled() = runTest {
        assertEquals(LegacyBackup.Result.Read::class, import(backup(60, 8))::class)
        val logger = logger()
        logger.startDay()
        val bench = logger.session.exercises[0]
        val s = bench.suggestion!!
        assertEquals(Suggestion.Kind.Starting, s.kind)
        assertEquals("From old app: 60 kg × 8 @ 2 RIR, 12 Sep", s.why)
        assertEquals("60", logger.session.entry!!.weight)
        assertEquals("8", logger.session.entry!!.full)
    }

    @Test
    fun anOldSetOutsideTheRangeIsWorkedIntoIt() = runTest {
        import(backup(60, 12))
        val logger = logger()
        logger.startDay()
        // 60 × 12 @ 2 is an e1RM of 88; 8 @ 2 at 88 is 66, rounded down to the plates.
        assertEquals("From old app: 60 kg × 12 @ 2 RIR, 12 Sep → 65 kg × 8", logger.session.exercises[0].suggestion!!.why)
        assertEquals("65", logger.session.entry!!.weight)
    }

    @Test
    fun theFirstRealSessionReplacesIt() = runTest {
        import(backup(60, 8))
        val logger = logger()
        logger.startDay()
        logger.log("60", 8, 2); logger.log("60", 8, 2)
        logger.finish()
        logger.startDay()
        assertNotEquals(Suggestion.Kind.Starting, logger.session.exercises[0].suggestion!!.kind)
    }

    @Test
    fun dumbbellTotalsAreHalved() = runTest {
        import(backup(60, 8))
        val logger = logger()
        logger.startDay()
        logger.switchTo(logger.session.exercises[1].id)
        assertEquals("From old app: 25 kg × 9 @ 1 RIR, 12 Sep", logger.session.exercises[1].suggestion!!.why)
        assertEquals("25", logger.session.entry!!.weight)
    }

    @Test
    fun aMachineShowsTheOldWeightAsAHintOnly() = runTest {
        import(backup(60, 8))
        val logger = logger()
        logger.startDay()
        logger.switchTo(logger.session.exercises[2].id)
        logger.chooseMachine(logger.session.gym!!.items.single().id)
        val ext = logger.session.current!!
        assertNull(ext.suggestion)
        assertEquals("45 kg × 12 @ 1 RIR, 12 Sep", ext.startingHint)
        assertEquals("", logger.session.entry!!.weight)
        // Gone once a set is logged on this machine.
        logger.log("50", 12, 1)
        assertNull(logger.session.current!!.startingHint)
    }

    @Test
    fun anAdHocFreeWeightIsPrefilledFromIt() = runTest {
        import(backup(60, 8))
        val logger = logger()
        logger.start(gym)
        logger.addExercise("barbell_bench_press")
        assertEquals("From old app: 60 kg × 8 @ 2 RIR, 12 Sep", logger.session.current!!.suggestion!!.why)
        assertEquals("60", logger.session.entry!!.weight)
        assertEquals("8", logger.session.entry!!.full)
    }

    @Test
    fun importingAddsNoSessionsAndReplacesAnEarlierImport() = runTest {
        import(backup(60, 8))
        import(backup(70, 6))
        val logger = logger()
        assertNull(logger.state.value)
        assertEquals(3, StartingWeights(db).count.first())
        logger.start(gym)
        logger.addExercise("barbell_bench_press")
        assertEquals("70", logger.session.entry!!.weight)
        assertEquals(emptyList<Any>(), logger.session.current!!.lastTime)
    }
}
