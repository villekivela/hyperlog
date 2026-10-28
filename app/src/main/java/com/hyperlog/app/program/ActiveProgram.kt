package com.hyperlog.app.program

import com.hyperlog.app.coach.AcceptedChange
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.ChangeDiff
import com.hyperlog.app.coach.Changes
import com.hyperlog.app.coach.DayDiff
import com.hyperlog.app.coach.Operation
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.coach.daysLeft
import com.hyperlog.app.coach.ordinal
import com.hyperlog.app.week.Rotation
import com.hyperlog.app.week.RotationTurn
import com.hyperlog.app.week.Rotations
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A date with the program day it trains, missed or to come (GLOSSARY: Turn): [day] as trained on
 * [date], with the Changes covering it, eased in an easy week.
 */
data class Turn(val date: LocalDate, val day: PlannedDay, val status: Status) {
    enum class Status { Ahead, Missed }

    val position: Position get() = day.position
}

/**
 * The program being run, where you are in it, and its Changes: [history] holds every accepted
 * one, oldest first, reverted ones included. [today] is the date the plan is read for. With
 * training days its days take turns on them: [rotation] (ADR 0006), whose next day is [position];
 * null while it runs as a sequence, whose next day goes on tomorrow once a session finished on
 * [lastTrained], today. Either way it hands out its [turns] resolved.
 */
