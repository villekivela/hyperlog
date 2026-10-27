package com.hyperlog.app.coach

import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.PlannedSlot
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A Change as accepted: the day it was accepted, the position it moved (the same twice when it
 * moves none) and the block it was accepted in, which `rest_of_block` keeps to. Its `deload_week`
 * operations name their week.
 */
data class AcceptedChange(
    val id: Long,
    val change: Change,
    val acceptedOn: LocalDate,
    val before: Position,
    val after: Position,
    val blockId: String?,
) {
    /** Whether this Change's plan edits cover a session of [day] on [date]. */
    fun covers(day: PlannedDay, date: LocalDate): Boolean = when (val scope = change.scope) {
        is Scope.Dates -> date in scope.from..scope.to
        Scope.RestOfProgram -> date >= acceptedOn
        Scope.RestOfBlock -> date >= acceptedOn && day.block.id == blockId
    }
}

/** What a Change would do to the plan: where the next day moves, and the days it edits. */
data class ChangeDiff(
    /** The next day, before → after; null when it doesn't move. */
    val next: Pair<Position, Position>?,
    /** Days left in the program, before → after; null when the position doesn't move. */
    val daysLeft: Pair<Int, Int>?,
    /** The first day of each kind the Change edits, as it will be trained. */
    val days: List<DayDiff>,
)

data class DayDiff(val day: PlannedDay, val slots: List<SlotDiff>)

/** One slot, before and after: null before for an added exercise, null after for a removed one. */
data class SlotDiff(val before: PlannedSlot?, val after: PlannedSlot?)

/**
 * The plan: the program as written, with accepted Changes on top. Pure, so the session, Today,
 * the Program screen, the diff view and the coaches all resolve a day the same way.
 */
object Changes {
    /**
     * The day at [position] as trained on [date]: the program's day with the plan edits of every
     * Change in [changes] (accepted ones, oldest first) whose scope covers it.
     */
    fun day(program: Program, position: Position, date: LocalDate, changes: List<AcceptedChange>): PlannedDay? {
        val written = program.plannedDay(position) ?: return null
        return changes.filter { it.covers(written, date) }.fold(written) { day, c -> c.change.operations.fold(day, ::edit) }
    }

