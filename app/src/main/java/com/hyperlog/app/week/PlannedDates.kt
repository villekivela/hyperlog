package com.hyperlog.app.week

import com.hyperlog.app.coach.Changes
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Position
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The program's coming days put on dates, for Today's calendar (SPEC, Today → Planned dates). The
 * program runs as a sequence, so the dates are an estimate: the next day goes on today, or on the
 * next training day when a session was already finished today, and each day after it on the next
 * training day after that. Each day is read for its date, with the Changes covering it.
 */
object PlannedDates {
    /**
     * [program]'s days left, by date, from its today. [days] are the weekdays you train on; with
     * none, only the next day is marked (tomorrow when trained today). Empty with no program or a
     * finished one.
     */
    fun of(program: ActiveProgram?, days: Set<DayOfWeek>, trainedToday: Boolean): Map<LocalDate, PlannedDay> {
        if (program == null || program.finished) return emptyMap()
        val today = program.today
        if (days.isEmpty()) {
            val date = if (trainedToday) today.plusDays(1) else today
            return Changes.day(program.program, program.position, date, program.changes)?.let { mapOf(date to it) }.orEmpty()
        }
        fun after(date: LocalDate): LocalDate = generateSequence(date.plusDays(1)) { it.plusDays(1) }.first { it.dayOfWeek in days }
        val out = linkedMapOf<LocalDate, PlannedDay>()
        var position: Position? = program.position
        var date = if (trainedToday) after(today) else today
        while (position != null) {
            Changes.day(program.program, position, date, program.changes)?.let { out[date] = it }
            position = program.program.next(position)
            date = after(date)
        }
        return out
    }
}
