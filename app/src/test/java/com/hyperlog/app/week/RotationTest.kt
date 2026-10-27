package com.hyperlog.app.week

import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Day
import com.hyperlog.app.program.Group
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetSpec
import com.hyperlog.app.program.Slot
import org.junit.Assert.assertEquals
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate
import org.junit.Test

/** The program's days taking turns on the training days (SPEC, Domain → Training days and the rotation). */
class RotationTest {
    private val mon26 = LocalDate.of(2026, 10, 26)
    private val monThuSat = setOf(MONDAY, THURSDAY, SATURDAY)

    /** A program of [weeks] weeks with the days [letters], all in one block unless [blocks] splits them. */
    private fun program(letters: String, weeks: Int = 12, blocks: List<IntRange> = listOf(1..weeks)) = Program(
        title = "Test",
        author = null,
        blocks = blocks.mapIndexed { i, range ->
            Block("b$i", "Block $i", range.first, range.last, letters.map { l ->
                Day(l.lowercase(), "Day $l", listOf(Group(false, listOf(Slot("s", "leg_extension", emptyList(), null, listOf(SetSpec(count = 3, reps = RepRange(8, 10), rir = 2)))))))
            })
        },
    )

    /** The planned turns as `"week.letter weekday-date"`, for reading at a glance. */
    private fun Rotation.plan(program: Program, take: Int = 9) = planned.take(take).map { it.label(program) }

    private fun Turn.label(program: Program) = "${position.week}.${program.blockAt(position.week)!!.days[position.day].id.uppercase()} ${date.dayOfWeek.name.take(3)} ${date.dayOfMonth}"

