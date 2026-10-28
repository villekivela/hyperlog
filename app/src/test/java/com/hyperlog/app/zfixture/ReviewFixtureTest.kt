package com.hyperlog.app.zfixture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hyperlog.app.data.BodyweightEntity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.LiftGoalEntity
import com.hyperlog.app.data.LoggedSetEntity
import com.hyperlog.app.data.SessionEntity
import com.hyperlog.app.data.SessionExerciseEntity
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Programs
import com.hyperlog.app.program.StarterProgram
import com.hyperlog.app.week.WeekdayPlan
import com.hyperlog.app.week.WeekdayPlans
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Temporary: writes review fixture databases for the design passes. Not committed. */
@RunWith(RobolectricTestRunner::class)
class ReviewFixtureTest {
    private val out = System.getenv("REVIEW_FIXTURE_DIR")
    private val zone = ZoneId.systemDefault()

    private fun millis(d: LocalDate, h: Int = 18) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun build(name: String, start: LocalDate, until: LocalDate, missed: Set<LocalDate>) = runBlocking {
        val file = File(out, name).also { it.delete(); File(out, "$name-wal").delete(); File(out, "$name-shm").delete() }
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), HyperlogDatabase::class.java, file.absolutePath)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).allowMainThreadQueries().build()
        ExerciseLibrary(db).sync(LibrarySeed.fromAssets(ApplicationProvider.getApplicationContext()))
        val clock = { millis(start, 8) }
        WeekdayPlans(db, clock).set(WeekdayPlan(mapOf(DayOfWeek.MONDAY to null, DayOfWeek.WEDNESDAY to null, DayOfWeek.FRIDAY to null)))
        val programs = Programs(db, clock = clock)
        programs.import(StarterProgram.source(ApplicationProvider.getApplicationContext()))
        val active = programs.active.first()!!
        val program = active.program
        val dao = db.sessions()
        var date = start
        var dayInWeek = 0
        var n = 0
        /** Logs [day] on [date]: each set's weight worked back from the lift's estimate in [week], for its reps at RIR 1. */
        suspend fun log(date: LocalDate, day: com.hyperlog.app.program.PlannedDay, programId: Long?, week: Int) {
            val id = dao.insertSession(
                SessionEntity(
                    startedAt = millis(date), finishedAt = millis(date) + 3_600_000, programId = programId,
                    week = week.takeIf { programId != null }, dayIndex = day.position.day.takeIf { programId != null },
                ),
            )
            day.slots.forEachIndexed { i, slot ->
                val se = dao.insertSessionExercise(SessionExerciseEntity(sessionId = id, exerciseId = slot.exercise, position = i, slotId = slot.slotIdOrNull(programId)))
                val base = START[slot.exercise]?.first ?: 40.0
                // Plates and pins go in 2.5 kg; light dumbbells and cables in half kilos.
                val step = if (base >= 20) 2.5 else 0.5
                // The working weight for 6 reps at the start, gaining 0.6% a week; the stalled ones never do.
                val e1rm = base * (1 + 7 / 30.0) * (1 + if (slot.exercise in STALLED) 0.0 else 0.006 * week)
                slot.sets.forEachIndexed { s, p ->
                    val top = Math.floor(e1rm / (1 + (p.reps.min + 1) / 30.0) / step) * step
                    // A back-off is its percentage lighter, to the step below.
                    val weight = p.backoff?.let { b -> Math.floor(top * (100 - b.percent) / 100 / step) * step } ?: top
                    dao.insertSet(LoggedSetEntity(sessionExerciseId = se, position = s, weightKg = weight, fullReps = p.reps.min, partialReps = 0, rir = if (p.rir == null) null else 1, loggedAt = millis(date) + s * 120_000))
                }
            }
        }
        // Three weeks of training before the program, so its estimates are settled from the start.
        (1..3).forEach { w ->
            listOf(0L, 2L, 4L).forEachIndexed { d, offset -> log(start.minusWeeks(4L - w).plusDays(offset), program.plannedDay(Position(1, d))!!, null, 0) }
        }
        while (date <= until) {
            if (date.dayOfWeek == DayOfWeek.MONDAY) dayInWeek = 0
            if (date.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) {
                val week = (java.time.temporal.ChronoUnit.WEEKS.between(start, date) + 1).toInt()
                val position = Position(week, dayInWeek)
                dayInWeek++
                if (date !in missed && week <= program.weeks) {
                    log(date, program.plannedDay(position)!!, active.id, week)
                    n++
                }
            }
            date = date.plusDays(1)
        }
        var bw = 82.4
        generateSequence(start) { it.plusDays(4) }.takeWhile { it <= until }.forEach { db.bodyweights().put(BodyweightEntity(it.toEpochDay(), bw)); bw -= 0.2 }
        db.liftGoals().put(LiftGoalEntity("barbell_bench_press", 125.0, LocalDate.of(2027, 1, 15).toEpochDay()))
        val reader = com.hyperlog.app.program.BlockReviewReader(db, programs)
        File(out, "$name.txt").writeText(buildString {
            program.blocks.forEach { b ->
                val page = reader.page(active.id, b.id, withProgram = true) ?: return@forEach
                appendLine("${b.name}: " + page.review.lifts.joinToString { "${it.exercise} ${"%.1f".format(it.startKg)}→${"%.1f".format(it.endKg)}" })
                appendLine("  program: " + page.whole!!.lifts.joinToString { "${it.exercise} ${"%.1f".format(it.startKg)}→${"%.1f".format(it.endKg)}" })
                appendLine("  goal: " + page.review.goals + " / " + page.whole!!.goals)
            }
        })
        db.close()
    }

    private fun com.hyperlog.app.program.PlannedSlot.slotIdOrNull(programId: Long?) = id.takeIf { programId != null }

    private companion object {
        /** An intermediate lifter's working weight (each dumbbell's for dumbbells) and the step they add. */
        val START = mapOf(
            "barbell_bench_press" to (85.0 to 2.5), "close_grip_bench_press" to (70.0 to 2.5), "smith_machine_bench_press" to (80.0 to 2.5),
            "db_incline_press" to (28.0 to 2.0), "db_bench_press" to (30.0 to 2.0), "smith_machine_incline_press" to (70.0 to 2.5),
            "machine_chest_press" to (70.0 to 5.0), "incline_machine_press" to (60.0 to 5.0), "pec_deck" to (55.0 to 2.5), "cable_flye" to (15.0 to 2.5),
            "hack_squat" to (100.0 to 5.0), "smith_machine_squat" to (100.0 to 5.0), "leg_press" to (160.0 to 5.0),
            "barbell_rdl" to (100.0 to 5.0), "db_romanian_deadlift" to (30.0 to 2.0), "45_hyperextension" to (20.0 to 2.5),
            "lying_leg_curl" to (45.0 to 2.5), "seated_leg_curl" to (50.0 to 2.5), "leg_extension" to (55.0 to 2.5),
            "leg_press_calf_press" to (120.0 to 5.0), "seated_calf_raise" to (50.0 to 2.5), "standing_calf_raise" to (80.0 to 2.5),
            "cable_lateral_raise" to (7.5 to 1.25), "db_lateral_raise" to (10.0 to 1.0), "machine_lateral_raise" to (30.0 to 2.5),
            "machine_shoulder_press" to (50.0 to 5.0), "cable_shoulder_press" to (25.0 to 2.5), "seated_db_shoulder_press" to (24.0 to 2.0),
            "machine_preacher_curl" to (30.0 to 2.5), "bayesian_cable_curl" to (12.5 to 1.25), "db_incline_curl" to (12.0 to 1.0),
            "overhead_cable_triceps_extension" to (25.0 to 2.5), "overhead_db_triceps_extension" to (25.0 to 2.0),
            "triceps_pressdown" to (30.0 to 2.5), "cable_triceps_kickback" to (7.5 to 1.25),
            "machine_pulldown" to (65.0 to 2.5), "2_grip_lat_pulldown" to (65.0 to 2.5), "1_arm_cable_pulldown" to (30.0 to 2.5),
            "seated_cable_row" to (65.0 to 2.5), "chest_supported_machine_row" to (70.0 to 2.5), "chest_supported_db_row" to (30.0 to 2.0),
            "1_arm_reverse_pec_deck" to (15.0 to 2.5), "reverse_cable_crossover" to (10.0 to 1.25), "lying_reverse_db_flye" to (8.0 to 1.0),
            "neutral_grip_pullup" to (10.0 to 2.5),
        )

        /** Lifts that don't move, so the review has a stall to show. */
        val STALLED = setOf("machine_preacher_curl")
    }

    @Test
    fun write() {
        assumeTrue(out != null)
        // Ramp-in done, Build not started: Monday 26 Oct missed.
        build("ramp-in-done.db", LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 27), missed = setOf(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 26)))
        // The whole program done.
        build("program-done.db", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 10, 25), missed = setOf(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9)))
    }
}
