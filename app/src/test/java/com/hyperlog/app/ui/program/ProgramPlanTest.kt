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
class ProgramPlanTest {
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

    private fun List<PlanDay>.ahead(day: Int) = this[day] as PlanDay.Ahead

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
        val week7 = ProgramPlan.week(active, 7, emptyList())
        assertEquals("smith_machine_bench_press", week7.ahead(0).day.slot("bench")!!.exercise)
        assertEquals("barbell_bench_press", ProgramPlan.week(active, 8, emptyList()).ahead(0).day.slot("bench")!!.exercise)
        assertTrue(ProgramPlan.week(active, 12, emptyList()).ahead(0).day.deload)
        // A day under a date-ranged Change says when it ends, in case the day falls later.
        assertEquals(LocalDate.of(2026, 10, 26), week7.ahead(0).endsOn)
        assertEquals(null, ProgramPlan.week(active, 8, emptyList()).ahead(0).endsOn)

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
        val week = ProgramPlan.week(active(), 6, sessions)
        val done = week[0] as PlanDay.Done
        assertEquals(sessions.single().id, done.session.id)
        assertEquals("Day B — Hinge, close-grip bench, row", (week[1] as PlanDay.Skipped).day.day.name)
        assertTrue(week.ahead(2).next)
    }

    @Test
    fun aBlockIsComparedWithTheOneBeforeSlotBySlot() = runTest {
        val program = active().program
        assertEquals(null, ProgramPlan.from(program, program.blocks[0]))

        val from = ProgramPlan.from(program, program.blocks.first { it.name == "Intensify" })!!
        assertEquals("Build", from.previous.name)
        val dayA = from.days.first()
        assertEquals("Day A — Heavy bench, squat, pulldown", dayA.day.name)
        val bench = dayA.slots.first { it.after!!.id == "bench" }
        assertEquals("4–6", bench.before!!.sets.first().reps.toString())
        assertEquals("3–5", bench.after!!.sets.first().reps.toString())
        // Unchanged slots are left out: the pulldown is the same in both blocks.
        assertTrue(dayA.slots.none { it.after?.id == "lat_pulldown" })
    }
}