    /**
     * [change] accepted at [position] on [today]: its position operations run, and it is dated.
     * With training days, [trainingDays] are the coming ones, in order: each `skip_day` names the
     * next one not yet skipped, on or after its own date when it has one, and it and `repeat_week`
     * leave the position to the rotation (ADR 0006).
     */
    fun accept(program: Program, position: Position, change: Change, today: LocalDate, trainingDays: List<LocalDate>? = null): AcceptedChange {
        val skipped = mutableSetOf<LocalDate>()
        val resolved = change.copy(
            operations = change.operations.map {
                when {
                    it is Operation.DeloadWeek && it.week == null -> it.copy(week = position.week.coerceAtMost(program.weeks))
                    it is Operation.SkipDay && trainingDays != null -> {
                        val wanted = it.date?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() }
                        val date = trainingDays.firstOrNull { d -> (wanted == null || d >= wanted) && d !in skipped } ?: wanted ?: today
                        skipped += date
                        it.copy(date = date.toString())
                    }
                    else -> it
                }
            },
        )
        val rotating = trainingDays != null
        val after = resolved.operations.filter { it.movesPosition && !(rotating && (it is Operation.SkipDay || it is Operation.RepeatWeek)) }
            .fold(position) { p, op -> move(program, p, op) }
        return AcceptedChange(0, resolved, today, position, after, program.blockAt(position.week.coerceAtMost(program.weeks))?.id)
    }

    /**
     * The position once [accepted] is reverted, from [current]: its move is taken back, and days
     * trained since still count.
     */
    fun revert(program: Program, current: Position, accepted: AcceptedChange): Position {
        if (current == accepted.after) return accepted.before
        val moved = program.ordinal(accepted.after) - program.ordinal(accepted.before)
        return program.positionAt(program.ordinal(current) - moved)
    }

    /**
     * What accepting [proposed] at [position] on [today] would change, on top of [changes]: the
     * days it covers from the position it leaves, the first of each day of each block.
     */
    fun diff(program: Program, position: Position, today: LocalDate, changes: List<AcceptedChange>, proposed: Change, trainingDays: List<LocalDate>? = null): ChangeDiff {
        // The Changes it replaces end first, as Revert would end them; the plan after has them gone.
        val (ended, kept) = changes.partition { it.id in proposed.replaces }
        val from = ending(program, position, ended)
        val accepted = accept(program, from, proposed, today, trainingDays).copy(id = Long.MAX_VALUE)
        val date = (proposed.scope as? Scope.Dates)?.from?.takeIf { it > today } ?: today
        val window = when (val scope = proposed.scope) {
            is Scope.Dates -> {
                // One week of program days per week of dates, and never more days than dates.
                val dates = ChronoUnit.DAYS.between(scope.from, scope.to).toInt() + 1
                val weeks = (dates + 6) / 7
                val perWeek = program.blockAt(accepted.after.week)?.days?.size ?: 0
                generateSequence(accepted.after) { program.next(it) }.take(minOf(dates, weeks * perWeek))
            }
            Scope.RestOfBlock -> generateSequence(accepted.after) { program.next(it) }
                .takeWhile { program.blockAt(it.week)?.id == accepted.blockId }
            Scope.RestOfProgram -> generateSequence(accepted.after) { program.next(it) }
        }.filter { it.week <= program.weeks }
        val seen = mutableSetOf<Pair<String, String>>()
        val days = window.mapNotNull { p ->
            val before = day(program, p, date, changes) ?: return@mapNotNull null
            val after = day(program, p, date, kept + accepted) ?: return@mapNotNull null
            val key = before.block.id to before.day.id
            if (key in seen) return@mapNotNull null
            val slots = slotDiffs(before, after)
            if (slots.isEmpty() && before.deload == after.deload) return@mapNotNull null
            seen += key
            DayDiff(after, slots)
        }.toList()
        val moved = accepted.after != position
        return ChangeDiff(
            next = if (moved) position to accepted.after else null,
            daysLeft = if (moved) program.daysLeft(position) to program.daysLeft(accepted.after) else null,
            days = days,
        )
    }

    /** Where [position] goes once [ended] are reverted, newest first, as Revert takes each back. */
    fun ending(program: Program, position: Position, ended: List<AcceptedChange>): Position =
        ended.sortedByDescending { it.id }.fold(position) { p, c -> revert(program, p, c) }

    /**
     * What [record], a Change in force, does from [position] on [today]: the days it still covers,
     * each with every other Change in [changes] applied before and after, and the move of the
     * position it made when it was accepted.
     */
    fun inForceDiff(program: Program, position: Position, today: LocalDate, changes: List<AcceptedChange>, record: AcceptedChange): ChangeDiff {
        val others = changes.filter { it.id != record.id }
        val date = (record.change.scope as? Scope.Dates)?.from?.takeIf { it > today } ?: today
        val window = when (val scope = record.change.scope) {
            is Scope.Dates -> generateSequence(position) { program.next(it) }.take(datesWindow(program, position, today, scope))
            Scope.RestOfBlock -> generateSequence(position) { program.next(it) }.takeWhile { program.blockAt(it.week)?.id == record.blockId }
            Scope.RestOfProgram -> generateSequence(position) { program.next(it) }
        }.filter { it.week <= program.weeks }
        val seen = mutableSetOf<Pair<String, String>>()
        val days = window.mapNotNull { p ->
            val before = day(program, p, date, others) ?: return@mapNotNull null
            val after = day(program, p, date, others + record) ?: return@mapNotNull null
            val key = before.block.id to before.day.id
            if (key in seen) return@mapNotNull null
            val slots = slotDiffs(before, after)
            if (slots.isEmpty() && before.deload == after.deload) return@mapNotNull null
            seen += key
            DayDiff(after, slots)
        }.toList()
        val moved = record.after != record.before
        return ChangeDiff(
            next = if (moved) record.before to record.after else null,
            daysLeft = if (moved) program.daysLeft(record.before) to program.daysLeft(record.after) else null,
            days = days,
        )
    }

    /**
     * How many program days from [next] a date range still covers on [today]: one week of program
     * days per week of dates from the later of its start and today, and never more days than dates.
     */
    private fun datesWindow(program: Program, next: Position, today: LocalDate, scope: Scope.Dates): Int {
        val from = scope.from.takeIf { it > today } ?: today
        val dates = ChronoUnit.DAYS.between(from, scope.to).toInt() + 1
        val perWeek = program.blockAt(next.week)?.days?.size ?: 0
        return minOf(dates, ((dates + 6) / 7) * perWeek).coerceAtLeast(0)
    }

    /**
     * The day at [position], at or after the next one at [next], as Program shows it without
     * dates: the next day as trained [today], as Today shows it; a later one with the Changes in
     * [changes] that cover it as the in-force diff reads them, a date range by [datesWindow].
     * Returns the day and the date-ranged Changes on it.
     */
    fun dayAhead(program: Program, next: Position, position: Position, today: LocalDate, changes: List<AcceptedChange>): Pair<PlannedDay, List<AcceptedChange>>? {
        val written = program.plannedDay(position) ?: return null
        val ahead = program.ordinal(position) - program.ordinal(next)
        val covering = changes.filter { c ->
            when (val scope = c.change.scope) {
                is Scope.Dates -> if (ahead == 0) c.covers(written, today) else ahead < datesWindow(program, next, today, scope)
                else -> c.covers(written, today)
            }
        }
        val day = covering.fold(written) { day, c -> c.change.operations.fold(day, ::edit) }
        return day to covering.filter { it.change.scope is Scope.Dates }
    }

    private fun slotDiffs(before: PlannedDay, after: PlannedDay): List<SlotDiff> {
        val old = before.slots.associateBy { it.id }
        val new = after.slots.associateBy { it.id }
        val changed = after.slots.mapNotNull { s ->
            val was = old[s.id]
            if (was == s) null else SlotDiff(was, s)
        }
        val removed = before.slots.filter { it.id !in new }.map { SlotDiff(it, null) }
        // In the day's order: removed slots where they were.
        val order = (before.slots.map { it.id } + after.slots.map { it.id }).distinct()
        return (changed + removed).sortedBy { order.indexOf((it.after ?: it.before)!!.id) }
    }

    private fun move(program: Program, p: Position, op: Operation): Position = when (op) {
        is Operation.SkipDay -> program.positionAt(program.ordinal(p) + 1)
        is Operation.ShiftProgram -> {
            val week = p.week - op.weeks
            when {
                week < 1 -> Position(1, 0)
                week > program.weeks -> program.positionAt(Int.MAX_VALUE)
                else -> Position(week, p.day.coerceAtMost(program.blockAt(week)!!.days.size - 1))
            }
        }
        is Operation.RepeatWeek -> {
            val lastTrained = if (p.day > 0) p.week else p.week - 1
            Position(lastTrained.coerceIn(1, program.weeks), 0)
        }
        else -> p
    }

    /** One plan edit on [day]; position operations and edits for other slots or days leave it. */
    private fun edit(day: PlannedDay, op: Operation): PlannedDay = when (op) {
        is Operation.Swap -> day.mapSlot(op.slot) { s ->
            s.copy(
                exercise = op.exercise,
                sets = s.setsFor(op.exercise),
                alternatives = (listOf(s.exercise) + s.alternatives).distinct() - op.exercise,
                inPlaceOf = (s.inPlaceOf ?: s.exercise).takeIf { it != op.exercise },
            )
        }
        is Operation.Remove -> day.copy(slots = day.slots.filter { it.id != op.slot })
        is Operation.Add -> if (op.day != day.day.id || day.slots.any { it.id == addedId(op) }) day else {
            val slot = PlannedSlot(
                id = addedId(op),
                label = ADDED_LABEL,
                group = (day.slots.maxOfOrNull { it.group } ?: -1) + 1,
                exercise = op.exercise,
                alternatives = emptyList(),
                note = null,
                sets = op.sets.flatMap { line -> List(line.count) { SetPrescription(line.reps, line.rir, line.rest) } },
            )
            val at = day.slots.indexOfFirst { it.id == op.after }.let { if (it < 0) day.slots.size else it + 1 }
            day.copy(slots = day.slots.toMutableList().apply { add(at, slot) })
        }
        is Operation.AdjustSets -> day.mapSlot(op.slot) { s ->
            s.copy(sets = s.sets.withCount(op.sets), alternativeSets = s.alternativeSets.mapValues { it.value.withCount(op.sets) })
        }
        is Operation.AdjustLoad -> day.mapSlots(op.slot) { it.copy(loadPercent = it.loadPercent + op.percent) }
        is Operation.AdjustRir -> day.mapSlots(op.slot) { s ->
            fun List<SetPrescription>.eased() = map { set -> set.copy(rir = set.rir?.let { (it + op.by).coerceIn(0, 10) }) }
            s.copy(sets = s.sets.eased(), alternativeSets = s.alternativeSets.mapValues { it.value.eased() })
        }
        is Operation.RemoveTechnique -> day.mapSlots(op.slot) { s ->
            fun List<SetPrescription>.plain() = map { set -> if (set.technique != null && (op.technique == null || set.technique.kind == op.kind)) set.copy(technique = null) else set }
            s.copy(sets = s.sets.plain(), alternativeSets = s.alternativeSets.mapValues { it.value.plain() })
        }
        is Operation.DeloadWeek -> if (op.week != day.position.week) day else {
            fun List<SetPrescription>.deloaded() = withCount((size + 1) / 2).map { set ->
                set.copy(rir = set.rir?.let { (it + DELOAD_RIR).coerceAtMost(10) }, technique = null)
            }
            day.copy(deload = true, slots = day.slots.map { s -> s.copy(sets = s.sets.deloaded(), alternativeSets = s.alternativeSets.mapValues { it.value.deloaded() }) })
        }
        is Operation.SkipDay, is Operation.ShiftProgram, is Operation.RepeatWeek -> day
    }

    /** Added exercises stand in a slot of their own, labelled `+` in the day's numbering. */
    const val ADDED_LABEL = "+"
    private const val DELOAD_RIR = 2

    private fun addedId(op: Operation.Add) = "+${op.exercise}"

    private fun PlannedDay.mapSlot(id: String, f: (PlannedSlot) -> PlannedSlot) = copy(slots = slots.map { if (it.id == id) f(it) else it })
    private fun PlannedDay.mapSlots(id: String?, f: (PlannedSlot) -> PlannedSlot) = copy(slots = slots.map { if (id == null || it.id == id) f(it) else it })

    /** The first [count] sets, or the last one repeated without its technique (a back-off stays one). */
    private fun List<SetPrescription>.withCount(count: Int): List<SetPrescription> {
        if (isEmpty() || count < 1) return this
        if (count <= size) return take(count)
        val extra = last().copy(technique = null)
        return this + List(count - size) { extra }
    }
}

/** Days before [position] in the program's sequence; the program's length when finished. */
fun Program.ordinal(position: Position): Int {
    if (position.week > weeks) return totalDays
    val before = (1 until position.week).sumOf { blockAt(it)!!.days.size }
    return before + position.day
}

/** The position [ordinal] days into the program, clamped to its start and its finish. */
fun Program.positionAt(ordinal: Int): Position {
    var left = ordinal.coerceAtLeast(0)
    for (week in 1..weeks) {
        val days = blockAt(week)!!.days.size
        if (left < days) return Position(week, left)
        left -= days
    }
    return Position(weeks + 1, 0)
}

val Program.totalDays: Int get() = (1..weeks).sumOf { blockAt(it)!!.days.size }

fun Program.daysLeft(position: Position): Int = totalDays - ordinal(position)
