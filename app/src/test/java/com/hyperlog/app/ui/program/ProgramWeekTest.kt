package com.hyperlog.app.ui.program

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.coach.ChangeJson
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.session.PastSessionEditor
import com.hyperlog.app.session.SessionLogger
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.PadKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/** What Program shows of a week: each day as the session would prescribe it, or as it was trained. */
@RunWith(RobolectricTestRunner::class)
class ProgramWeekTest {
    private lateinit var db: HyperlogDatabase
    private lateinit var programs: Programs
    private val zone = ZoneId.systemDefault()
    private var now = LocalDate.of(2026, 10, 13).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun open() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        programs = Programs(db, clock = { now })
        val imported = programs.import(StarterProgram.source(ApplicationProvider.getApplicationContext()))
        check(imported is ProgramParser.Result.Valid) { imported.toString() }
        db.programs().setPosition(active().id, 6, 0)
    }

    @After
    fun close() = db.close()

    private suspend fun active() = programs.active.first()!!
    private fun days(n: Long) { now += n * 86_400_000L }

    private fun List<ProgramDay>.ahead(day: Int) = this[day] as ProgramDay.Ahead

    @Test
    fun aDayAheadIsWhatTheSessionPrescribesWithChangesAndDeloadsApplied() = runTest {
        // Two weeks of no barbell bench from today: by the diff's rule that covers the next six days.
        programs.accept(
            ChangeJson.decode(
                """{"summary":"Hands","scope":{"from":"2026-10-13","to":"2026-10-26"},
                   "operations":[{"op":"swap","slot":"bench","exercise":"smith_machine_bench_press"}]}""",
            ),
        )
        val active = active()
        val week7 = ProgramWeek.days(active, 7, emptyList())
        assertEquals("smith_machine_bench_press", week7.ahead(0).day.slot("bench")!!.exercise)
        assertEquals("barbell_bench_press", ProgramWeek.days(active, 8, emptyList()).ahead(0).day.slot("bench")!!.exercise)
        assertTrue(ProgramWeek.days(active, 12, emptyList()).ahead(0).day.deload)
        // A day under a date-ranged Change says when it ends, in case the day falls later.
        assertEquals(LocalDate.of(2026, 10, 26), week7.ahead(0).endsOn)
        assertEquals(null, ProgramWeek.days(active, 8, emptyList()).ahead(0).endsOn)

        // Week 7 · Day A, trained a week from now, is the day Program showed.
        days(7)
        db.programs().setPosition(active.id, 7, 0)
        val logger = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        logger.startDay(active(), null)
        val shown = week7.ahead(0).day
        assertEquals(shown.slots.map { it.exercise }, logger.state.value!!.exercises.map { it.exercise.id })
        assertEquals(shown, programs.plannedDay(active.id, Position(7, 0), LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun aDateRangeStartingLaterLandsAsTheInForceDiffReadsIt() = runTest {
        // 15–21 Oct, two days from now: a week of dates is one week of program days, three here.
        programs.accept(
            ChangeJson.decode(
                """{"summary":"Back","scope":{"from":"2026-10-15","to":"2026-10-21"},
                   "operations":[{"op":"swap","slot":"rdl","exercise":"45_hyperextension"}]}""",
            ),
        )
        val active = active()
        // The next day is read for today, as Today shows it; the range hasn't started.
        assertEquals(null, ProgramWeek.days(active, 6, emptyList()).ahead(0).endsOn)
        // Day B, the day after, is the window's first day ahead...
        assertEquals("45_hyperextension", ProgramWeek.days(active, 6, emptyList()).ahead(1).day.slot("rdl")!!.exercise)
        assertEquals(LocalDate.of(2026, 10, 21), ProgramWeek.days(active, 6, emptyList()).ahead(1).endsOn)
        // ...and week 7's Day B, four days on, is past its three days.
        assertEquals("barbell_rdl", ProgramWeek.days(active, 7, emptyList()).ahead(1).day.slot("rdl")!!.exercise)
        // As the Change's card on Coach shows it.
        val record = active.inForce.single().accepted
        val diff = com.hyperlog.app.coach.Changes.inForceDiff(active.program, active.position, active.today, active.changes, record)
        assertEquals(listOf(Position(6, 1)), diff.days.map { it.day.position })
    }

    @Test
    fun aDoneDayShowsItsSessionAndASkippedDayItsPlan() = runTest {
        val logger = SessionLogger(db, clock = { now }, programs = programs).also { it.load() }
        logger.startDay(active(), null)
        logger.focus(Field.Weight)
        listOf(PadKey.Digit(8), PadKey.Digit(0)).forEach { logger.press(it) }
        logger.done(); logger.press(PadKey.Digit(5)); logger.selectRir(1); now += 2_000; logger.done()
        now += 4_000
        logger.finish()
        days(2)
        programs.accept(ChangeJson.decode("""{"summary":"Skip B","scope":"rest_of_program","operations":[{"op":"skip_day"}]}"""))

        val sessions = PastSessionEditor(db, programs).sessions()
        val week = ProgramWeek.days(active(), 6, sessions)
        val done = week[0] as ProgramDay.Done
        assertEquals(sessions.single().id, done.session.id)
        assertEquals("Day B — Hinge, close-grip bench, row", (week[1] as ProgramDay.Skipped).day.day.name)
        assertTrue(week.ahead(2).next)
    }

    @Test
    fun aBlockIsComparedWithTheOneBeforeSlotBySlot() = runTest {
        val program = active().program
        assertEquals(null, ProgramWeek.comparison(program, program.blocks[0]))

        val from = ProgramWeek.comparison(program, program.blocks.first { it.name == "Intensify" })!!
        assertEquals("Build", from.previous.name)
        val dayA = from.days.first()
        assertEquals("Day A — Heavy bench, squat, pulldown", dayA.day.name)
        val bench = dayA.slots.first { it.after!!.id == "bench" }
        assertEquals("4–6", bench.before!!.sets.first().reps.toString())
        assertEquals("3–5", bench.after!!.sets.first().reps.toString())
        // Unchanged slots are left out: the pulldown is the same in both blocks.
        assertTrue(dayA.slots.none { it.after?.id == "lat_pulldown" })
    }

    @Test
    fun aBlockWideRuleIsNamedOnceAndSlotsAreComparedAsWritten() = runTest {
        val program = active().program
        // Ramp-in is Build written the same, one RIR easier and without techniques.
        val build = ProgramWeek.comparison(program, program.blocks.first { it.name == "Build" })!!
        assertEquals(BlockRule(rirAdd = 1, noTechniques = true), build.rule.first)
        assertEquals(BlockRule(), build.rule.second)
        assertEquals(emptyList<DayComparison>(), build.days)

        val test = ProgramWeek.comparison(program, program.blocks.last())!!
        assertTrue(test.rule.second.deload)
    }

    @Test
    fun withTrainingDaysAWeekShowsItsDaysOnTheirDatesAndAMissedOneSaysSo() = runTest {
        // Tuesday 13 Oct; the rotation runs from Monday, which was missed.
        com.hyperlog.app.week.WeekdayPlans(db, clock = { now }).set(
            com.hyperlog.app.week.WeekdayPlan(listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.SATURDAY).associateWith { null }),
        )
        db.programs().setRotation(active().id, LocalDate.of(2026, 10, 12).toEpochDay(), 6, 0, 0, 0)
        val days = ProgramWeek.days(active(), 6, emptyList()).map { day ->
            when (day) {
                is ProgramDay.Missed -> "missed ${day.date} ${day.position.day}"
                is ProgramDay.Ahead -> "ahead ${day.date} ${day.position.day}${if (day.next) " next" else ""}"
                else -> day.toString()
            }
        }
        assertEquals(listOf("missed 2026-10-12 0", "ahead 2026-10-15 1 next", "ahead 2026-10-17 2"), days)
    }
}
