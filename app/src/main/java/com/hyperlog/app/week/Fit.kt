package com.hyperlog.app.week

import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.MissingSwaps
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.PlannedDay
import java.time.DayOfWeek
import java.time.LocalDate

/** A planned day that runs [about] minutes on a training day that has [has]. */
data class TimeOver(val about: Int, val has: Int, val weekday: DayOfWeek)

/**
 * What a fit chip's tap starts a check-in with: its first answer [words], and the day it was on,
 * its last [slot] and planned [date] (null for today's day, which the hero card shows without one), for the scripted coach.
 */
data class FitAsk(val words: String, val slot: String?, val date: LocalDate?)

/** The swaps [count] a planned day needs at [gym], the start sheet's list. */
data class SwapCount(val count: Int, val gym: String)

/** How a planned day fits its training day: what is off, if anything. */
data class DayFit(val time: TimeOver?, val swaps: SwapCount?) {
    val isEmpty: Boolean get() = time == null && swaps == null

    companion object {
        val None = DayFit(null, null)
    }
}

/**
 * Each planned day against the training day it lands on (SPEC, Domain → Training days and the
 * rotation → The fit). It reads the day and never changes it.
 */
object Fit {
    /** Minutes a day may run over its training day's before it shows. */
    const val SLACK = 5

    /**
     * The fit of [day] on [date]: its time when its [about] minutes are more than [SLACK] over the
     * training day's (a training day without minutes is never checked), and the swaps its training
     * day's gym needs (none for no gym). Nothing on a date that is no training day.
     */
    fun of(day: PlannedDay, date: LocalDate, plan: WeekdayPlan, gyms: List<Gym>, library: List<Exercise>, about: Int): DayFit {
        val weekday = date.dayOfWeek
        if (weekday !in plan.days) return DayFit.None
        val has = plan.minutes[weekday]
        val time = if (has != null && about > has + SLACK) TimeOver(about, has, weekday) else null
        val gym = plan.gyms[weekday]?.let { id -> gyms.firstOrNull { it.id == id } }
        val count = gym?.let { MissingSwaps.forDay(day, library, it.missing).size } ?: 0
        return DayFit(time, if (count > 0 && gym != null) SwapCount(count, gym.name) else null)
    }
}
