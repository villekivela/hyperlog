package com.hyperlog.app.gym

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.PadKey
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.session.SessionLogger
import com.hyperlog.app.week.WeekdayPlan
import com.hyperlog.app.week.WeekdayPlans
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
        val id = gyms.addGym("Central")
        gyms.saveItem(EquipmentItem(gymId = id, name = "Cable station", ladder = LadderSpec.lbStack()))

        val gym = gyms.gyms.first().single()
        assertEquals("Central", gym.name)
        val item = gym.items.single()
        assertEquals("Cable station", item.name)
        assertEquals(listOf(1.1, 3.4, 5.7), item.ladder.ladder().rungs.take(3))
    }

    @Test
    fun renamingAnItemKeepsWhatItIs() = runTest {
        val id = gyms.addGym("Central")
        val rack = gyms.addItem(EquipmentItem(gymId = id, name = "Kyykkytelineet", ladder = LadderSpec.Explicit(emptyList()), kind = EquipmentItem.RACK))
        val station = gyms.addItem(EquipmentItem(gymId = id, name = "Talja", ladder = LadderSpec.DefaultPinStack, state = LadderState.Unconfirmed, cableStation = true))
        gyms.renameItem(rack, "Squat rack")
        gyms.renameItem(station, " Cable station ")
        val gym = gyms.gym(id)!!
        assertEquals(listOf("Squat rack" to EquipmentItem.RACK), gym.kit.map { it.name to it.kind })
        val cable = gym.items.single()
        assertEquals("Cable station", cable.name)
        assertEquals(true, cable.cableStation)
        assertEquals(LadderState.Unconfirmed, cable.state)
    }

    @Test
    fun editingAnItemReplacesIt() = runTest {
        val id = gyms.addGym("Central")
        gyms.saveItem(EquipmentItem(gymId = id, name = "Leg press", ladder = LadderSpec.lbStack()))
        val saved = gyms.gyms.first().single().items.single()
        gyms.saveItem(saved.copy(ladder = LadderSpec.Plates(50.0, listOf(20.0))))
        assertEquals(listOf(50.0, 90.0), gyms.gyms.first().single().items.single().ladder.ladder().rungs)
        gyms.deleteItem(saved.id)
        assertEquals(emptyList<EquipmentItem>(), gyms.gyms.first().single().items)
    }

    @Test
    fun anUnusedMachineOrGymIsDeletedOutright() = runTest {
        val id = gyms.addGym("Central")
        val item = gyms.addItem(EquipmentItem(gymId = id, name = "Leg press", ladder = LadderSpec.lbStack()))
        gyms.deleteItem(item)
        gyms.deleteGym(id)
        assertEquals(0, db.openHelper.readableDatabase.query("SELECT * FROM equipment_item").use { it.count })
        assertEquals(0, db.openHelper.readableDatabase.query("SELECT * FROM gym").use { it.count })
    }

    @Test
    fun theBarbellIsTwentyKgUntilSetAndLoadsTheTypicalPlates() = runTest {
        assertEquals(20.0, gyms.barbell.first(), 0.0)
        gyms.setBarbell(15.0)
        assertEquals(15.0, gyms.barbell.first(), 0.0)
        assertEquals(LadderSpec.Plates(15.0, LadderSpec.DefaultGymPlates), gyms.shared().plates)
    }

    /** 12 Oct 2026 is a Monday. */
    private fun at(day: Int) = LocalDate.of(2026, 10, 12).plusDays(day.toLong()).atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun aSessionStartsAtTheGymYouUsuallyTrainAtOnThatWeekday() = runTest {
        val central = gyms.addGym("Central")
        val riverside = gyms.addGym("Riverside")
        var now = 0L
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        val logger = SessionLogger(db, clock = { now }).also { it.load() }
        suspend fun train(gym: Long, day: Int) {
            now = at(day)
            logger.start(gym)
            logger.addExercise("db_bench_press")
            logger.closeWarmup()
            "25".forEach { logger.press(PadKey.Digit(it.digitToInt())) }
            logger.done(); logger.press(PadKey.Digit(8)); logger.selectRir(2); logger.done()
            logger.finish()
        }
        // Mondays at Central, Wednesdays at Riverside, and one Monday at Riverside.
        train(central, 0); train(riverside, 2); train(central, 7); train(riverside, 9); train(riverside, 14)

        assertEquals(UsualGym(central, UsualGym.Why.Weekday), gyms.usualGym(DayOfWeek.MONDAY, ZoneOffset.UTC))
        assertEquals(UsualGym(riverside, UsualGym.Why.Weekday), gyms.usualGym(DayOfWeek.WEDNESDAY, ZoneOffset.UTC))
        // A weekday with no history takes the gym trained at last, and doesn't call it usual on Fridays.
        assertEquals(UsualGym(riverside, UsualGym.Why.Last), gyms.usualGym(DayOfWeek.FRIDAY, ZoneOffset.UTC))

        // A training day naming a gym beats history.
        WeekdayPlans(db).set(WeekdayPlan(mapOf(DayOfWeek.MONDAY to riverside, DayOfWeek.THURSDAY to riverside)))
        assertEquals(UsualGym(riverside, UsualGym.Why.Weekday), gyms.usualGym(DayOfWeek.MONDAY, ZoneOffset.UTC))
        // One naming no gym leaves it to history when there are several gyms.
        WeekdayPlans(db).set(WeekdayPlan(mapOf(DayOfWeek.MONDAY to null)))
        assertEquals(UsualGym(central, UsualGym.Why.Weekday), gyms.usualGym(DayOfWeek.MONDAY, ZoneOffset.UTC))
    }

    @Test
    fun withOneGymEveryTrainingDayIsAtIt() = runTest {
        val central = gyms.addGym("Central")
        WeekdayPlans(db).set(WeekdayPlan(mapOf(DayOfWeek.TUESDAY to null)))
        assertEquals(UsualGym(central, UsualGym.Why.Weekday), gyms.usualGym(DayOfWeek.TUESDAY, ZoneOffset.UTC))
        // A day you don't train on isn't usual anywhere.
        assertEquals(UsualGym(central, UsualGym.Why.First), gyms.usualGym(DayOfWeek.WEDNESDAY, ZoneOffset.UTC))
    }

    @Test
    fun deletingAGymKeepsItsTrainingDaysWithNoGym() = runTest {
        val central = gyms.addGym("Central")
        val hotel = gyms.addGym("Hotel")
        val plans = WeekdayPlans(db)
        plans.set(WeekdayPlan(mapOf(DayOfWeek.MONDAY to central, DayOfWeek.FRIDAY to hotel)))
        // Trained at, so deleting it archives it rather than removing the row.
        db.sessions().insertSession(com.hyperlog.app.data.SessionEntity(startedAt = at(4), finishedAt = at(4) + 3_600_000, gymId = hotel))
        gyms.deleteGym(hotel)
        assertEquals(WeekdayPlan(mapOf(DayOfWeek.MONDAY to central, DayOfWeek.FRIDAY to null)), plans.plan.first())
    }

    @Test
    fun withNoHistoryTheFirstGymIsTheUsualOne() = runTest {
        assertEquals(null, gyms.usualGym(DayOfWeek.MONDAY, ZoneOffset.UTC))
        gyms.addGym("Riverside")
        val central = gyms.addGym("Central")
        // A new gym with no history is only the first one, not the usual one.
        assertEquals(UsualGym(central, UsualGym.Why.First), gyms.usualGym(DayOfWeek.MONDAY, ZoneOffset.UTC))
    }
}
