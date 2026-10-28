package com.hyperlog.app.today

import com.hyperlog.app.coach.daysLeft
import com.hyperlog.app.history.TimeOff
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Turn
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.session.date
import com.hyperlog.app.week.Rotations
import com.hyperlog.app.week.WeekdayPlan
import java.time.LocalDate

/** The kinds of day Today shows for the date picked, each with its own list. */
enum class DayKind { Running, Done, Planned, Missed, Rest }

/**
 * What Today shows for [selected] (SPEC, Today): the day's kind and the day shown, whether Start is
 * offered, the time-off line, the again notice and the calendar's marks. [sessions] are every
 * session finished; [workout] is a workout from Claude waiting today; while [sessionRunning], its
 * day is [runningDay] (null for one ad hoc or a workout).
 */
class TodayState(
    private val program: ActiveProgram?,
    sessions: List<PastSessionItem>,
    weekdayPlan: WeekdayPlan,
    lastTrained: LocalDate?,
    workout: Boolean,
    sessionRunning: Boolean,
    runningDay: PlannedDay?,
    val selected: LocalDate,
    val today: LocalDate = program?.today ?: LocalDate.now(),
) {
    /** The finished sessions by date, each day's in the order they started. */
    val done: Map<LocalDate, List<PastSessionItem>> = sessions.groupBy { it.date() }.mapValues { (_, s) -> s.sortedBy { it.startedAt } }

    /** The turns to come by date: with training days the rotation dates every one, without them only the next. */
    val planned: Map<LocalDate, PlannedDay> = program?.turns.orEmpty().filter { it.status == Turn.Status.Ahead }.associate { it.date to it.day }

    /** The turns missed by date. */
    val missed: Map<LocalDate, PlannedDay> = program?.turns.orEmpty().filter { it.status == Turn.Status.Missed }.associate { it.date to it.day }

    /** The day Start (or Resume) opens: the running session's, even after finishing moved the program on, else the next. */
    val nextDay: PlannedDay? = runningDay?.takeIf { sessionRunning } ?: program?.next?.day

    val isToday: Boolean get() = selected == today

    /** A running session keeps today as it was, whatever else was done or planned for it. */
    private val runningToday = selected == today && sessionRunning

    /** The sessions finished on [selected]. */
    val doneOn: List<PastSessionItem> = done[selected].orEmpty()

    // Today with no training day shows the next day, dated. (A sequence's next day is on today
    // unless a session was done today.)
    private val plannedOn = planned[selected]
        ?: planned.values.firstOrNull()?.takeIf { selected == today && !runningToday && doneOn.isEmpty() }

    private val missedOn = missed[selected]?.takeIf { doneOn.isEmpty() && plannedOn == null }

    val kind: DayKind = when {
        runningToday && nextDay != null -> DayKind.Running
        !runningToday && doneOn.isNotEmpty() -> DayKind.Done
        plannedOn != null && !runningToday -> DayKind.Planned
        missedOn != null -> DayKind.Missed
        else -> DayKind.Rest
    }

    /** The program day shown: the running session's, the one planned or the one missed; null otherwise. */
    val day: PlannedDay? = when (kind) {
        DayKind.Running -> nextDay
        DayKind.Planned -> plannedOn
        DayKind.Missed -> missedOn
        else -> null
    }

    /** The date [day] is planned or was missed on: [selected], or the next day's own date shown on today. */
    val date: LocalDate? = when (kind) {
        DayKind.Planned -> if (planned[selected] != null) selected else planned.keys.firstOrNull()
        DayKind.Missed -> selected
        else -> null
    }

    /**
     * [day] is the next to train, on whatever date it falls, shown first; a later one is only a plan,
     * and so is the next one while a session runs.
     */
    val next: Boolean = kind == DayKind.Planned && date == planned.keys.first() && !sessionRunning

    /** Start is offered on [day]: only on the next day, planned on today. */
    val start: Boolean = next && date == today

    /** This week's missed day, on offer under the next day on today when today has no training day. */
    val trainMissed: Turn? = program?.trainToday?.takeIf { next && selected == today }

    /** With training days the last date planned is the program's last day. */
    val last: Boolean = kind == DayKind.Planned && weekdayPlan.days.isNotEmpty() && selected == planned.keys.last()

    /** The whole days since the last session; the time-off words count these, whatever the weekday plan. */
    private val daysSince = TimeOff.daysSince(lastTrained, today)

    /**
     * The days since the last session, for the time-off line: after more than a week off (by the
     * weekday plan if there is one), with a next day to train, no session running, no workout
     * waiting and no Change made today; null otherwise.
     */
    val easeBack: Int? = daysSince?.takeIf {
        program?.next != null && !sessionRunning && !workout &&
            TimeOff.isTimeOff(TimeOff.daysOff(lastTrained, today, weekdayPlan.days)) && program.changes.none { it.acceptedOn == program.today }
    }

    /** The days since the last session, counted on the next day when it's in an easy week; null otherwise. */
    val backAfter: Int? = daysSince?.takeIf { next && day?.easy == true }

    /**
     * The program week trained again after a week with no session, named over today's day until
     * this week's first session; null otherwise.
     */
    val again: Int? = planned.values.firstOrNull()?.position?.week?.takeIf {
        program?.again == true && !sessionRunning && sessions.none { s -> Rotations.mondayOf(s.date()) == Rotations.mondayOf(today) }
    }

    /** The first day planned after [date], with its date; null past the last. */
    fun nextAfter(date: LocalDate): Pair<LocalDate, PlannedDay>? = planned.entries.firstOrNull { it.key > date }?.toPair()

    /**
     * What a rest day says: the next day after it; past the last planned day, that day. With no
     * training days only the next day has a date, so a later one is the program going on undated.
     * Null on a day with something to show.
     */
    val rest: RestMode? = if (kind != DayKind.Rest) null else nextAfter(selected)?.let { RestMode.Rest(it) }
        ?: planned.entries.lastOrNull()?.takeIf { it.key < selected }?.toPair()?.let { last ->
            // How many days the program has left if every weekday were a training day: more than
            // are planned means it goes on past the dates shown.
            val daysLeft = program?.let { it.program.daysLeft(it.position) } ?: 0
            if (weekdayPlan.days.isEmpty() && daysLeft > planned.size) RestMode.Undated(last) else RestMode.AfterProgram(last)
        }
        ?: RestMode.Rest(null)

    /**
     * The program week the calendar week from [monday] stands for: the week of its first planned
     * day, else of its first program session done; null when it has neither (outside the program,
     * or no program).
     */
    fun programWeek(monday: LocalDate): Int? {
        val days = (0..6L).map { monday.plusDays(it) }
        return days.firstNotNullOfOrNull { planned[it]?.position?.week }
            ?: days.firstNotNullOfOrNull { d -> done[d]?.firstNotNullOfOrNull { it.position?.week } }
    }

    /** The calendar week from [monday] is an easy week: one of its days is, or it's this week and this week is. */
    fun easyWeek(monday: LocalDate): Boolean =
        (0..6L).any { planned[monday.plusDays(it)]?.easy == true } || (monday == Rotations.mondayOf(today) && program?.easyWeek == true)
}

/** What a day with nothing planned or done says. */
sealed interface RestMode {
    /** `Rest day`, and the next session to select ([next] null past it). */
    data class Rest(val next: Pair<LocalDate, PlannedDay>?) : RestMode

    /** Past the program's last planned day: `Nothing planned`, pointing back to [last]. */
    data class AfterProgram(val last: Pair<LocalDate, PlannedDay>) : RestMode

    /** The program goes on but only its [next] day has a date (no training days): `Not planned yet`. */
    data class Undated(val next: Pair<LocalDate, PlannedDay>) : RestMode
}
