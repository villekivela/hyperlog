package com.hyperlog.app.coach

import com.hyperlog.app.program.Program
import java.time.LocalDate

/** A past training day's turn of program day [day] (its id, named [name]), trained or [missed]. */
data class DayTurn(val date: LocalDate, val day: String, val name: String, val missed: Boolean)

/**
 * A finished session as the coach line reads it: [eased] for a deload or an easy week, which
 * aren't counted in a stall.
 */
data class LoggedSession(val id: Long, val date: LocalDate, val gym: String?, val eased: Boolean, val exercises: List<LoggedExercise>)

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

/** What the coach line reads: the running program's past turns, and the finished sessions oldest first. */
data class CoachLog(val turns: List<DayTurn> = emptyList(), val sessions: List<LoggedSession> = emptyList())

/** A pattern in the log a check-in should look at (SPEC, Coaching → The coach line). */
sealed interface CoachLine {
    /** What × hides: the pattern and its latest evidence, so the line comes back once it grows or comes back. */
    val key: String
        get() = when (this) {
            is DayMissed -> "missed:$day:$last"
            is Stall -> "stall:$exercise:$last"
            is Grinding -> "grinding:$exercise:$last"
            is SameSwap -> "swap:$slot:$swappedFor:$last"
        }

    /** Program day [day] missed [missed] times in its last [of] turns, the last on [last]. */
    data class DayMissed(val day: String, val name: String, val missed: Int, val of: Int, val last: LocalDate) : CoachLine

    /** [exercise]'s estimate flat or down over its last [sessions] counted sessions, the last session [last]. */
    data class Stall(val exercise: String, val sessions: Int, val last: Long) : CoachLine

    /** [exercise]'s last [sessions] sessions each had a work set at 0 RIR where the target was 2 or more. */
    data class Grinding(val exercise: String, val sessions: Int, val last: Long) : CoachLine

    /**
     * [slot]'s [exercise] swapped for [swappedFor] in its last [sessions] sessions, all at [gym]
     * (null for different gyms, or none).
     */
    data class SameSwap(val slot: String, val exercise: String, val swappedFor: String, val gym: String?, val sessions: Int, val last: Long) : CoachLine
}

/** The coach line's patterns, found in the log. It only points: nothing here changes the plan (ADR 0003). */
object CoachLines {
    /** Every pattern in [log], in the order they show: days missed, then stalls, grinding and swaps. */
    fun of(log: CoachLog): List<CoachLine> = missedDays(log.turns) + stalls(log.sessions) + grinding(log.sessions) + swaps(log.sessions)

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
     * more counted sessions than before them; deload and easy-week sessions aren't counted. The
     * latest session first.
     */
    private fun stalls(sessions: List<LoggedSession>): List<CoachLine.Stall> =
        sessions.filter { !it.eased }
            .flatMap { s -> s.exercises.mapNotNull { e -> e.estimateKg?.let { Triple(e.exercise to e.machine, s.id, it) } } }
            .groupBy({ it.first }, { it.second to it.third })
            .mapNotNull { (line, points) ->
                val run = (STALL until points.size).filter { n ->
                    val before = points[points.size - n - 1].second
                    points.takeLast(n).all { it.second <= before + SAME_KG }
                }.maxOrNull() ?: return@mapNotNull null
                CoachLine.Stall(line.first, run, points.last().first)
            }
            .sortedByDescending { it.last }

    /**
     * An exercise whose last 3 or more sessions each had a work set at 0 RIR where the target was 2
     * or more; the latest session first.
     */
    private fun grinding(sessions: List<LoggedSession>): List<CoachLine.Grinding> =
        sessions.flatMap { s -> s.exercises.map { e -> e.exercise to (s.id to e.sets.any { it.rir == 0 && (it.target ?: 0) >= GRIND_TARGET }) } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (exercise, done) ->
                val run = done.takeLastWhile { it.second }
                if (run.size < GRINDING) null else CoachLine.Grinding(exercise, run.size, run.last().first)
            }
            .sortedByDescending { it.last }

    /** A slot swapped for the same exercise in its last 3 or more sessions; the latest session first. */
    private fun swaps(sessions: List<LoggedSession>): List<CoachLine.SameSwap> =
        sessions.flatMap { s -> s.exercises.mapNotNull { e -> e.slot?.let { slot -> slot to (s to e) } } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (slot, done) ->
                val (_, latest) = done.last()
                val planned = latest.inPlaceOf ?: return@mapNotNull null
                val run = done.takeLastWhile { (_, e) -> e.inPlaceOf == planned && e.exercise == latest.exercise }
                if (run.size < SWAPS) return@mapNotNull null
                val gym = run.map { it.first.gym }.distinct().singleOrNull()
                CoachLine.SameSwap(slot, planned, latest.exercise, gym, run.size, run.last().first.id)
            }
            .sortedByDescending { it.last }

    /**
     * The one line Today shows: the first of [lines] not [hidden] by its × and not covered by a
     * Change [inForce] in [program]; null for none.
     */
    fun shown(lines: List<CoachLine>, hidden: Set<String>, inForce: List<Change>, program: Program?): CoachLine? {
        val slots = program?.blocks.orEmpty().flatMap { b -> b.days.flatMap { d -> d.groups.flatMap { g -> g.slots.map { it.id to (d.id to it.exercise) } } } }.toMap()
        val operations = inForce.flatMap { it.operations }
        return lines.firstOrNull { line -> line.key !in hidden && operations.none { covers(it, line, slots) } }
    }

    /**
     * Whether [op] covers [line], [slots] giving each slot's day and exercise. A day: an operation
     * on one of its slots, adding to it, a deload or a move of the position. An exercise: an
     * operation on its slot, swapping or adding it, one on every slot (no slot named), or a deload.
     * A swap: an operation on its slot.
     */
    private fun covers(op: Operation, line: CoachLine, slots: Map<String, Pair<String, String>>): Boolean {
        val slot = when (op) {
            is Operation.Swap -> op.slot
            is Operation.Remove -> op.slot
            is Operation.AdjustSets -> op.slot
            is Operation.AdjustLoad -> op.slot
            is Operation.AdjustRir -> op.slot
            is Operation.RemoveTechnique -> op.slot
            else -> null
        }
        val everySlot = slot == null && (op is Operation.AdjustLoad || op is Operation.AdjustRir || op is Operation.RemoveTechnique)
        fun exercise(id: String) = everySlot || op is Operation.DeloadWeek || slots[slot]?.second == id ||
            (op is Operation.Swap && op.exercise == id) || (op is Operation.Add && op.exercise == id)
        return when (line) {
            is CoachLine.DayMissed -> op.movesPosition || op is Operation.DeloadWeek || slots[slot]?.first == line.day || (op is Operation.Add && op.day == line.day)
            is CoachLine.Stall -> exercise(line.exercise)
            is CoachLine.Grinding -> exercise(line.exercise)
            is CoachLine.SameSwap -> slot == line.slot
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
