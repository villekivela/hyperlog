package com.hyperlog.app.today

import com.hyperlog.app.coach.AcceptedChange
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TestPrograms
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.week.RotationAnchor
import com.hyperlog.app.week.Rotations
import com.hyperlog.app.week.WeekdayPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.LocalDate
import java.time.ZoneId

/** What Today decides for the date picked (SPEC, Today; Training days and the rotation). */
class TodayStateTest {
    private val program = (ProgramParser.parse(TestPrograms.twoDays("Test"), setOf("barbell_bench_press", "smith_machine_bench_press", "cable_lateral_raise", "leg_extension")) as ProgramParser.Result.Valid).program
    private val days = setOf(MONDAY, THURSDAY, SATURDAY)
    private val plan = WeekdayPlan(days.associateWith { null })

    private val mon = LocalDate.of(2026, 10, 26)
    private val tue = LocalDate.of(2026, 10, 27)
    private val wed = LocalDate.of(2026, 10, 28)
    private val thu = LocalDate.of(2026, 10, 29)
    private val sat = LocalDate.of(2026, 10, 31)

    /** The program rotating on Mondays, Thursdays and Saturdays from Monday 26 Oct, seen on [today]. */
    private fun rotating(today: LocalDate, emptyWeeks: Int = 0): ActiveProgram {
        val rotation = Rotations.of(program, RotationAnchor(mon, week = 1, day = 0, emptyWeeks = emptyWeeks), days, emptyList(), today)
        return ActiveProgram(1, program, rotation.next, today = today, rotation = rotation)
    }

    /** The program as a sequence, its next day at [position], seen on [today]. */
    private fun sequence(today: LocalDate, position: Position = Position(1, 0), lastTrained: LocalDate? = null) =
        ActiveProgram(1, program, position, today = today, lastTrained = lastTrained)

    /** A session finished on [date]: ad hoc, or of the program's day at [position]. */
    private fun session(date: LocalDate, position: Position? = null, id: Long = 1): PastSessionItem {
        val at = date.atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return PastSessionItem(id, at, at + 3_600_000, null, null, emptyList(), programId = position?.let { 1 }, position = position)
    }

    private fun state(
        program: ActiveProgram?,
        selected: LocalDate = program?.today ?: thu,
        sessions: List<PastSessionItem> = emptyList(),
        plan: WeekdayPlan = this.plan,
        lastTrained: LocalDate? = null,
        workout: Boolean = false,
        running: Boolean = false,
        runningDay: PlannedDay? = null,
    ) = TodayState(
        program, sessions, plan, lastTrained,
        workout = workout, sessionRunning = running, runningDay = runningDay, selected = selected, today = program?.today ?: thu,
    )

    // The day's kind and the day shown

    @Test
    fun withNoTrainingDaysOnlyTheNextDayHasADate() {
        assertEquals(setOf(thu), state(sequence(thu), plan = WeekdayPlan.None).planned.keys)
    }

    @Test
    fun todayWithNoTrainingDayShowsTheNextDayOnItsDate() {
        val today = state(rotating(tue))
        assertEquals(DayKind.Planned, today.kind)
        assertEquals(thu, today.date)
        assertEquals("Day B", today.day?.day?.name)
    }

    @Test
    fun aDayPlannedLaterShowsItsOwnDay() {
        val saturday = state(rotating(tue), selected = sat)
        assertEquals(DayKind.Planned, saturday.kind)
        assertEquals(sat, saturday.date)
        assertEquals("Day A", saturday.day?.day?.name)
    }

    @Test
    fun aMissedDayPickedShowsItsPlan() {
        val monday = state(rotating(tue), selected = mon)
        assertEquals(DayKind.Missed, monday.kind)
        assertEquals(mon, monday.date)
        assertEquals("Day A", monday.day?.day?.name)
    }

    @Test
    fun aSessionDoneOnADayShowsItOverWhatWasPlannedOrMissed() {
        val sessions = listOf(session(mon, id = 1), session(thu, id = 2))
        assertEquals(DayKind.Done, state(rotating(tue), selected = mon, sessions = sessions).kind)
        val thursday = state(rotating(thu), sessions = sessions)
        assertEquals(DayKind.Done, thursday.kind)
        assertEquals(listOf(2L), thursday.doneOn.map { it.id })
    }

    @Test
    fun aDayWithNothingIsARestDay() {
        val wednesday = state(rotating(tue), selected = wed)
        assertEquals(DayKind.RestDay, wednesday.kind)
        assertNull(wednesday.day)
    }

