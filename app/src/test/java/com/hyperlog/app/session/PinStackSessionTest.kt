package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A selectorised machine in a session: its pin, add-ons and the gym's pin plates (SPEC, Stacks with add-ons). */
@RunWith(RobolectricTestRunner::class)
class PinStackSessionTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var gyms: Gyms

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
        return SessionLogger(db).also { it.load() }
    }

    private suspend fun SessionLogger.type(text: String) = text.forEach {
        press(if (it == '.') PadKey.Dot else PadKey.Digit(it.digitToInt()))
    }

    @Test
    fun aGymStartsWithOneAndAQuarterAndTwoAndAHalfForThePin() = runTest {
        val id = gyms.addGym("Harbour")
        assertEquals(listOf(1.25, 2.5), gyms.gyms.first().single { it.id == id }.pinPlates)
        gyms.setPinPlates(id, listOf(2.5, 1.25, 1.25))
        assertEquals(listOf(2.5, 1.25, 1.25), gyms.gym(id)!!.pinPlates)
    }

    @Test
    fun theLadderStepsOverPinsAndWhatGoesOnThem() = runTest {
        val gym = gyms.addGym("Harbour")
        gyms.addItem(EquipmentItem(gymId = gym, name = "Leg extension", ladder = LadderSpec.DefaultPinStack))
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")

        val exercise = logger.state.value!!.current!!
        assertEquals(listOf(5.0, 6.25, 7.5, 8.75, 10.0), exercise.ladder.rungs.take(5))
        // + from 40 steps onto the gym's 1.25 kg plate on the pin.
        logger.type("40"); logger.press(PadKey.Plus)
        assertEquals("41.25", logger.state.value!!.entry!!.weight)
        assertEquals("pin 40 + 1.25 on the pin", exercise.pinLoad(41.25)!!.hint())
        assertEquals("pin 40", exercise.pinLoad(40.0)!!.hint())
    }

    @Test
    fun theGymsOwnPinPlatesAreTheOnesOffered() = runTest {
        val gym = gyms.addGym("Harbour")
        gyms.setPinPlates(gym, listOf(2.5))
        gyms.addItem(EquipmentItem(gymId = gym, name = "Leg extension", ladder = LadderSpec.DefaultPinStack))
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")
        val exercise = logger.state.value!!.current!!
        assertEquals(listOf(5.0, 7.5, 10.0), exercise.ladder.rungs.take(3))
        assertNull(exercise.pinLoad(6.25))
    }

    @Test
    fun builtInAddOnsShowInTheLoading() = runTest {
        val gym = gyms.addGym("Harbour")
        val stack = LadderSpec.PinStack(WeightUnit.Kg, start = 10.0, step = 10.0, top = 100.0, builtIn = listOf(2.5, 2.5), pinPlates = false)
        gyms.addItem(EquipmentItem(gymId = gym, name = "Leg extension", ladder = stack))
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")
        assertEquals("pin 30 + 2 add-ons", logger.state.value!!.current!!.pinLoad(35.0)!!.hint())
    }

    @Test
    fun otherLaddersHaveNoPinLoading() = runTest {
        val gym = gyms.addGym("Harbour")
        gyms.addItem(EquipmentItem(gymId = gym, name = "Leg extension", ladder = LadderSpec.lbStack()))
        val logger = logger()
        logger.start(gym)
        logger.addExercise("leg_extension")
        assertNull(logger.state.value!!.current!!.pinLoad(1.1))
    }
}
