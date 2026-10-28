package com.hyperlog.app.coach

import com.hyperlog.app.program.Program
import java.time.LocalDate

/** A past training day's turn of program day [day] (its id, named [name]), trained or [missed]. */
data class DayTurn(val date: LocalDate, val day: String, val name: String, val missed: Boolean)

/**
 * A finished session as the coach line reads it: [eased] in a deload, an easy week or a week the
 * plan eases (as Ramp-in), which aren't counted in a stall.
 */
data class LoggedSession(val date: LocalDate, val eased: Boolean, val exercises: List<LoggedExercise>)

/**
 * An exercise of a [LoggedSession]: on [machine] for machines (ADR 0002), with the estimate after
 * the session on it; null with no set that gives one.
 */
data class LoggedExercise(
    val exercise: String,
    val machine: Long? = null,
    val estimateKg: Double? = null,
    /** Its work sets, in order. */
    val sets: List<RirSet> = emptyList(),
    /** The program slot it filled; null when added ad hoc. */
    val slot: String? = null,
    /** The exercise the session's plan had in [slot], when it was swapped for this one. */
    val inPlaceOf: String? = null,
)

/** A work set's logged [rir] and the RIR its prescription asked for ([target]; null without one). */
data class RirSet(val rir: Int?, val target: Int?)

/**
 * What the coach line reads: the running program's past turns, and the finished sessions oldest
 * first. A pattern whose latest evidence is before [since] shows no line.
 */
data class CoachLog(val turns: List<DayTurn> = emptyList(), val sessions: List<LoggedSession> = emptyList(), val since: LocalDate = LocalDate.MIN)

/** A pattern in the log a check-in should look at (SPEC, Coaching → The coach line). */
sealed interface CoachLine {
    /** The day of its latest evidence: the last session behind it, or the last day missed. */
    val last: LocalDate

    /** What × hides: the pattern and its latest evidence, so the line comes back once it grows or comes back. */
    val key: String
        get() = when (this) {
            is DayMissed -> "missed:$day:$last"
            is Stall -> "stall:$exercise:$last"
            is Grinding -> "grinding:$exercise:$last"
            is SameSwap -> "swap:$slot:$swappedFor:$last"
        }

    /** Program day [day] missed [missed] times in its last [of] turns. */
    data class DayMissed(val day: String, val name: String, val missed: Int, val of: Int, override val last: LocalDate) : CoachLine

    /** [exercise]'s estimate flat or down over its last [sessions] counted sessions. */
    data class Stall(val exercise: String, val sessions: Int, override val last: LocalDate) : CoachLine

    /** [exercise]'s last [sessions] sessions each had a work set at 0 RIR where the target was 2 or more. */
    data class Grinding(val exercise: String, val sessions: Int, override val last: LocalDate) : CoachLine

    /** [slot]'s [exercise] swapped for [swappedFor] in its last [sessions] sessions, at whatever gym. */
    data class SameSwap(val slot: String, val exercise: String, val swappedFor: String, val sessions: Int, override val last: LocalDate) : CoachLine
}

/** The coach line's patterns, found in the log. It only points: nothing here changes the plan (ADR 0003). */
object CoachLines {
    /** What a check-in from [line] keeps of it (the pattern and what it names), for the scripted coach. */
    fun about(line: CoachLine): About = when (line) {
        is CoachLine.DayMissed -> About(pattern = LinePattern.DayMissed, dayName = line.name, missed = line.missed, of = line.of)
        is CoachLine.Stall -> About(pattern = LinePattern.Stall, exercise = line.exercise)
        is CoachLine.Grinding -> About(pattern = LinePattern.Grinding, exercise = line.exercise)
        is CoachLine.SameSwap -> About(pattern = LinePattern.SameSwap, slot = line.slot, exercise = line.exercise, swappedFor = line.swappedFor)
    }

    /** Every pattern in [log], in the order they show: days missed, then stalls, grinding and swaps. */
    fun of(log: CoachLog): List<CoachLine> =
        (missedDays(log.turns) + stalls(log.sessions) + grinding(log.sessions) + swaps(log.sessions)).filter { it.last >= log.since }

    /** A program day missed 3 times in its last 4 turns; the latest miss first. */
    private fun missedDays(turns: List<DayTurn>): List<CoachLine.DayMissed> =
        turns.groupBy { it.day }.values.mapNotNull { own ->
            val last = own.sortedBy { it.date }.takeLast(TURNS)
            val missed = last.filter { it.missed }
            if (missed.size < MISSED) null
            else CoachLine.DayMissed(last.last().day, last.last().name, missed.size, last.size, missed.last().date)
        }.sortedByDescending { it.last }