    @Test
    fun aSessionRunningKeepsItsDayOnTodayWhateverWasDoneOrPlanned() {
        val runningDay = program.plannedDay(Position(2, 1))!!
        val today = state(rotating(thu), sessions = listOf(session(thu)), running = true, runningDay = runningDay)
        assertEquals(DayKind.Running, today.kind)
        assertEquals(runningDay, today.day)
    }

    @Test
    fun aSessionRunningAdHocWithoutAProgramLeavesTodayEmpty() {
        assertEquals(DayKind.RestDay, state(null, running = true).kind)
    }

    @Test
    fun aSequenceTrainedTodayShowsTheSessionDoneAndPutsTheNextDayOnTomorrow() {
        val program = sequence(thu, Position(1, 1), lastTrained = thu)
        val today = state(program, sessions = listOf(session(thu, Position(1, 0))))
        assertEquals(DayKind.Done, today.kind)
        val tomorrow = state(program, selected = thu.plusDays(1))
        assertEquals(DayKind.Planned, tomorrow.kind)
        assertEquals("Day B", tomorrow.day?.day?.name)
    }

    // Start, the missed day on offer and the last day

    @Test
    fun startIsOnlyForTheNextDayPlannedOnToday() {
        val thursday = state(rotating(thu))
        assertTrue(thursday.isNext)
        assertTrue(thursday.offersStart)
        // On a day with no training day the next day is shown first, without Start.
        val tuesday = state(rotating(tue))
        assertTrue(tuesday.isNext)
        assertFalse(tuesday.offersStart)
        // A later day is only a plan.
        val saturday = state(rotating(thu), selected = sat)
        assertFalse(saturday.isNext)
        assertFalse(saturday.offersStart)
    }

    @Test
    fun whileASessionRunsTheNextDayOnALaterDateIsOnlyAPlan() {
        val thursday = state(rotating(tue), selected = thu, running = true)
        assertEquals(DayKind.Planned, thursday.kind)
        assertFalse(thursday.isNext)
        assertFalse(thursday.offersStart)
    }

    @Test
    fun onADayWithNoTrainingDayThisWeeksMissedDayIsOnOfferToday() {
        val missed = state(rotating(tue)).trainMissed!!
        assertEquals(mon, missed.date)
        assertEquals("Day A", missed.day.day.name)
        // Only on today, and not on a training day.
        assertNull(state(rotating(tue), selected = thu).trainMissed)
        assertNull(state(rotating(thu)).trainMissed)
    }

    @Test
    fun withTrainingDaysTheLastDatePlannedIsTheProgramsLastDay() {
        // The program's turns on Mondays, Thursdays and Saturdays run to Saturday 7 Nov.
        val lastDay = LocalDate.of(2026, 11, 7)
        assertTrue(state(rotating(thu), selected = lastDay).isLastDay)
        assertFalse(state(rotating(thu), selected = LocalDate.of(2026, 11, 2)).isLastDay)
        assertFalse(state(rotating(thu), selected = lastDay, plan = WeekdayPlan.None).isLastDay)
    }

    // Time off

    @Test
    fun moreThanAWeekOffOffersToEaseBackInCountingTheDaysSinceTheLastSession() {
        assertEquals(8, state(sequence(thu), plan = WeekdayPlan.None, lastTrained = thu.minusDays(8)).easeBack)
        assertNull(state(sequence(thu), plan = WeekdayPlan.None, lastTrained = thu.minusDays(7)).easeBack)
        assertNull(state(sequence(thu), plan = WeekdayPlan.None).easeBack)
    }

    @Test
    fun withTrainingDaysTimeOffCountsFromTheFirstOneMissed() {
        // Last trained Monday 12 Oct: Thursday 15 Oct was the first missed, 14 days ago.
        assertEquals(17, state(rotating(thu), lastTrained = LocalDate.of(2026, 10, 12)).easeBack)
        // On Mondays alone, last trained Monday 19 Oct: only Monday 26 Oct was missed.
        val mondays = WeekdayPlan(mapOf(MONDAY to null))
        assertNull(state(sequence(thu), plan = mondays, lastTrained = LocalDate.of(2026, 10, 19)).easeBack)
    }

