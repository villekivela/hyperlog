package com.hyperlog.app.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * One shared cable station per gym: every cable exercise there defaults to it, and each keeps its
 * own history on it (SPEC, Gyms and equipment; ADR 0002).
 */
@RunWith(RobolectricTestRunner::class)
class CableStationTest {
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

    private suspend fun SessionLogger.log(kg: String, reps: String) {
        focus(Field.Weight); type(kg); done(); type(reps); selectRir(2); done()
        now += 120_000
    }

    private val SessionLogger.current get() = state.value!!.current!!

    @Test
    fun everyCableExerciseGoesStraightToTheStation() = runTest {
        val gym = gyms.addGym("Harbour")
        val station = gyms.addItem(EquipmentItem(gymId = gym, name = "Cable station", ladder = LadderSpec.lbStack(), cableStation = true))
        val logger = logger()
        logger.start(gym)
        logger.addExercise("cable_lateral_raise")
        assertFalse(logger.current.needsMachine)
        assertEquals(station, logger.current.machine!!.id)
        logger.addExercise("triceps_pressdown")
        assertEquals(station, logger.current.machine!!.id)
        assertEquals(station, gyms.remembered(gym, "triceps_pressdown"))
    }

    @Test
    fun eachExerciseKeepsItsOwnHistoryOnTheStation() = runTest {
        val gym = gyms.addGym("Harbour")
        gyms.addItem(EquipmentItem(gymId = gym, name = "Cable station", ladder = LadderSpec.lbStack(), cableStation = true))
        val logger = logger()
        logger.start(gym)
        logger.addExercise("cable_lateral_raise")
        logger.log("5.7", "12")
        logger.addExercise("triceps_pressdown")
        logger.log("23.8", "10")
        logger.finish()

        now += 86_400_000L
        logger.start(gym)
        logger.addExercise("cable_lateral_raise")
        assertEquals(listOf(LoggedSet(5.7, 12, 0, 2)), logger.current.lastTime)
        logger.addExercise("triceps_pressdown")
        assertEquals(listOf(LoggedSet(23.8, 10, 0, 2)), logger.current.lastTime)
    }

    @Test
    fun theFirstMachineAddedForACableExerciseBecomesTheStation() = runTest {
        val gym = gyms.addGym("Harbour")
        val logger = logger()
        logger.start(gym)
        logger.addExercise("cable_lateral_raise")
        assertTrue(logger.current.needsMachine)
        logger.addMachine("Cable station", LadderSpec.lbStack())
        val station = logger.current.machine!!
        assertTrue(station.cableStation)
        logger.addExercise("cable_face_pull")
        assertEquals(station.id, logger.current.machine!!.id)
    }

    @Test
    fun aRememberedMachineWinsOverTheStation() = runTest {
        val gym = gyms.addGym("Harbour")
        gyms.addItem(EquipmentItem(gymId = gym, name = "Cable station", ladder = LadderSpec.lbStack(), cableStation = true))
        val pulldown = gyms.addItem(EquipmentItem(gymId = gym, name = "Lat pulldown", ladder = LadderSpec.lbStack()))
        val logger = logger()
        gyms.remember(gym, "lat_pulldown", pulldown)
        logger.start(gym)
        logger.addExercise("lat_pulldown")
        assertEquals(pulldown, logger.current.machine!!.id)
    }

    @Test
    fun aMachineExerciseStillAsksWhichMachine() = runTest {
        val gym = gyms.addGym("Harbour")
        gyms.addItem(EquipmentItem(gymId = gym, name = "Cable station", ladder = LadderSpec.lbStack(), cableStation = true))
        val logger = logger()
        logger.start(gym)
        logger.addExercise("pec_deck")
        assertTrue(logger.current.needsMachine)
    }
}
