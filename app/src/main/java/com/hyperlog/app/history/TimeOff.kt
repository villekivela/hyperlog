package com.hyperlog.app.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Time off, as Today's time-off line counts it (SPEC, Coaching → Time off on Today). The count
 * only says something happened; what to do about it is Claude's (ADR 0003).
 */
object TimeOff {
    /** More days off than this shows the time-off line. */
    const val DAYS = 7

    /** Whole days since [lastTrained]; null with no session yet. */
    fun daysSince(lastTrained: LocalDate?, today: LocalDate): Int? =
        lastTrained?.let { ChronoUnit.DAYS.between(it, today).toInt().coerceAtLeast(0) }

    /**
     * The days off. With a weekday plan ([weekdays]), from the first planned day after
     * [lastTrained] that went untrained (today isn't missed yet), so the plan's own rest days don't
     * count; 0 when none was missed. Without one, the days since the last session. Null with no
     * session yet.
     */
    fun daysOff(lastTrained: LocalDate?, today: LocalDate, weekdays: Set<DayOfWeek> = emptySet()): Int? {
        val since = daysSince(lastTrained, today) ?: return null
        if (weekdays.isEmpty()) return since
        val missed = generateSequence(lastTrained!!.plusDays(1)) { it.plusDays(1) }.takeWhile { it < today }
            .firstOrNull { it.dayOfWeek in weekdays } ?: return 0
        return ChronoUnit.DAYS.between(missed, today).toInt()
    }

    /** Whether [days] off is more than a week. */
    fun isTimeOff(days: Int?): Boolean = days != null && days > DAYS
}
