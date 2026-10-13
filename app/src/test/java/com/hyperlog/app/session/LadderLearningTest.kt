package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.gym.LadderState
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

/**
 * A machine whose ladder is still a guess: its first use asks for the stack's labels, typed
 * weights are kept as typed, and the stack they show is offered once (SPEC, Learning a stack).
 */
@RunWith(RobolectricTestRunner::class)
class LadderLearningTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var gyms: Gyms
    private var now = 1_000_000L

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gyms = Gyms(db)
    }

    @After
    fun close() = db.close()

    private suspend fun logger(): SessionLogger {
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        return SessionLogger(db, clock = { now }).also { it.load() }
    }

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    private suspend fun SessionLogger.log(kg: String) {
        focus(com.hyperlog.app.domain.Field.Weight); type(kg); done(); type("10"); selectRir(2); done()
        now += 120_000
    }

    private val SessionLogger.current get() = state.value!!.current!!

    private suspend fun machine(state: LadderState): Pair<Long, Long> {
        val gym = gyms.addGym("Harbour")
        val item = gyms.addItem(EquipmentItem(gymId = gym, name = "Leg extension", ladder = LadderSpec.DefaultPinStack, state = state))
        return gym to item
    }

    @Test
    fun aGuessedLadderKeepsTypedWeightsWithoutACheck() = runTest {
        val (gym, _) = machine(LadderState.Guess)
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")
        logger.log("22.7")
        assertNull(logger.state.value!!.weightCheck)
        assertEquals(listOf(LoggedSet(22.7, 10, 0, 2)), logger.current.sets)
    }

    @Test
    fun threeWeightsOffTheGuessOfferTheStackTheyShow() = runTest {
        val (gym, _) = machine(LadderState.Guess)
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")
        logger.log("22.7"); logger.log("27.2")
        assertNull(logger.current.ladderOffer)
        logger.log("31.8")
        val offer = logger.current.ladderOffer!!
        assertEquals(WeightUnit.Lb, offer.unit)
        assertEquals(10.0, offer.step, 0.0)
    }

    @Test
    fun takingTheOfferMakesItTheMachinesLadder() = runTest {
        val (gym, item) = machine(LadderState.Guess)
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")
        logger.log("22.7"); logger.log("27.2"); logger.log("31.8")
        val offer = logger.current.ladderOffer!!
        logger.takeLadderOffer()
        val machine = gyms.gym(gym)!!.items.single { it.id == item }
        assertEquals(offer, machine.ladder)
        assertEquals(LadderState.Confirmed, machine.state)
        assertNull(logger.current.ladderOffer)
        assertTrue(logger.current.ladderKnown)
    }

    @Test
    fun aDeclinedOfferIsNeverMadeAgain() = runTest {
        val (gym, item) = machine(LadderState.Guess)
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")
        logger.log("22.7"); logger.log("27.2"); logger.log("31.8")
        logger.declineLadderOffer()
        assertNull(logger.current.ladderOffer)
        logger.log("36.3")
        assertNull(logger.current.ladderOffer)
        val machine = gyms.gym(gym)!!.items.single { it.id == item }
        assertEquals(LadderSpec.DefaultPinStack, machine.ladder)
        assertEquals(LadderState.Offered, machine.state)
    }

    @Test
    fun aConfirmedLadderIsNeverChanged() = runTest {
        val (gym, _) = machine(LadderState.Confirmed)
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")
        assertTrue(logger.current.ladderKnown)
        assertFalse(logger.current.needsLadder)
    }

    @Test
    fun anUnconfirmedMachineAsksForItsLabelsOnFirstUse() = runTest {
        val (gym, item) = machine(LadderState.Unconfirmed)
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")
        assertTrue(logger.current.needsLadder)
        assertNull(logger.state.value!!.entry)

        val read = LadderSpec.PinStack.steps(WeightUnit.Kg, 5.0, 5.0, 100.0, pinPlates = false)
        logger.setUpLadder(read)
        assertFalse(logger.current.needsLadder)
        val machine = gyms.gym(gym)!!.items.single { it.id == item }
        assertEquals(read, machine.ladder)
        assertEquals(LadderState.Confirmed, machine.state)
    }

    @Test
    fun skippingTheSetupKeepsTheGuessAndLearnsFromTyping() = runTest {
        val (gym, item) = machine(LadderState.Unconfirmed)
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")
        logger.skipLadderSetup()
        assertFalse(logger.current.needsLadder)
        assertFalse(logger.current.ladderKnown)
        assertEquals(LadderState.Guess, gyms.gym(gym)!!.items.single { it.id == item }.state)
    }
}