    /**
     * An exercise's estimate (on each machine, for machines) no higher after each of its last 3 or
     * more counted sessions than before them; [LoggedSession.eased] sessions aren't counted. The
     * latest session first.
     */
    private fun stalls(sessions: List<LoggedSession>): List<CoachLine.Stall> =
        sessions.filter { !it.eased }
            .flatMap { s -> s.exercises.mapNotNull { e -> e.estimateKg?.let { kg -> (e.exercise to e.machine) to Point(s.date, kg) } } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (line, points) ->
                val run = (STALL until points.size).filter { n ->
                    val before = points[points.size - n - 1].kg
                    points.takeLast(n).all { it.kg <= before + SAME_KG }
                }.maxOrNull() ?: return@mapNotNull null
                CoachLine.Stall(line.first, run, points.last().date)
            }
            .sortedByDescending { it.last }

    /** An estimate after the session on [date]. */
    private data class Point(val date: LocalDate, val kg: Double)

    /**
     * An exercise whose last 3 or more sessions each had a work set at 0 RIR where the target was 2
     * or more; the latest session first.
     */
    private fun grinding(sessions: List<LoggedSession>): List<CoachLine.Grinding> =
        sessions.flatMap { s -> s.exercises.map { e -> e.exercise to (s.date to e.sets.any { it.rir == 0 && (it.target ?: 0) >= GRIND_TARGET }) } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (exercise, done) ->
                val run = done.takeLastWhile { (_, ground) -> ground }
                if (run.size < GRINDING) null else CoachLine.Grinding(exercise, run.size, run.last().first)
            }
            .sortedByDescending { it.last }

    /** A slot swapped for the same exercise in its last 3 or more sessions; the latest session first. */
    private fun swaps(sessions: List<LoggedSession>): List<CoachLine.SameSwap> =
        sessions.flatMap { s -> s.exercises.mapNotNull { e -> e.slot?.let { slot -> slot to (s.date to e) } } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (slot, done) ->
                val (_, latest) = done.last()
                val planned = latest.inPlaceOf ?: return@mapNotNull null
                val run = done.takeLastWhile { (_, e) -> e.inPlaceOf == planned && e.exercise == latest.exercise }
                if (run.size < SWAPS) return@mapNotNull null
                CoachLine.SameSwap(slot, planned, latest.exercise, run.size, run.last().first)
            }
            .sortedByDescending { it.last }

    /**
     * The one line Today shows: the first of [lines] not [hidden] by its × and not covered by a
     * Change [inForce] in [program]; null for none.
     */
    fun shown(lines: List<CoachLine>, hidden: Set<String>, inForce: List<Change>, program: Program?): CoachLine? {
        // A slot id may come back in later blocks, on another day or with another exercise.
        val slots = program?.blocks.orEmpty().flatMap { b -> b.days.flatMap { d -> d.groups.flatMap { g -> g.slots.map { it.id to SlotPlace(d.id, it.exercise) } } } }
            .groupBy({ it.first }, { it.second })
        val operations = inForce.flatMap { it.operations }
        return lines.firstOrNull { line -> line.key !in hidden && operations.none { covers(it, line, slots[it.editsSlot].orEmpty()) } }
    }

    /** Where a slot is: its [day]'s id and its [exercise]. */
    private data class SlotPlace(val day: String, val exercise: String)

    /**
     * Whether [op] covers [line], [places] being where its slot is. A day: an operation on one of
     * its slots, adding to it, a deload or a move of the position. An exercise: an operation on its
     * slot, swapping or adding it, one on every slot, or a deload. A swap: an operation on its slot.
     */
    private fun covers(op: Operation, line: CoachLine, places: List<SlotPlace>): Boolean {
        fun exercise(id: String) = op.everySlot || op is Operation.DeloadWeek || places.any { it.exercise == id } ||
            (op is Operation.Swap && op.exercise == id) || (op is Operation.Add && op.exercise == id)
        return when (line) {
            is CoachLine.DayMissed -> op.movesPosition || op is Operation.DeloadWeek || places.any { it.day == line.day } || (op is Operation.Add && op.day == line.day)
            is CoachLine.Stall -> exercise(line.exercise)
            is CoachLine.Grinding -> exercise(line.exercise)
            is CoachLine.SameSwap -> op.editsSlot == line.slot
        }
    }

    private const val TURNS = 4
    private const val MISSED = 3
    private const val STALL = 3
    private const val GRINDING = 3
    private const val GRIND_TARGET = 2
    private const val SWAPS = 3

    /** Estimates this close are the same: rounding is never a gain. */
    private const val SAME_KG = 0.001
}
