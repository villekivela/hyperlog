package com.hyperlog.app.gym

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.session.SessionLogger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Each gym's own plates, as pairs per size, which its plate-loaded machines and bars load (SPEC, Gyms and equipment). */
@RunWith(RobolectricTestRunner::class)
class GymPlatesTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var gyms: Gyms

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.parse(File("src/main/assets/exercises.json").readText()))
        gyms = Gyms(db)
    }

    @After
    fun close() = db.close()

    private val twentyPairs = List(8) { 20.0 }

    @Test
    fun aNewGymHasATypicalCommercialGymsPlates() = runTest {
        val gym = gyms.gym(gyms.addGym("Harbour"))!!
        assertEquals(mapOf(20.0 to 8, 15.0 to 2, 10.0 to 2, 5.0 to 2, 2.5 to 2, 1.25 to 2), gym.plates.groupingBy { it }.eachCount())
    }

    @Test
    fun aGymKeepsThePlatesSetForIt() = runTest {
        val id = gyms.addGym("Harbour")
        gyms.setPlates(id, listOf(20.0, 20.0, 10.0))
        assertEquals(listOf(20.0, 20.0, 10.0), gyms.gym(id)!!.plates)
    }

    @Test
    fun aPlateLoadedMachineLoadsItsGymsPlatesWellPast187kg() = runTest {
        val id = (gyms.import(
            """{ "format": "hyperlog-gym", "version": 1, "name": "Harbour",
                 "equipment": [ { "name": "Leg press", "kind": "plate_loaded", "serves": ["leg_press"] } ] }""",
        ) as Gyms.Import.Added).gymId
        val logger = SessionLogger(db).also { it.load() }
        logger.start(id)
        logger.addExercise("leg_press")
        // 8 pairs of 20 and two each of 15, 10, 5, 2.5 and 1.25: 455 kg on an empty sled.
        assertEquals(455.0, logger.state.value!!.current!!.ladder.rungs.last(), 0.0)

        gyms.setPlates(id, listOf(20.0, 20.0, 10.0))
        logger.load()
        assertEquals(100.0, logger.state.value!!.current!!.ladder.rungs.last(), 0.0)
        assertEquals(listOf(20.0, 20.0, 10.0), logger.state.value!!.current!!.plates!!.perSide(100.0))
    }

    @Test
    fun theBarbellLoadsTheSessionGymsPlates() = runTest {
        val id = gyms.addGym("Harbour")
        gyms.setPlates(id, twentyPairs)
        val logger = SessionLogger(db).also { it.load() }
        logger.start(id)
        logger.addExercise("barbell_bench_press")
        assertEquals(LadderSpec.Plates(20.0, twentyPairs), logger.state.value!!.current!!.plates)
        assertEquals(340.0, logger.state.value!!.current!!.ladder.rungs.last(), 0.0)
    }

    @Test
    fun aSessionWithNoGymLoadsTheTypicalPlates() = runTest {
        val logger = SessionLogger(db).also { it.load() }
        logger.start()
        logger.addExercise("barbell_bench_press")
        assertEquals(LadderSpec.Plates(20.0, LadderSpec.DefaultGymPlates), logger.state.value!!.current!!.plates)
    }
}