data class ActiveProgram(
    val id: Long,
    val program: Program,
    val position: Position,
    val history: List<ChangeRecord> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val rotation: Rotation? = null,
    /** The date of the last session finished; null with none. */
    val lastTrained: LocalDate? = null,
) {
    /** Past the last day: every day has been trained. */
    val finished: Boolean get() = position.week > program.weeks

    /** The week of [position], the last one once the program is finished. */
    val week: Int get() = position.week.coerceAtMost(program.weeks)

    /** The Changes in force, oldest first. */
    val changes: List<AcceptedChange> get() = history.filter { it.revertedAt == null }.map { it.accepted }

    /**
     * The accepted Changes, not reverted, that still cover today or a later day, newest first:
     * the cards at the top of Coach. A date range runs to its last day, the rest of the block
     * while the block it was accepted in runs, the rest of the program until it's finished.
     */
    val inForce: List<ChangeRecord>
        get() = if (finished) emptyList() else history.filter { record ->
            // One that only ended others does nothing more.
            record.revertedAt == null && record.change.operations.isNotEmpty() && when (val scope = record.change.scope) {
                is Scope.Dates -> scope.to >= today
                Scope.RestOfProgram -> true
                Scope.RestOfBlock -> program.blockAt(position.week)?.id == record.accepted.blockId
            }
        }.sortedWith(compareByDescending<ChangeRecord> { it.acceptedAt }.thenByDescending { it.id })

    /** The ids of Changes ended by a later one's `replaces`. */
    val replaced: Set<Long> get() = history.flatMap { it.change.replaces }.toSet()

    /**
     * Every turn missed and to come, by date. With training days, the rotation's; a sequence has
     * one, its next day on today, or tomorrow once a session finished today. None once finished.
     */
    val turns: List<Turn> by lazy {
        if (rotation == null) {
            val date = if (lastTrained == today) today.plusDays(1) else today
            listOfNotNull(Changes.day(program, position, date, changes)?.let { Turn(date, it, Turn.Status.Ahead) })
        } else {
            (rotation.missed.mapNotNull { resolved(it, Turn.Status.Missed) } + rotation.planned.mapNotNull { resolved(it, Turn.Status.Ahead) })
                .sortedBy { it.date }
        }
    }

    /** Whether its days take turns on training days (ADR 0006), not running as a sequence. */
    val rotates: Boolean get() = rotation != null

    /** This week trains its program week again, after one with no session. */
    val again: Boolean get() = rotation?.again == true

    /** This week trains its program week again, by a `repeat_week`. */
    val repeated: Boolean get() = rotation?.repeated == true

    /** This week is an easy week, after [emptyWeeks] weeks with no session in a row. */
    val easyWeek: Boolean get() = rotation?.easy == true

    /** The weeks with no session in a row before this one. */
    val emptyWeeks: Int get() = rotation?.emptyWeeks ?: 0

    /** The next turn to train, on whatever date it falls; null once the program is finished. */
    val next: Turn? get() = turns.firstOrNull { it.status == Turn.Status.Ahead }

    /**
     * This week's last missed turn, while today has no training day and no later one came: it can
     * be trained today, so its day is read for today.
     */
    val trainToday: Turn?
        get() = rotation?.trainToday?.let { turn -> resolved(turn.copy(date = today), Turn.Status.Missed)?.copy(date = turn.date) }

    private fun resolved(turn: RotationTurn, status: Turn.Status): Turn? = dayOf(turn, changes)?.let { Turn(turn.date, it, status) }

    /** [turn]'s day as trained on its date with [changes], eased in an easy week. */
    private fun dayOf(turn: RotationTurn, changes: List<AcceptedChange>): PlannedDay? =
        Changes.day(program, turn.position, turn.date, changes)?.easyWeek(turn.easy)

    /**
     * The turns Program shows in [week], whose sessions of this program started on [trained]: those
     * missed (not of a week that ended with no session, which trains again) and those to come, none
     * of an easy week. Null for a week read by its place instead: one before the rotation started,
     * or any week of a sequence.
     */
    fun turnsOf(week: Int, trained: List<LocalDate>): List<Turn>? {
        val rotation = rotation ?: return null
        fun endedEmpty(date: LocalDate): Boolean {
            val monday = Rotations.mondayOf(date)
            return monday.plusDays(6) < today && trained.none { Rotations.mondayOf(it) == monday }
        }
        val shown = turns.filter { it.position.week == week && !it.day.easy && !(it.status == Turn.Status.Missed && endedEmpty(it.date)) }
        return shown.takeIf { it.isNotEmpty() || trained.any { it >= rotation.since } }
    }

    /**
     * The day at [position], at or after the next one, as Program shows it without a date: the next
     * day as its turn reads it; a later one with the Changes covering it, a date range by as many
     * days as its weeks hold ([datesWindow]). With it, the date-ranged Changes on it.
     */
    fun undated(position: Position): Pair<PlannedDay, List<AcceptedChange>>? {
        val written = program.plannedDay(position) ?: return null
        val next = next
        val ahead = program.ordinal(position) - program.ordinal(this.position)
        val covering = changes.filter { c ->
            val scope = c.change.scope
            when {
                next != null && next.position == position -> c.covers(written, next.date)
                scope is Scope.Dates -> ahead < datesWindow(scope)
                else -> c.covers(written, today)
            }
        }
        return Changes.edited(written, covering) to covering.filter { it.change.scope is Scope.Dates }
    }

    /**
     * [change] accepted at [position] today: its position operations run, and it is dated. With
     * training days each `skip_day` names the next turn to come not yet skipped, on or after its own
     * date when it has one, and it and `repeat_week` leave the position to the rotation (ADR 0006).
     */
    fun accept(change: Change): AcceptedChange {
        val trainingDays = rotation?.trainingDays
        val skipped = mutableSetOf<LocalDate>()
        val resolved = change.copy(
            operations = change.operations.map {
                when {
                    it is Operation.DeloadWeek && it.week == null -> it.copy(week = week)
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
            .fold(position) { p, op -> Changes.move(program, p, op) }
        return AcceptedChange(0, resolved, today, position, after, program.blockAt(week)?.id)
    }

    /**
     * What accepting [proposed] would change, on top of the Changes in force: where the next day
     * moves, and the first day of each kind it edits from there ([dayDiffs]).
     */
    fun diff(proposed: Change): ChangeDiff {
        // The Changes it replaces end first, as Revert would end them; the plan after has them gone.
        val (ended, kept) = history.filter { it.revertedAt == null }.partition { it.id in proposed.replaces }
        val from = movedTo(Changes.ending(program, position, ended.map { it.accepted }))
        val accepted = from.accept(proposed).copy(id = Long.MAX_VALUE)
        val after = from.movedTo(accepted.after)
        return changeDiff(position, accepted.after, after.dayDiffs(accepted, before = changes, after = kept.map { it.accepted } + accepted))
    }

    /**
     * What [record], a Change in force, does from the next day on: the days it still covers
     * ([dayDiffs]), each with every other Change in force applied before and after, and the move of
     * the position it made when it was accepted.
     */
    fun inForceDiff(record: AcceptedChange): ChangeDiff {
        val others = changes.filter { it.id != record.id }
        return changeDiff(record.before, record.after, dayDiffs(record, before = others, after = others + record))
    }

    /** A diff of [days] whose next day moves [from] → [to], when it moves. */
    private fun changeDiff(from: Position, to: Position, days: List<DayDiff>): ChangeDiff {
        val moved = to != from
        return ChangeDiff(
            next = if (moved) from to to else null,
            daysLeft = if (moved) program.daysLeft(from) to program.daysLeft(to) else null,
            days = days,
        )
    }

    /** At [position] instead, the rotation set again there as a Change moving it sets it. */
    private fun movedTo(position: Position): ActiveProgram =
        if (position == this.position) this else copy(position = position, rotation = rotation?.movedTo(position))

    /**
     * The first day of each kind of each block that [change]'s scope covers from the next day on and
     * that reads differently with the Changes [after] than with [before]. With training days each
     * is read on its turn's date; a sequence's are all read on the day a date range starts (today
     * at the earliest), as many as its weeks hold ([datesWindow]).
     */
    private fun dayDiffs(change: AcceptedChange, before: List<AcceptedChange>, after: List<AcceptedChange>): List<DayDiff> {
        val scope = change.change.scope
        val window = if (rotation != null) {
            rotation.planned.asSequence().filter { scope !is Scope.Dates || it.date in scope.from..scope.to }
        } else {
            val date = (scope as? Scope.Dates)?.from?.takeIf { it > today } ?: today
            val days = generateSequence(position) { program.next(it) }.map { RotationTurn(date, it) }
            if (scope is Scope.Dates) days.take(datesWindow(scope)) else days
        }
        val seen = mutableSetOf<Pair<String, String>>()
        return window
            .takeWhile { scope != Scope.RestOfBlock || program.blockAt(it.position.week)?.id == change.blockId }
            .filter { it.position.week <= program.weeks }
            .mapNotNull { turn ->
                val was = dayOf(turn, before) ?: return@mapNotNull null
                val now = dayOf(turn, after) ?: return@mapNotNull null
                val key = was.block.id to was.day.id
                if (key in seen) return@mapNotNull null
                val slots = Changes.slotDiffs(was, now)
                if (slots.isEmpty() && was.deload == now.deload) return@mapNotNull null
                seen += key
                DayDiff(now, slots)
            }.toList()
    }

    /**
     * Without training days, how many program days from [position] a date range still covers: one
     * week of program days per week of dates from the later of its start and today, and never more
     * days than dates.
     */
    private fun datesWindow(scope: Scope.Dates): Int {
        val from = scope.from.takeIf { it > today } ?: today
        val dates = ChronoUnit.DAYS.between(from, scope.to).toInt() + 1
        val perWeek = program.blockAt(position.week)?.days?.size ?: 0
        return minOf(dates, ((dates + 6) / 7) * perWeek).coerceAtLeast(0)
    }
}
