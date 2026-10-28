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

/**
 * A training day and the program day the rotation gives it; [easy] in an easy week. The rotation's
 * own record: the program running hands each out resolved, as a Turn.
 */
data class RotationTurn(val date: LocalDate, val position: Position, val easy: Boolean = false)

/**
 * A [turn] missed before the rotation was last set again, kept since it no longer reaches it, with
 * the training day [after] it if that came first: until then it can still be trained.
 */
data class KeptMiss(val turn: RotationTurn, val after: LocalDate? = null)

/**
 * The rotation as it stands today: the days to come on their training days ([planned], from today
 * to the program's end), the training days [missed] (a day skipped ahead of time included), and
 * this week: whether it trains its program week [again] after an empty one, is an [easy] week, and
 * the [emptyWeeks] in a row before it.
 */
data class Rotation(
    val planned: List<RotationTurn>,
    val missed: List<RotationTurn>,
    /** This week's last missed day, while today has no training day and no later one came. */
    val trainToday: RotationTurn?,
    val again: Boolean,
    /** This week trains its program week again, by a `repeat_week`. */
    val repeated: Boolean = false,
    val easy: Boolean,
    val emptyWeeks: Int,
    /** The rotation set again from today (tomorrow once a session started today), in the same place. */
    val anchor: RotationAnchor,
    /** The next day to train; past the last week once nothing is left. */
    val next: Position,
    /** The date the rotation counts from: days trained before it ran as a sequence. */
    val since: LocalDate = LocalDate.MIN,
    /** The rotation set again from next Monday, this week done with the sessions it had. */
    private val weekDone: RotationAnchor = anchor,
    /** Each training day's date, missed or not, with the next one's. */
    private val turnAfter: Map<LocalDate, LocalDate> = emptyMap(),
    /** What it was read from, to set it again somewhere else. */
    private val read: Read? = null,
) {
    /** What [Rotations.of] read a rotation from, beside its anchor and the days it kept. */
    data class Read(val program: Program, val days: Set<DayOfWeek>, val sessions: List<SessionStart>, val today: LocalDate, val skips: Set<LocalDate>, val repeats: Set<LocalDate>)

    /** The rotation set again from today at [position], as a Change moving the position sets it. */
    fun anchorAt(position: Position): RotationAnchor = anchor.copy(week = position.week, day = position.day, rollbackDay = position.day)

    /** The rotation as it would read set again at [position] ([anchorAt]); itself when it can't be read again. */
    fun movedTo(position: Position): Rotation {
        val read = read ?: return this
        return Rotations.of(read.program, anchorAt(position), read.days, read.sessions, read.today, read.skips, read.repeats)
    }

    /** The coming training days' dates, in order. */
    val trainingDays: List<LocalDate> get() = planned.map { it.date }

    /**
     * The rotation set again on [days]: from today, or, when none of them is left this week, from
     * next Monday with this week done, so it counts as trained or empty all the same.
     */
    fun anchorOn(days: Set<DayOfWeek>): RotationAnchor {
        val sunday = Rotations.mondayOf(anchor.from).plusDays(6)
        val left = generateSequence(anchor.from) { it.plusDays(1) }.takeWhile { it <= sunday }.any { it.dayOfWeek in days }
        return if (left) anchor else weekDone
    }

    /** The days missed before [from], to keep when the rotation is set again from it. */
    fun keptBefore(from: LocalDate): List<KeptMiss> =
        missed.filter { it.date < from }.map { KeptMiss(it, turnAfter[it.date]?.takeIf { after -> after < anchor.from }) }
}

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
     * Monday) trains its program week again the week after. [kept] are the days missed before the
     * anchor.
     */
    fun of(
        program: Program,
        anchor: RotationAnchor,
        days: Set<DayOfWeek>,
        sessions: List<SessionStart>,
        today: LocalDate,
        skips: Set<LocalDate> = emptySet(),
        repeats: Set<LocalDate> = emptySet(),
        kept: List<KeptMiss> = emptyList(),
    ): Rotation {
        require(days.isNotEmpty()) { "Without training days the program runs as a sequence" }
        val planned = mutableListOf<RotationTurn>()
        val elapsed = mutableListOf<RotationTurn>()
        val ahead = mutableListOf<RotationTurn>()
        val started = sessions.map { it.date }.toSet()
        // Today is passed once a session started on it.
        val cutoff = if (today in started) today.plusDays(1) else today
        // A rotation set again part-way through a week keeps how that week began.
        var place = Place(
            anchor.week, anchor.day, anchor.rollbackDay, anchor.emptyWeeks,
            again = anchor.emptyWeeks > 0,
            repeated = anchor.emptyWeeks == 0 && mondayOf(anchor.from).minusWeeks(1) in repeats,
        )
        var thisWeek: WeekState? = null
        var reset: RotationAnchor? = null
        var weekDone: RotationAnchor? = null
        var monday = mondayOf(anchor.from)
        while (place.week <= program.weeks) {
            val block = program.blockAt(place.week)!!
            val easy = place.empty >= 2
            // An easy week sits outside the program: it isn't a week trained again.
            if (monday == mondayOf(today)) thisWeek = WeekState(place.again && !easy, place.repeated && !easy, easy, place.empty)
            val dates = (0L..6L).map { monday.plusDays(it) }.filter { it.dayOfWeek in days && it >= anchor.from }
            val sunday = monday.plusDays(6)
            val trained = started.any { it in monday..sunday }
            if (monday == mondayOf(cutoff)) {
                val passed = place.copy(day = place.day + dates.count { it < cutoff })
                reset = RotationAnchor(maxOf(cutoff, anchor.from), place.week, passed.day % block.days.size, place.rollback, place.empty)
                weekDone = passed.endWeek(program, trained, monday in repeats).anchor(program, monday.plusWeeks(1))
            }
            dates.forEach { date ->
                val turn = RotationTurn(date, Position(place.week, place.day % block.days.size), easy)
                when {
                    date < cutoff -> elapsed += turn
                    date in skips -> ahead += turn
                    else -> planned += turn
                }
                place = place.copy(day = place.day + 1)
            }
            // A past week counts if it had a session; this week and later ones while a training day is left.
            val counts = trained || dates.any { it >= cutoff && it !in skips }
            place = if (dates.isEmpty()) place.copy(rollback = place.day, again = false, repeated = false) else place.endWeek(program, counts, monday in repeats)
            monday = monday.plusWeeks(1)
        }
        val keptTurns = kept.map { it.turn }.filter { it.date < anchor.from }
        val turns = (keptTurns + elapsed + planned).sortedBy { it.date }
        val turnAfter = turns.zipWithNext { a, b -> a.date to b.date }.toMap()
        // A kept day's next training day may have come before the rotation was set again.
        val until = turnAfter + kept.mapNotNull { k -> k.after?.let { k.turn.date to it } }
        // A missed day trained before the next training day came isn't missed.
        val missed = (keptTurns + elapsed).filter { turn ->
            val next = until[turn.date] ?: LocalDate.MAX
            turn.date !in started && sessions.none { it.position == turn.position && it.date > turn.date && it.date < next }
        }
        val trainToday = (keptTurns + elapsed).maxByOrNull { it.date }?.takeIf {
            today.dayOfWeek !in days && it in missed && it.date >= mondayOf(today) && (until[it.date] ?: LocalDate.MAX) > today
        }
        val now = thisWeek ?: WeekState(again = false, repeated = false, easy = false, emptyWeeks = place.empty)
        val anchorNow = reset ?: RotationAnchor(maxOf(cutoff, anchor.from), place.week, 0, 0, place.empty)
        return Rotation(
            planned = planned,
            missed = (missed + ahead).sortedBy { it.date },
            trainToday = trainToday,
            again = now.again,
            repeated = now.repeated,
            easy = now.easy,
            emptyWeeks = now.emptyWeeks,
            anchor = anchorNow,
            next = planned.firstOrNull()?.position ?: Position(program.weeks + 1, 0),
            since = anchor.from,
            weekDone = weekDone ?: anchorNow,
            turnAfter = until,
            read = Rotation.Read(program, days, sessions, today, skips, repeats),
        )
    }

    /** The Monday of [date]'s calendar week, which is a program week. */
    fun mondayOf(date: LocalDate): LocalDate = date.minusDays(date.dayOfWeek.value - 1L)

    /** This calendar week as the rotation reaches it. */
    private data class WeekState(val again: Boolean, val repeated: Boolean, val easy: Boolean, val emptyWeeks: Int)

    /**
     * Where the rotation stands: the program [week], the [day] the next training day takes, the
     * [rollback] day the week started at, the [empty] weeks in a row before it, and whether it
     * trains its week [again] or [repeated] by a `repeat_week`.
     */
    private data class Place(val week: Int, val day: Int, val rollback: Int, val empty: Int, val again: Boolean = false, val repeated: Boolean = false) {
        /** The week ended: a week that [counts] moves on (a [repeat] or an easy week stays), an empty one starts again. */
        fun endWeek(program: Program, counts: Boolean, repeat: Boolean): Place {
            if (!counts) return Place(week, rollback, rollback, empty + 1, again = true)
            val easy = empty >= 2
            val next = if (easy || repeat) week else week + 1
            // A new block starts at its first day; a day left over from the last one is dropped.
            val day = if (program.blockAt(next)?.id != program.blockAt(week)?.id) 0 else day
            return Place(next, day, day, 0, repeated = !easy && repeat)
        }

        /** The rotation set again from [from] at this place. */
        fun anchor(program: Program, from: LocalDate): RotationAnchor {
            val size = program.blockAt(week)?.days?.size ?: 1
            return RotationAnchor(from, week, day % size, rollback % size, empty)
        }
    }
}
