package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.WarmupSet
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Programs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Warm-ups and the plate calculator in a session (SPEC, Screens → Today). */
@RunWith(RobolectricTestRunner::class)
class WarmupSessionTest {
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
                { "slots": [ { "id": "lat", "exercise": "db_lateral_raise", "sets": [ { "reps": "10-12", "rir": 1, "rest": 90 } ] } ] },
                { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [
                  { "reps": "4-6", "rir": 1, "rest": 180 }, { "count": 2, "reps": "6-8", "rir": 1, "rest": 180, "backoff": {} } ] } ] }
              ] } ] } ] }
            """,
        )
    }

    @After
    fun close() = db.close()

    private suspend fun logger() = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private suspend fun SessionLogger.tick() {
        now += 5_000
        done()
    }

    private val SessionLogger.session get() = state.value!!

    /** Logs the lateral raise, so the bench comes up with its rest running. */
    private suspend fun benchUp(): SessionLogger {
        val logger = logger()
        logger.startDay(programs.active.first()!!, gymId = null)
        logger.type("10"); logger.done(); logger.type("12"); logger.selectRir(1); logger.tick()
        assertEquals("Barbell Bench Press", logger.session.current!!.exercise.name)
        return logger
    }

    @Test
    fun aNinetyKgBenchTopSetGetsARampOfWarmUpsOnThePlates() = runTest {
        val logger = benchUp()
        logger.type("90")
        // Not keystroke by keystroke: the ramp waits for → (typing 90 never passes through a 9 kg ramp).
        assertEquals(emptyList<WarmupSet>(), logger.session.warmups)
        logger.done()
        assertEquals(
            listOf(WarmupSet(40.0, 8), WarmupSet(55.0, 5), WarmupSet(65.0, 3), WarmupSet(75.0, 2)),
            logger.session.warmups,
        )
        assertEquals(LadderSpec.DefaultPlates, logger.session.current!!.plates)
    }

    @Test
    fun tickingWarmUpsLeavesRestAndTheWorkSetsAlone() = runTest {
        val logger = benchUp()
        val rest = logger.session.restEndsAt!!
        logger.type("90"); logger.done()
        logger.tickWarmup(1)
        assertEquals(2, logger.session.current!!.warmupsDone)
        assertEquals(rest, logger.session.restEndsAt)
        assertEquals(emptyList<Any>(), logger.session.current!!.sets)
        assertEquals("90", logger.session.entry!!.weight)

        // Ticking the last ticked one takes it back; ticks survive the app being killed.
        logger.tickWarmup(1)
        assertEquals(1, logger.logger().session.current!!.warmupsDone)
    }

    @Test
    fun warmUpsGoOnceTheFirstWorkSetIsLoggedAndDontCountAsSets() = runTest {
        val logger = benchUp()
        logger.type("90"); logger.done(); logger.type("5")
        logger.tickWarmup(3)
        logger.tick()
        assertEquals(1, logger.session.current!!.sets.size)
        assertEquals(emptyList<WarmupSet>(), logger.session.warmups)
    }

    @Test
    fun typingRepsNeverChangesTheWarmUpsAndAPlannedTwelveGetsTheMediumRamp() = runTest {
        programs.import(
            """
            { "format": 1, "title": "Volume", "blocks": [ { "id": "b", "name": "B", "weekStart": 1, "weekEnd": 4, "days": [
              { "id": "a", "name": "Day A", "groups": [
                { "slots": [ { "id": "bench", "exercise": "barbell_bench_press", "sets": [ { "reps": "10-12", "rir": 1, "rest": 120 } ] } ] }
              ] } ] } ] }
            """,
        )
        val logger = logger()
        logger.startDay(programs.active.first()!!, gymId = null)
        logger.type("90"); logger.done()
        val planned = logger.session.warmups
        // Planned for 10–12: the medium ramp, not the heavy one that one or five typed reps would give.
        assertEquals(listOf(WarmupSet(40.0, 8), WarmupSet(60.0, 5), WarmupSet(75.0, 3)), planned)
        logger.type("1")
        assertEquals(planned, logger.session.warmups)
        logger.type("12")
        assertEquals(planned, logger.session.warmups)
    }

    @Test
    fun anAdHocBenchRampsFromLastTimesRepsNotFromWhatIsTyped() = runTest {
        val first = logger()
        first.start()
        first.addExercise("barbell_bench_press")
        first.type("90"); first.done(); first.type("5"); first.selectRir(2); first.tick()
        first.finish()
        val logger = logger()
        logger.start()
        logger.addExercise("barbell_bench_press")
        // Last time was five reps: the heavy ramp, whatever is typed now.
        val heavy = listOf(WarmupSet(40.0, 8), WarmupSet(55.0, 5), WarmupSet(65.0, 3), WarmupSet(75.0, 2))
        assertEquals(heavy, logger.session.warmups)
        logger.type("12")
        assertEquals(heavy, logger.session.warmups)
    }

    @Test
    fun anAdHocBenchWithNoHistoryHasTheMediumRamp() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("barbell_bench_press")
        logger.type("90"); logger.done()
        val medium = logger.session.warmups
        assertEquals(3, medium.size)
        logger.type("5")
        assertEquals(medium, logger.session.warmups)
    }

    @Test
    fun anEzBarLoadsTheSharedPlatesOnItsOwnBar() = runTest {
        val gyms = com.hyperlog.app.gym.Gyms(db)
        val logger = logger()
        logger.start()
        logger.addExercise("ez_bar_curl")
        // The EZ bar is 10 kg until set otherwise, with the shared plates on it.
        assertEquals(LadderSpec.Plates(10.0, LadderSpec.DefaultPlates.pairs), logger.session.current!!.plates)
        assertEquals(listOf(10.0, 12.5, 15.0), logger.session.current!!.ladder.rungs.take(3))
        assertEquals(listOf(5.0, 2.5), logger.session.current!!.plates!!.perSide(25.0))

        gyms.setBar(com.hyperlog.app.library.Equipment.EzBar, 8.0)
        assertEquals(8.0, gyms.bars.first().getValue(com.hyperlog.app.library.Equipment.EzBar), 0.0)
        logger.load()
        assertEquals(8.0, logger.session.current!!.plates!!.baseKg, 0.0)
    }

    @Test
    fun dumbbellsHaveNoPlateCalculator() = runTest {
        val logger = logger()
        logger.startDay(programs.active.first()!!, gymId = null)
        assertNull(logger.session.current!!.plates)
    }

    @Test
    fun retypingTheWeightKeepsTheRampUntilThePadMovesOn() = runTest {
        val logger = benchUp()
        logger.type("90"); logger.done()
        val at90 = logger.session.warmups
        logger.focus(Field.Weight)
        logger.type("1")
        assertEquals(at90, logger.session.warmups)
        logger.type("00")
        assertEquals(at90, logger.session.warmups)
        // → moves on to the reps: now it ramps from 100.
        logger.done()
        assertEquals(WarmupSet(45.0, 8), logger.session.warmups.first())
        // Tapping the reps cell moves on too.
        logger.focus(Field.Weight); logger.type("60")
        assertEquals(WarmupSet(45.0, 8), logger.session.warmups.first())
        logger.focus(Field.Reps)
        assertEquals(WarmupSet(25.0, 8), logger.session.warmups.first())
    }

    @Test
    fun aWarmUpIsTypedOnThePadAndArrowTicksItAndMovesOn() = runTest {
        val logger = benchUp()
        logger.type("90"); logger.done()
        val rest = logger.session.restEndsAt
        logger.editWarmup(1)
        assertEquals(1, logger.session.warmupEdit!!.index)
        assertEquals(Field.Weight, logger.session.warmupEdit!!.entry.field)
        logger.type("50"); logger.done()
        assertEquals(Field.Reps, logger.session.warmupEdit!!.entry.field)
        logger.type("6"); logger.done()
        // Ticked with the ones before it, kept as typed, and the next warm-up is on the pad.
        assertEquals(WarmupSet(50.0, 6), logger.session.warmups[1])
        assertEquals(2, logger.session.current!!.warmupsDone)
        assertEquals(2, logger.session.warmupEdit!!.index)
        // No rest, no set, and the set to do keeps its entry.
        assertEquals(rest, logger.session.restEndsAt)
        assertEquals(emptyList<Any>(), logger.session.current!!.sets)
        assertEquals("90", logger.session.entry!!.weight)

        // → through the last two: the pad goes back to set 1.
        logger.done(); logger.done()
        logger.done(); logger.done()
        assertNull(logger.session.warmupEdit)
        assertEquals(4, logger.session.current!!.warmupsDone)
    }

    @Test
    fun typedWarmUpsLastForTheSessionAndNoLongerFollowTheRamp() = runTest {
        val logger = benchUp()
        logger.type("90"); logger.done()
        logger.editWarmup(0)
        logger.type("30"); logger.done(); logger.done()
        // The app is killed: the typed warm-up is still there.
        val again = logger.logger()
        assertEquals(WarmupSet(30.0, 8), again.session.warmups[0])
        assertEquals(1, again.session.current!!.warmupsDone)
        // A new weight for set 1 leaves the warm-up typed by hand alone.
        again.focus(Field.Weight); again.type("100"); again.done()
        assertEquals(WarmupSet(30.0, 8), again.session.warmups[0])
        assertEquals(4, again.session.warmups.size)
    }

    @Test
    fun aTypedWarmUpFreezesOnlyItselfAndTheOthersFollowThePlan() = runTest {
        val logger = benchUp()
        logger.type("90"); logger.done()
        logger.editWarmup(1)
        logger.type("50"); logger.done(); logger.done()
        assertEquals(WarmupSet(50.0, 5), logger.session.warmups[1])
        // Set 1 goes up to 100: warm-up 2 stays as typed, the others ramp from 100.
        logger.closeWarmup()
        logger.focus(Field.Weight); logger.type("100"); logger.done()
        assertEquals(listOf(WarmupSet(45.0, 8), WarmupSet(50.0, 5), WarmupSet(75.0, 3), WarmupSet(85.0, 2)), logger.session.warmups)
        // Still so after the app is killed.
        assertEquals(listOf(WarmupSet(45.0, 8), WarmupSet(50.0, 5), WarmupSet(75.0, 3), WarmupSet(85.0, 2)), logger.logger().session.warmups)
    }

    @Test
    fun aWarmUpTypedBackToItsRampFollowsTheRampAgain() = runTest {
        val logger = benchUp()
        logger.type("90"); logger.done()
        logger.editWarmup(1)
        logger.type("50"); logger.done(); logger.done()
        logger.editWarmup(1)
        logger.type("55"); logger.done(); logger.done()
        logger.closeWarmup()
        logger.focus(Field.Weight); logger.type("100"); logger.done()
        assertEquals(WarmupSet(60.0, 5), logger.session.warmups[1])
    }

    @Test
    fun theCloseKeyLeavesTheWarmUpAsItWas() = runTest {
        val logger = benchUp()
        logger.type("90"); logger.done()
        val before = logger.session.warmups
        logger.editWarmup(2)
        logger.type("70")
        logger.closeWarmup()
        assertNull(logger.session.warmupEdit)
        assertEquals(before, logger.session.warmups)
        assertEquals(0, logger.session.current!!.warmupsDone)
    }

    /** A finished day, so the next one's bench arrives with a weight and so with warm-ups. */
    private suspend fun benchWithHistory(): SessionLogger {
        val first = logger()
        first.startDay(programs.active.first()!!, gymId = null)
        first.type("10"); first.done(); first.closeWarmup(); first.type("12"); first.tick()
        first.type("90"); first.done(); first.type("5"); first.tick()
        first.finish()
        now += 86_400_000
        return logger().also { it.startDay(programs.active.first()!!, gymId = null) }
    }

    /** The raise comes up on its warm-up with last time's weight; this steps past it and logs its set. */
    private suspend fun SessionLogger.logRaise() {
        closeWarmup(); type("12"); tick()
    }

    @Test
    fun theNextExerciseOpensOnItsFirstWarmUpWhenItHasWarmUps() = runTest {
        val logger = benchWithHistory()
        logger.logRaise()
        assertEquals("Barbell Bench Press", logger.session.current!!.exercise.name)
        assertEquals(true, logger.session.warmups.isNotEmpty())
        assertEquals(0, logger.session.warmupEdit!!.index)
    }

    @Test
    fun anExerciseWithNoWarmUpsOpensOnSetOne() = runTest {
        // No weight yet, so no ramp: the bench comes up on set 1.
        val logger = benchUp()
        assertEquals(emptyList<WarmupSet>(), logger.session.warmups)
        assertNull(logger.session.warmupEdit)
    }

    @Test
    fun tickedWarmUpsAreSkippedWhenAnExerciseComesBackUp() = runTest {
        val logger = benchWithHistory()
        logger.logRaise()
        logger.tickWarmup(1)
        val bench = logger.session.current!!.id
        val raise = logger.session.exercises.first { it.id != bench }.id
        logger.switchTo(raise)
        assertNull(logger.session.warmupEdit)
        logger.switchTo(bench)
        assertEquals(2, logger.session.warmupEdit!!.index)
    }

    @Test
    fun openingAnExerciseFromTheListLandsOnItsFirstWarmUp() = runTest {
        val logger = benchWithHistory()
        val bench = logger.session.exercises.first { it.exercise.name == "Barbell Bench Press" }.id
        logger.open(bench)
        assertEquals(bench, logger.session.currentId)
        assertEquals(0, logger.session.warmupEdit!!.index)
    }

    private suspend fun SessionLogger.logger() = this@WarmupSessionTest.logger()
}