    @Test
    fun easingBackInIsNotOfferedWhileASessionRunsAWorkoutWaitsOrAChangeWasMadeToday() {
        val off = thu.minusDays(10)
        assertEquals(10, state(sequence(thu), plan = WeekdayPlan.None, lastTrained = off).easeBack)
        assertNull(state(sequence(thu), plan = WeekdayPlan.None, lastTrained = off, running = true).easeBack)
        assertNull(state(sequence(thu), plan = WeekdayPlan.None, lastTrained = off, workout = true).easeBack)
        val change = AcceptedChange(1, Change("Ease back in", Scope.RestOfProgram, emptyList()), thu, Position(1, 0), Position(1, 0), null)
        val changed = sequence(thu).copy(history = listOf(ChangeRecord(change, 0, null)))
        assertNull(state(changed, plan = WeekdayPlan.None, lastTrained = off).easeBack)
        // Nor with no program, or a finished one.
        assertNull(state(null, plan = WeekdayPlan.None, lastTrained = off).easeBack)
        assertNull(state(sequence(thu, Position(3, 0)), plan = WeekdayPlan.None, lastTrained = off).easeBack)
    }

    @Test
    fun inAnEasyWeekTheNextDayCountsTheDaysBack() {
        assertEquals(16, state(rotating(thu, emptyWeeks = 2), lastTrained = LocalDate.of(2026, 10, 13)).backAfter)
        assertNull(state(rotating(thu), lastTrained = LocalDate.of(2026, 10, 13)).backAfter)
    }

    // The week trained again

    @Test
    fun afterAWeekWithNoSessionTodaySaysItsWeekTrainsAgainUntilItsFirstSession() {
        assertEquals(1, state(rotating(thu, emptyWeeks = 1)).again)
        assertNull(state(rotating(thu, emptyWeeks = 1), sessions = listOf(session(mon))).again)
        assertNull(state(rotating(thu, emptyWeeks = 1), running = true).again)
        assertNull(state(rotating(thu)).again)
    }

    // What comes next, and a day with nothing

    @Test
    fun theNextDayAfterADateIsTheFirstPlannedPastIt() {
        val (date, day) = state(rotating(tue)).nextAfter(thu)!!
        assertEquals(sat, date)
        assertEquals("Day A", day.day.name)
        assertNull(state(rotating(tue)).nextAfter(LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun aRestDayPointsToTheNextDay() {
        val rest = state(rotating(tue), selected = wed).restDay as RestDay.Next
        assertEquals(thu, rest.next?.first)
    }

    @Test
    fun pastTheProgramsLastDayNothingIsPlannedAndItPointsBackToThatDay() {
        val after = state(rotating(thu), selected = LocalDate.of(2026, 11, 8)).restDay as RestDay.AfterProgram
        assertEquals(LocalDate.of(2026, 11, 7), after.last.first)
        // A sequence on its last day has nothing after it either.
        val last = state(sequence(thu, Position(2, 1)), selected = sat, plan = WeekdayPlan.None).restDay
        assertEquals(thu, (last as RestDay.AfterProgram).last.first)
    }

    @Test
    fun withNoTrainingDaysALaterDateIsTheProgramGoingOnUndated() {
        val later = state(sequence(thu), selected = sat, plan = WeekdayPlan.None).restDay as RestDay.Undated
        assertEquals(thu, later.next.first)
    }

    // The calendar's head

    @Test
    fun aCalendarWeekStandsForTheProgramWeekOfItsFirstPlannedDayElseOfItsFirstSession() {
        val today = state(rotating(thu), sessions = listOf(session(LocalDate.of(2026, 10, 21), Position(1, 0))))
        assertEquals(1, today.programWeek(mon))
        assertEquals(2, today.programWeek(LocalDate.of(2026, 11, 2)))
        assertEquals(1, today.programWeek(LocalDate.of(2026, 10, 19)))
        assertNull(today.programWeek(LocalDate.of(2026, 11, 9)))
        // With no training days only the next day's week has one.
        assertNull(state(sequence(thu), plan = WeekdayPlan.None).programWeek(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun anEasyWeekIsNamedInTheHeadOfItsCalendarWeek() {
        val easy = state(rotating(thu, emptyWeeks = 2))
        assertTrue(easy.easyWeek(mon))
        assertFalse(easy.easyWeek(LocalDate.of(2026, 11, 2)))
        assertFalse(state(rotating(thu)).easyWeek(mon))
        // Seen the Sunday before, the week ahead is easy by its days.
        assertTrue(state(rotating(LocalDate.of(2026, 10, 25), emptyWeeks = 2)).easyWeek(mon))
        // On its Sunday, with none of its days left to come, this week is easy all the same.
        assertTrue(state(rotating(LocalDate.of(2026, 11, 1), emptyWeeks = 2)).easyWeek(mon))
    }
}
