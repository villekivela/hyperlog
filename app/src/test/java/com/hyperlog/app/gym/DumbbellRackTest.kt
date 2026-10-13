package com.hyperlog.app.gym

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.session.SessionLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each gym has its own dumbbell rack; history stays shared across gyms (SPEC, Gyms and equipment). */
@RunWith(RobolectricTestRunner::class)
class DumbbellRackTest {
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

    @Test
    fun aNewGymHasTheUsualRack() = runTest {
        val id = gyms.addGym("Harbour")
        assertEquals(LadderSpec.DefaultDumbbells, gyms.gym(id)!!.dumbbells)
    }

    @Test
    fun eachGymKeepsItsOwnRack() = runTest {
        val harbour = gyms.addGym("Harbour")
        val home = gyms.addGym("Home")
        gyms.setDumbbells(home, LadderSpec.Explicit(listOf(5.0, 10.0, 15.0)))
        val byName = gyms.gyms.first().associateBy { it.name }
        assertEquals(LadderSpec.Explicit(listOf(5.0, 10.0, 15.0)), byName.getValue("Home").dumbbells)
        assertEquals(LadderSpec.DefaultDumbbells, byName.getValue("Harbour").dumbbells)
        assertEquals(harbour, byName.getValue("Harbour").id)
    }

    @Test
    fun aSessionStepsOverItsGymsRack() = runTest {
        val home = gyms.addGym("Home")
        gyms.setDumbbells(home, LadderSpec.Explicit(listOf(10.0, 12.5, 15.0)))
        val logger = logger()
        logger.start(home)
        logger.addExercise("db_bench_press")
        logger.type("10"); logger.press(PadKey.Plus)
        assertEquals("12.5", logger.state.value!!.entry!!.weight)
        assertEquals(listOf(10.0, 12.5, 15.0), logger.state.value!!.current!!.ladder.rungs)
    }

    @Test
    fun withoutAGymTheUsualRackStandsIn() = runTest {
        val logger = logger()
        logger.start()
        logger.addExercise("db_bench_press")
        assertEquals(LadderSpec.DefaultDumbbells.ladder().rungs, logger.state.value!!.current!!.ladder.rungs)
    }

    @Test
    fun dumbbellHistoryIsSharedAcrossGyms() = runTest {
        val harbour = gyms.addGym("Harbour")
        val home = gyms.addGym("Home")
        gyms.setDumbbells(home, LadderSpec.Explicit(listOf(20.0, 22.0, 24.0)))
        val logger = logger()
        logger.start(harbour)
        logger.addExercise("db_bench_press")
        logger.type("22.5"); logger.done(); logger.type("8"); logger.selectRir(2); logger.done()
        logger.finish()
        now += 86_400_000L
        logger.start(home)
        logger.addExercise("db_bench_press")
        assertEquals(listOf(LoggedSet(22.5, 8, 0, 2)), logger.state.value!!.current!!.lastTime)
    }
}