    @Test
    fun threeDaysOnThreeTrainingDaysAlwaysLandOnTheSameWeekdays() {
        val program = program("ABC")
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, sessions = emptyList(), today = mon26)
        assertEquals(
            listOf("1.A MON 26", "1.B THU 29", "1.C SAT 31", "2.A MON 2", "2.B THU 5", "2.C SAT 7", "3.A MON 9", "3.B THU 12", "3.C SAT 14"),
            rotation.plan(program),
        )
    }

    @Test
    fun fourDaysOnThreeTrainingDaysTakeTurnsAndTheWeekFollowsTheCalendar() {
        val program = program("ABCD")
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, sessions = emptyList(), today = mon26)
        assertEquals(
            listOf("1.A MON 26", "1.B THU 29", "1.C SAT 31", "2.D MON 2", "2.A THU 5", "2.B SAT 7", "3.C MON 9", "3.D THU 12", "3.A SAT 14"),
            rotation.plan(program),
        )
    }

    @Test
    fun aMissedTrainingDayIsSkippedAndAnySessionUsesItsDayUp() {
        val program = program("ABCD")
        // Monday missed; Thursday's Day B trained; Saturday an ad-hoc session; Monday a workout, today.
        val sessions = listOf(
            SessionStart(LocalDate.of(2026, 10, 29), Position(1, 1)),
            SessionStart(LocalDate.of(2026, 10, 31)),
            SessionStart(LocalDate.of(2026, 11, 2)),
        )
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, sessions, today = LocalDate.of(2026, 11, 2))
        assertEquals(listOf("1.A MON 26"), rotation.missed.map { it.label(program) })
        // Monday's D is used by the workout: Thursday takes A.
        assertEquals(listOf("2.A THU 5", "2.B SAT 7", "3.C MON 9"), rotation.plan(program, take = 3))
    }

    @Test
    fun todayIsNotMissedYet() {
        val program = program("ABC")
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, emptyList(), today = LocalDate.of(2026, 10, 29))
        assertEquals(listOf("1.A MON 26"), rotation.missed.map { it.label(program) })
        assertEquals(listOf("1.B THU 29", "1.C SAT 31"), rotation.plan(program, take = 2))
    }

    @Test
    fun aMissedDayCanBeTrainedOnADayWithNoTrainingDayUntilALaterTrainingDayComes() {
        val program = program("ABC")
        val anchor = RotationAnchor(mon26, week = 1, day = 0)
        val tue27 = LocalDate.of(2026, 10, 27)
        assertEquals("1.A MON 26", Rotations.of(program, anchor, monThuSat, emptyList(), today = tue27).trainToday?.label(program))
        // On a training day there's no offer; after Thursday came, it is Thursday's day, Monday's is gone.
        assertEquals(null, Rotations.of(program, anchor, monThuSat, emptyList(), today = LocalDate.of(2026, 10, 29)).trainToday)
        assertEquals("1.B THU 29", Rotations.of(program, anchor, monThuSat, emptyList(), today = LocalDate.of(2026, 10, 30)).trainToday?.label(program))
        val thursdayTrained = listOf(SessionStart(LocalDate.of(2026, 10, 29)))
        assertEquals(null, Rotations.of(program, anchor, monThuSat, thursdayTrained, today = LocalDate.of(2026, 10, 30)).trainToday)
        // Last week's miss isn't offered.
        assertEquals(null, Rotations.of(program, anchor, setOf(SATURDAY), emptyList(), today = LocalDate.of(2026, 11, 2)).trainToday)
    }

    @Test
    fun trainingAMissedDayOnAnotherDayTakesTheMissAway() {
        val program = program("ABC")
        val tue27 = LocalDate.of(2026, 10, 27)
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, listOf(SessionStart(tue27, Position(1, 0))), today = tue27)
        assertEquals(emptyList<Turn>(), rotation.missed)
        assertEquals(null, rotation.trainToday)
        assertEquals(listOf("1.B THU 29"), rotation.plan(program, take = 1))
    }

    @Test
    fun anEmptyWeekTrainsItsProgramWeekAgainAndTheRotationPicksUpWhereItWas() {
        val program = program("ABCD")
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, emptyList(), today = LocalDate.of(2026, 11, 2))
        assertEquals(listOf("1.A MON 2", "1.B THU 5", "1.C SAT 7", "2.D MON 9"), rotation.plan(program, take = 4))
        assertEquals(true, rotation.again)
        assertEquals(false, rotation.easy)
    }

    @Test
    fun twoEmptyWeeksBringOneEasyWeekThenTheWeekYouStoppedAt() {
        val program = program("ABC")
        // Week 3 trained in full, then two weeks with nothing.
        val anchor = RotationAnchor(LocalDate.of(2026, 10, 12), week = 3, day = 0)
        val trained = listOf(12, 15, 17).map { SessionStart(LocalDate.of(2026, 10, it)) }
        val rotation = Rotations.of(program, anchor, monThuSat, trained, today = LocalDate.of(2026, 11, 2))
        assertEquals(true, rotation.easy)
        assertEquals(listOf("4.A MON 2", "4.B THU 5", "4.C SAT 7", "4.A MON 9", "4.B THU 12"), rotation.plan(program, take = 5))
        assertEquals(listOf(true, true, true, false), rotation.planned.take(4).map { it.easy })
        assertEquals(2, rotation.emptyWeeks)
    }

    @Test
    fun aNewBlockStartsAtItsFirstDayAndALeftoverDayIsDropped() {
        val program = program("ABCD", weeks = 2, blocks = listOf(1..1, 2..2))
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, emptyList(), today = mon26)
        assertEquals(listOf("1.A MON 26", "1.B THU 29", "1.C SAT 31", "2.A MON 2", "2.B THU 5", "2.C SAT 7"), rotation.plan(program))
    }

    @Test
    fun aDaySkippedAheadIsMissedAndTheRotationMovesOn() {
        val program = program("ABC")
        val sat31 = LocalDate.of(2026, 10, 31)
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, listOf(SessionStart(mon26)), today = LocalDate.of(2026, 10, 27), skips = setOf(sat31))
        assertEquals(listOf("1.C SAT 31"), rotation.missed.map { it.label(program) })
        assertEquals(listOf("1.B THU 29", "2.A MON 2"), rotation.plan(program, take = 2))
    }

    @Test
    fun aRepeatedWeekTrainsItsProgramWeekAgainNextCalendarWeek() {
        val program = program("ABCD")
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, listOf(SessionStart(mon26)), today = LocalDate.of(2026, 10, 27), repeats = setOf(mon26))
        assertEquals(listOf("1.B THU 29", "1.C SAT 31", "1.D MON 2", "1.A THU 5", "1.B SAT 7", "2.C MON 9"), rotation.plan(program, take = 6))
    }

    @Test
    fun theRotationSetAgainFromTodayKeepsItsPlace() {
        val program = program("ABCD")
        val wed28 = LocalDate.of(2026, 10, 28)
        val rotation = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, emptyList(), today = wed28)
        assertEquals(RotationAnchor(wed28, week = 1, day = 1, rollbackDay = 0, emptyWeeks = 0), rotation.anchor)
        // The week still ends empty with the new anchor: next Monday is the day the missed Monday was.
        assertEquals(listOf("1.A MON 2"), Rotations.of(program, rotation.anchor, monThuSat, emptyList(), today = LocalDate.of(2026, 11, 2)).plan(program, take = 1))
        // A session today: the anchor starts tomorrow, past today's day.
        val trained = Rotations.of(program, RotationAnchor(mon26, week = 1, day = 0), monThuSat, listOf(SessionStart(LocalDate.of(2026, 10, 29))), today = LocalDate.of(2026, 10, 29))
        assertEquals(RotationAnchor(LocalDate.of(2026, 10, 30), week = 1, day = 2, rollbackDay = 0, emptyWeeks = 0), trained.anchor)
        assertEquals(Position(1, 2), trained.next)
    }
}
