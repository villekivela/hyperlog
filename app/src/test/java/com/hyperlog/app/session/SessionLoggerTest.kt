package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionLoggerTest {
    private lateinit var db: HyperlogDatabase
    private var now = 1_000_000L

    @Before
    fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() = db.close()

    private suspend fun logger(): SessionLogger {
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        return SessionLogger(db, clock = { now }).also { it.load() }
    }

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private val SessionLogger.session get() = state.value!!

    @Test
    fun logsThreeSetsWithWeightFullAndPartialRepsAndRirUsingOnlyThePad() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("db_bench_press")

        logger.type("25"); logger.done()
        logger.type("10"); logger.selectRir(2); logger.done()
        // The next set comes with the last one's weight and RIR: type the reps, then ✓, after resting.
        now += 120_000
        logger.type("10"); logger.done()
        now += 120_000
        logger.type("9"); logger.partialMode(true); logger.type("4"); logger.selectRir(0); logger.done()

        val exercise = logger.session.exercises.single()
        assertEquals("DB Bench Press", exercise.exercise.name)
        assertEquals(
            listOf(LoggedSet(25.0, 10, 0, 2), LoggedSet(25.0, 10, 0, 2), LoggedSet(25.0, 9, 4, 0)),
            exercise.sets,
        )
    }

    @Test
    fun aPlainBodyweightExerciseStartsOnRepsAtNoWeightAndLogs() = runTest {
        // Found on the phone: Reverse Nordic's ✓ stayed disabled until a weight above 0 was typed.
        val logger = logger()
        logger.start()
        logger.addExercise("reverse_nordic")
        assertEquals(Field.Reps, logger.session.entry!!.field)
        assertEquals("0", logger.session.entry!!.weight)

        logger.type("8"); logger.selectRir(2); logger.done()

        assertEquals(listOf(LoggedSet(0.0, 8, 0, 2)), logger.session.exercises.single().sets)
    }

    @Test
    fun aWeightedBodyweightSetCanBeLoggedWithNothingOnTheBelt() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("weighted_pullup")
        logger.type("0"); logger.done()
        logger.type("6"); logger.selectRir(1); logger.done()

        assertEquals(listOf(LoggedSet(0.0, 6, 0, 1)), logger.session.exercises.single().sets)
    }

    @Test
    fun aReopenedLoggerRestoresTheSessionExactly() = runTest {
        val first = logger()
        first.start()
        first.addExercise("leg_extension")
        first.type("60"); first.done(); first.type("12"); first.selectRir(1); first.done()
        first.type("1"); first.partialMode(true); first.type("5")
        first.setPadVisible(false)

        val reopened = logger()

        assertEquals(first.session, reopened.session)
        val entry = reopened.session.entry!!
        assertEquals("1", entry.full)
        assertEquals("5", entry.partial)
        assertEquals(false, reopened.session.padVisible)
    }

    @Test
    fun theFirstSetStartsOnTheWeightAndLaterSetsFollowTheLastOne() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        assertEquals(Field.Weight, logger.session.entry!!.field)

        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()

        val entry = logger.session.entry!!
        assertEquals(Field.Reps, entry.field)
        assertEquals("60", entry.weight)
        assertEquals("", entry.full)
        assertEquals(1, entry.rir)
    }

    @Test
    fun lastTimeComesFromThePreviousSessionWithThatExercise() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()
        logger.finish()

        now += 86_400_000
        logger.start()
        logger.addExercise("leg_extension")

        val exercise = logger.session.exercises.single()
        assertEquals(listOf(LoggedSet(60.0, 12, 0, 1)), exercise.lastTime)
        assertEquals(emptyList<LoggedSet>(), exercise.sets)
        assertEquals("60", logger.session.entry!!.weight)
    }

    @Test
    fun aSecondTapOnTheDoneKeyRightAfterLoggingLogsNothing() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("cable_lateral_raise")
        logger.type("7.5"); logger.done(); logger.type("12"); logger.selectRir(2); logger.done()

        // A stutter on ✓, 150 ms after the set was logged.
        logger.type("12")
        now += 150
        logger.done()
        assertEquals(1, logger.session.current!!.sets.size)

        // A deliberate ✓ a moment later logs the next set as usual.
        now += 1_000
        logger.done()
        assertEquals(2, logger.session.current!!.sets.size)
    }

    @Test
    fun loggingASetStartsRest() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        assertNull(logger.session.restEndsAt)

        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()

        assertEquals(now + SessionLogger.DEFAULT_REST_MS, logger.session.restEndsAt)
    }

    @Test
    fun theSessionListSwitchesBetweenExercisesKeepingEachOnesSets() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()
        logger.addExercise("seated_leg_curl")
        assertEquals("seated_leg_curl", logger.session.current!!.exercise.id)

        logger.switchTo(logger.session.exercises.first().id)

        assertEquals("leg_extension", logger.session.current!!.exercise.id)
        assertEquals("60", logger.session.entry!!.weight)
    }

    @Test
    fun finishingEndsTheSessionAndSummarisesIt() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()
        logger.addExercise("seated_leg_curl")

        val summary = logger.finish()!!

        assertNull(logger.state.value)
        assertNull(logger().state.value)
        // Exercises with nothing logged are left out.
        assertEquals(listOf(ExerciseSummary("Leg Extension", listOf(LoggedSet(60.0, 12, 0, 1)))), summary.exercises)
    }

    @Test
    fun aFinishedSessionCanBeResumedAsItWas() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        logger.type("60"); logger.done(); logger.type("12"); logger.selectRir(1); logger.done()
        val summary = logger.finish()!!

        now += 60_000
        logger.resume(summary.sessionId)

        val session = logger.session
        assertEquals(listOf(LoggedSet(60.0, 12, 0, 1)), session.current!!.sets)
        assertEquals("60", session.entry!!.weight)
        // It runs again, so finishing it later gives a new summary.
        assertEquals(1, logger.finish()!!.exercises.single().sets.size)
    }

    @Test
    fun finishingWithNothingLoggedDiscardsTheSession() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")

        assertNull(logger.finish())
        assertNull(logger.state.value)
    }

    @Test
    fun aWeightTenTimesTheLastSetNeedsASecondTick() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("db_bench_press")
        logger.type("25"); logger.done(); logger.type("10"); logger.selectRir(2); logger.done()
        now += 120_000

        logger.focus(Field.Weight); logger.type("255"); logger.done(); logger.type("10"); logger.done()

        assertEquals(1, logger.session.current!!.sets.size)
        val check = logger.session.weightCheck!!
        assertEquals("10× last set", check.reason)
        assertEquals(listOf(22.5, 25.0, 27.5), check.options)

        // A stutter right after the warning doesn't keep the weight; a deliberate ✓ does.
        logger.done()
        assertEquals(1, logger.session.current!!.sets.size)
        now += 2_000
        logger.done()
        assertEquals(LoggedSet(255.0, 10, 0, 2, kept = true), logger.session.current!!.sets.last())
        assertNull(logger.session.weightCheck)
    }

    /** Types [weight] over the prefill and keeps it past the weight check with a second ✓. */
    private suspend fun SessionLogger.keep(weight: String) {
        focus(Field.Weight); type(weight); done(); type("12"); done()
        now += 2_000
        done()
        now += 120_000
    }

    @Test
    fun aKeptTypoIsNeitherThePrefillNorTheReferenceForTheNextSet() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("cable_lateral_raise")
        logger.logSet("7.5", "12", 2)
        logger.finish()

        now += 86_400_000
        logger.start()
        logger.addExercise("cable_lateral_raise")
        logger.closeWarmup()
        logger.keep("75")
        assertEquals(75.0, logger.session.current!!.sets.single().weightKg, 0.001)

        // The next set prefills last time's weight, and logging it raises no check.
        assertEquals("7.5", logger.session.entry!!.weight)
        logger.type("12"); logger.done()
        assertNull(logger.session.weightCheck)
        assertEquals(listOf(75.0, 7.5), logger.session.current!!.sets.map { it.weightKg })
        assertEquals("7.5", logger.session.entry!!.weight)
    }

    @Test
    fun aWeightKeptTwiceInARowIsTakenAsReal() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("cable_lateral_raise")
        logger.logSet("7.5", "12", 2)
        logger.keep("20")
        logger.keep("20")
        assertEquals("20", logger.session.entry!!.weight)
        logger.type("12"); logger.done()
        assertNull(logger.session.weightCheck)
        assertEquals(4, logger.session.current!!.sets.size)
    }

    @Test
    fun pickingAnOfferedWeightClearsTheCheck() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("db_bench_press")
        logger.type("23"); logger.done(); logger.type("10"); logger.selectRir(2); logger.done()
        assertEquals("Not on this equipment", logger.session.weightCheck!!.reason)

        logger.pickWeight(22.5)
        assertNull(logger.session.weightCheck)
        logger.done()
        assertEquals(listOf(LoggedSet(22.5, 10, 0, 2)), logger.session.current!!.sets)
    }

    @Test
    fun dumbbellsStepOverTheGymsRack() = runTest {
        val gym = Gyms(db).addGym("Home")
        Gyms(db).setDumbbells(gym, LadderSpec.Explicit(listOf(10.0, 12.5, 15.0)))
        val logger = logger()
        logger.start(gym)
        logger.addExercise("db_bench_press")
        logger.type("10"); logger.press(PadKey.Plus)
        assertEquals("12.5", logger.session.entry!!.weight)
        assertEquals(listOf(10.0, 12.5, 15.0), logger.session.current!!.ladder.rungs)
    }

    private suspend fun SessionLogger.logSet(weight: String, reps: String, rir: Int) {
        type(weight); done(); type(reps); if (state.value?.entry?.rir != rir) selectRir(rir); done()
        now += 120_000
    }

    @Test
    fun aMachineNamedForTheExerciseIsTakenWithoutAsking() = runTest {
        val gyms = Gyms(db)
        val central = gyms.addGym("Central")
        gyms.addItem(EquipmentItem(gymId = central, name = "Leg press", ladder = LadderSpec.Plates(40.0, listOf(20.0, 20.0))))
        gyms.addItem(EquipmentItem(gymId = central, name = "Leg extension", ladder = LadderSpec.Steps(5.0, 5.0, WeightUnit.Kg, 20)))
        val logger = logger()
        logger.start(central)
        logger.addExercise("leg_extension")
        val current = logger.session.current!!
        assertFalse(current.needsMachine)
        assertEquals("Leg extension", current.machine!!.name)
        // And it is remembered, as a picked one is.
        assertEquals(current.machine!!.id, gyms.remembered(central, "leg_extension"))
    }

    @Test
    fun theFirstLegExtensionAtANewGymAsksWhichMachineAndTheNextTimeItDoesNot() = runTest {
        val central = Gyms(db).addGym("Central")
        val logger = logger()
        logger.start(central)
        logger.addExercise("leg_extension")

        assertTrue(logger.session.current!!.needsMachine)
        assertNull(logger.session.entry)

        logger.addMachine("Leg extension", LadderSpec.Steps(5.0, 5.0, WeightUnit.Kg, 20))
        val current = logger.session.current!!
        assertFalse(current.needsMachine)
        assertEquals("Leg extension", current.machine!!.name)
        assertEquals(listOf(5.0, 10.0, 15.0), current.ladder.rungs.take(3))
        assertTrue(current.ladderKnown)
        logger.logSet("60", "12", 1)
        logger.finish()

        now += 86_400_000
        logger.start(central)
        logger.addExercise("leg_extension")
        val again = logger.session.current!!
        assertFalse(again.needsMachine)
        assertEquals("Leg extension", again.machine!!.name)
        assertEquals(listOf(LoggedSet(60.0, 12, 0, 1)), again.lastTime)
        assertEquals("60", logger.session.entry!!.weight)
    }

    @Test
    fun aMachineCanBePickedFromTheGymsEquipment() = runTest {
        val gyms = Gyms(db)
        val central = gyms.addGym("Central")
        gyms.saveItem(EquipmentItem(gymId = central, name = "Cable station", ladder = LadderSpec.lbStack()))
        val logger = logger()
        logger.start(central)
        logger.addExercise("cable_lateral_raise")

        logger.chooseMachine(logger.session.gym!!.items.single().id)

        assertEquals("Cable station", logger.session.current!!.machine!!.name)
        assertEquals(listOf(1.1, 3.4, 5.7), logger.session.current!!.ladder.rungs.take(3))
    }

    @Test
    fun freeWeightHistoryFollowsYouAcrossGymsButACableStationHasItsOwn() = runTest {
        val gyms = Gyms(db)
        val central = gyms.addGym("Central")
        val riverside = gyms.addGym("Riverside")
        val logger = logger()
        logger.start(central)
        logger.addExercise("db_bench_press")
        logger.logSet("25", "10", 2)
        logger.addExercise("cable_lateral_raise")
        logger.addMachine("Cable station", LadderSpec.lbStack())
        logger.logSet("7.9", "12", 1)
        logger.finish()

        now += 86_400_000
        logger.start(riverside)
        logger.addExercise("db_bench_press")
        assertEquals(listOf(LoggedSet(25.0, 10, 0, 2)), logger.session.current!!.lastTime)
        logger.addExercise("cable_lateral_raise")
        assertTrue(logger.session.current!!.needsMachine)
        logger.addMachine("Cable tower", LadderSpec.Steps(2.5, 2.5, WeightUnit.Kg, 20))
        assertEquals(emptyList<LoggedSet>(), logger.session.current!!.lastTime)
        assertEquals("", logger.session.entry!!.weight)
        logger.logSet("5", "12", 1)
        logger.finish()

        // Back at the first gym, the first station's history is still its own.
        now += 86_400_000
        logger.start(central)
        logger.addExercise("cable_lateral_raise")
        assertEquals(listOf(LoggedSet(7.9, 12, 0, 1)), logger.session.current!!.lastTime)
    }

    @Test
    fun aSessionWithoutAGymDoesNotAsk() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("leg_extension")
        assertFalse(logger.session.current!!.needsMachine)
        assertFalse(logger.session.current!!.ladderKnown)
        assertNull(logger.session.gym)
    }

    @Test
    fun theMachineCanBeChangedUntilASetIsLogged() = runTest {
        val central = Gyms(db).addGym("Central")
        val logger = logger()
        logger.start(central)
        logger.addExercise("leg_extension")
        logger.addMachine("Leg extension", LadderSpec.lbStack())

        logger.changeMachine()
        assertTrue(logger.session.current!!.needsMachine)
        logger.addMachine("Leg extension 2", LadderSpec.lbStack())
        assertEquals("Leg extension 2", logger.session.current!!.machine!!.name)

        logger.logSet("7.9", "12", 1)
        logger.changeMachine()
        assertEquals("Leg extension 2", logger.session.current!!.machine!!.name)
    }
}
