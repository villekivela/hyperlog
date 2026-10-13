package com.hyperlog.app.gym

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.library.toExercise
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.session.SessionLogger
import kotlinx.coroutines.flow.first
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
import java.io.File

/** "Add the equipment your program uses": a gym set up from the active program in one go (SPEC, Gyms and equipment). */
@RunWith(RobolectricTestRunner::class)
class ProgramSetupTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var gyms: Gyms
    private lateinit var programs: Programs
    private var gymId = 0L
    private var now = 1_760_000_000_000L

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.parse(File("src/main/assets/exercises.json").readText()))
        programs = Programs(db, clock = { now })
        programs.import(File("src/main/assets/${StarterProgram.ASSET}").readText())
        gyms = Gyms(db)
        gymId = gyms.addGym("Harbour")
    }

    @After
    fun close() = db.close()

    private suspend fun setup(): ProgramSetup {
        val library = db.exercises().all().associate { it.id to it.toExercise() }
        return programSetup(programs.active.first()!!.program, library, gyms.gym(gymId)!!)
    }

    @Test
    fun theStarterProgramNeedsItsMachinesAndOneCableStation() = runTest {
        val setup = setup()
        assertTrue("leg_extension" in setup.machines.map { it.id })
        assertTrue("hack_squat" in setup.machines.map { it.id })
        assertTrue(setup.machines.all { it.equipment == Equipment.Machine || it.equipment == Equipment.SmithMachine })
        assertTrue("cable_lateral_raise" in setup.cables.map { it.id })
        assertTrue(setup.newStation)
    }

    @Test
    fun addingMakesAMachineEachAndOneSharedStationOnTheGymsStack() = runTest {
        gyms.addProgramEquipment(gymId, setup(), LadderSpec.DefaultPinStack)
        val gym = gyms.gym(gymId)!!
        val extension = gym.items.single { it.name == "Leg Extension" }
        assertEquals(LadderSpec.DefaultPinStack, extension.ladder)
        assertEquals(LadderState.Unconfirmed, extension.state)
        // A plate-loaded machine gets the shared plates from a zero start.
        assertTrue(gym.items.single { it.name == "Hack Squat" }.ladder is LadderSpec.Plates)
        val stations = gym.items.filter { it.cableStation }
        assertEquals(1, stations.size)
        assertEquals(stations.single().id, gyms.remembered(gymId, "cable_lateral_raise"))
        assertEquals(stations.single().id, gyms.remembered(gymId, "triceps_pressdown"))
        assertEquals(extension.id, gyms.remembered(gymId, "leg_extension"))
        assertEquals(LadderSpec.DefaultPinStack, gym.defaultStack)
    }

    @Test
    fun afterwardsWhichMachineDoesntAsk() = runTest {
        gyms.addProgramEquipment(gymId, setup(), LadderSpec.DefaultPinStack)
        val logger = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        logger.startDay(programs.active.first()!!, gymId)
        assertTrue(logger.state.value!!.exercises.none { it.needsMachine })
    }

    @Test
    fun runningItAgainAddsNothing() = runTest {
        gyms.addProgramEquipment(gymId, setup(), LadderSpec.DefaultPinStack)
        val count = gyms.gym(gymId)!!.items.size
        val again = setup()
        assertTrue(again.isEmpty)
        gyms.addProgramEquipment(gymId, again, LadderSpec.DefaultPinStack)
        assertEquals(count, gyms.gym(gymId)!!.items.size)
    }

    @Test
    fun anLbStackGymGetsLbStacks() = runTest {
        gyms.addProgramEquipment(gymId, setup(), LadderSpec.LbPinStack)
        val extension = gyms.gym(gymId)!!.items.single { it.name == "Leg Extension" }
        assertEquals(WeightUnit.Lb, (extension.ladder as LadderSpec.PinStack).unit)
    }

    @Test
    fun aMachineAlreadyThereIsUsedAndAMissingOneSkipped() = runTest {
        val own = gyms.addItem(EquipmentItem(gymId = gymId, name = "Leg extension", ladder = LadderSpec.lbStack()))
        gyms.markMissing(gymId, "pec_deck")
        val setup = setup()
        assertFalse("pec_deck" in setup.machines.map { it.id })
        gyms.addProgramEquipment(gymId, setup, LadderSpec.DefaultPinStack)
        assertEquals(own, gyms.remembered(gymId, "leg_extension"))
        assertEquals(1, gyms.gym(gymId)!!.items.count { it.name.equals("Leg extension", ignoreCase = true) })
        assertNull(gyms.remembered(gymId, "pec_deck"))
    }

    @Test
    fun aGymWithAStationUsesIt() = runTest {
        val station = gyms.addItem(EquipmentItem(gymId = gymId, name = "Cables", ladder = LadderSpec.lbStack(), cableStation = true))
        val setup = setup()
        assertFalse(setup.newStation)
        gyms.addProgramEquipment(gymId, setup, LadderSpec.DefaultPinStack)
        assertEquals(station, gyms.remembered(gymId, "cable_lateral_raise"))
        assertEquals(1, gyms.gym(gymId)!!.items.count { it.cableStation })
    }
}
