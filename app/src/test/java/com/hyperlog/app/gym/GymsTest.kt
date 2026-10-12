package com.hyperlog.app.gym

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.session.SessionLogger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GymsTest {
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

    @Test
    fun aGymKeepsItsEquipmentAndLadders() = runTest {
        val id = gyms.addGym("Kallio")
        gyms.saveItem(EquipmentItem(gymId = id, name = "Cable station", ladder = LadderSpec.lbStack()))

        val gym = gyms.gyms.first().single()
        assertEquals("Kallio", gym.name)
        val item = gym.items.single()
        assertEquals("Cable station", item.name)
        assertEquals(listOf(1.1, 3.4, 5.7), item.ladder.ladder().rungs.take(3))
    }

    @Test
    fun editingAnItemReplacesIt() = runTest {
        val id = gyms.addGym("Kallio")
        gyms.saveItem(EquipmentItem(gymId = id, name = "Leg press", ladder = LadderSpec.lbStack()))
        val saved = gyms.gyms.first().single().items.single()
        gyms.saveItem(saved.copy(ladder = LadderSpec.Plates(50.0, listOf(20.0))))
        assertEquals(listOf(50.0, 90.0), gyms.gyms.first().single().items.single().ladder.ladder().rungs)
        gyms.deleteItem(saved.id)
        assertEquals(emptyList<EquipmentItem>(), gyms.gyms.first().single().items)
    }

    @Test
    fun dumbbellsAndPlatesAreSharedDefaultsUntilEdited() = runTest {
        assertEquals(LadderSpec.DefaultDumbbells, gyms.dumbbells.first())
        assertEquals(LadderSpec.DefaultPlates, gyms.plates.first())
        gyms.setDumbbells(LadderSpec.Explicit(listOf(5.0, 7.5)))
        gyms.setPlates(LadderSpec.Plates(15.0, listOf(10.0)))
        assertEquals(LadderSpec.Explicit(listOf(5.0, 7.5)), gyms.dumbbells.first())
        assertEquals(listOf(15.0, 35.0), gyms.plates.first().ladder().rungs)
    }

    /** 12 Oct 2026 is a Monday. */
    private fun at(day: Int) = LocalDate.of(2026, 10, 12).plusDays(day.toLong()).atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun aSessionStartsAtTheGymYouUsuallyTrainAtOnThatWeekday() = runTest {
        val kallio = gyms.addGym("Kallio")
        val toolo = gyms.addGym("Töölö")
        var now = 0L
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        val logger = SessionLogger(db, clock = { now }).also { it.load() }
        suspend fun train(gym: Long, day: Int) {
            now = at(day)
            logger.start(gym)
            logger.addExercise("db_bench_press")
            "25".forEach { logger.press(PadKey.Digit(it.digitToInt())) }
            logger.done(); logger.press(PadKey.Digit(8)); logger.selectRir(2); logger.done()
            logger.finish()
        }
        // Mondays at Kallio, Wednesdays at Töölö, and one Monday at Töölö.
        train(kallio, 0); train(toolo, 2); train(kallio, 7); train(toolo, 9); train(toolo, 14)

        assertEquals(kallio, gyms.usualGym(DayOfWeek.MONDAY, ZoneOffset.UTC))
        assertEquals(toolo, gyms.usualGym(DayOfWeek.WEDNESDAY, ZoneOffset.UTC))
        // A weekday with no history takes the gym trained at last.
        assertEquals(toolo, gyms.usualGym(DayOfWeek.FRIDAY, ZoneOffset.UTC))

        // A weekday plan beats history.
        gyms.setWeekdays(toolo, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        assertEquals(toolo, gyms.usualGym(DayOfWeek.MONDAY, ZoneOffset.UTC))
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), gyms.gyms.first().first { it.id == toolo }.weekdays)
    }

    @Test
    fun withNoHistoryTheFirstGymIsTheUsualOne() = runTest {
        assertEquals(null, gyms.usualGym(DayOfWeek.MONDAY, ZoneOffset.UTC))
        gyms.addGym("Töölö")
        val kallio = gyms.addGym("Kallio")
        assertEquals(kallio, gyms.usualGym(DayOfWeek.MONDAY, ZoneOffset.UTC))
    }
}
