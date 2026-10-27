package com.hyperlog.app.week

import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Where the rotation was last set (ADR 0006): from [from] on, the first training day takes [day]
 * of [week]. [rollbackDay] is the day the next week starts at if [from]'s week ends with no
 * session, and [emptyWeeks] the weeks with none in a row before it. Training days before [from]
 * aren't counted, so none of them is missed.
 */
data class RotationAnchor(val from: LocalDate, val week: Int, val day: Int, val rollbackDay: Int = day, val emptyWeeks: Int = 0)

/** A session as the rotation reads it: the date it started, and the day of this program it trained (null for any other). */
data class SessionStart(val date: LocalDate, val position: Position? = null)

/** A training day and the program day the rotation gives it; [easy] in an easy week. */
data class Turn(val date: LocalDate, val position: Position, val easy: Boolean = false)

/**
 * The rotation as it stands today: the days to come on their training days ([planned], from today
 * to the program's end), the training days [missed] (a day skipped ahead of time included), and
 * this week: whether it trains its program week [again] after an empty one, is an [easy] week, and
 * the [emptyWeeks] in a row before it.
 */
data class Rotation(
    val planned: List<Turn>,
    val missed: List<Turn>,
    /** This week's last missed day, while today has no training day and no later one came. */
    val trainToday: Turn?,
    val again: Boolean,
    val easy: Boolean,
    val emptyWeeks: Int,
    /** The rotation set again from today (tomorrow once a session started today), in the same place. */
    val anchor: RotationAnchor,
    /** The next day to train; past the last week once nothing is left. */
    val next: Position,
)

/**
 * The program's days taking turns on the training days (SPEC, Domain → Training days and the
 * rotation). Each training day takes the next day, trained or missed; a program week is a calendar
 * week. A week with no session trains its program week again from where it started, and the first
 * week after two or more of them is an easy week, outside the program.
 */
object Rotations {
    /**
     * The rotation from [anchor] on [days] to [today], given every session started since the
     * anchor's week. [skips] are training days skipped ahead of time; a week in [repeats] (by its
     * Monday) trains its program week again the week after.
     */
    fun of(
        program: Program,
        anchor: RotationAnchor,
        days: Set<DayOfWeek>,
        sessions: List<SessionStart>,
        today: LocalDate,
        skips: Set<LocalDate> = emptySet(),
        repeats: Set<LocalDate> = emptySet(),
    ): Rotation {
        require(days.isNotEmpty()) { "Without training days the program runs as a sequence" }
        val planned = mutableListOf<Turn>()
        val passed = mutableListOf<Turn>()
        val ahead = mutableListOf<Turn>()
        val started = sessions.map { it.date }.toSet()
        // Today is passed once a session started on it.
        val cutoff = if (today in started) today.plusDays(1) else today
        var week = anchor.week
        var day = anchor.day
        var rollback = anchor.rollbackDay
        var empty = anchor.emptyWeeks
        var again = false
        var thisWeek: Triple<Boolean, Boolean, Int>? = null
        var reset: RotationAnchor? = null
        var monday = mondayOf(anchor.from)
        while (week <= program.weeks) {
            val block = program.blockAt(week)!!
            val easy = empty >= 2
            if (monday == mondayOf(today)) thisWeek = Triple(again, easy, empty)
            val dates = (0L..6L).map { monday.plusDays(it) }.filter { it.dayOfWeek in days && it >= anchor.from }
            if (monday == mondayOf(cutoff)) {
                reset = RotationAnchor(maxOf(cutoff, anchor.from), week, (day + dates.count { it < cutoff }) % block.days.size, rollback, empty)
            }
            dates.forEach { date ->
                val turn = Turn(date, Position(week, day % block.days.size), easy)
                when {
                    date < cutoff -> passed += turn
                    date in skips -> ahead += turn
                    else -> planned += turn
                }
                day++
            }
            // A past week counts if it had a session; this week and later ones while a training day is left.
            val sunday = monday.plusDays(6)
            val counts = started.any { it in monday..sunday } || dates.any { it >= cutoff && it !in skips }
            again = false
            when {
                dates.isEmpty() -> Unit
                counts -> {
                    // An easy week sits outside the program: the week after it is the one you stopped at.
                    if (!easy && monday !in repeats) week++
                    empty = 0
                    // A new block starts at its first day; a day left over from the last one is dropped.
                    if (program.blockAt(week)?.id != block.id) day = 0
                }
                else -> {
                    day = rollback
                    empty++
                    again = true
                }
            }
            rollback = day
            monday = monday.plusWeeks(1)
        }
        val turns = (passed + planned).sortedBy { it.date }
        // A missed day trained before the next training day came isn't missed.
        val missed = passed.filterIndexed { i, turn ->
            val until = turns.getOrNull(i + 1)?.date ?: LocalDate.MAX
            (turn.date !in started || turn.date in skips) && sessions.none { it.position == turn.position && it.date > turn.date && it.date < until }
        }
        val trainToday = passed.lastOrNull()?.takeIf { today.dayOfWeek !in days && it in missed && it.date >= mondayOf(today) }
        val (thisAgain, thisEasy, thisEmpty) = thisWeek ?: Triple(false, false, empty)
        return Rotation(
            planned = planned,
            missed = (missed + ahead).sortedBy { it.date },
            trainToday = trainToday,
            again = thisAgain,
            easy = thisEasy,
            emptyWeeks = thisEmpty,
            anchor = reset ?: RotationAnchor(maxOf(cutoff, anchor.from), week, 0, 0, empty),
            next = planned.firstOrNull()?.position ?: Position(program.weeks + 1, 0),
        )
    }

    fun mondayOf(date: LocalDate): LocalDate = date.minusDays(date.dayOfWeek.value - 1L)
}
