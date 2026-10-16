package com.hyperlog.app.ui.program

import com.hyperlog.app.coach.Changes
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.coach.SlotDiff
import com.hyperlog.app.coach.ordinal
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Day
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import com.hyperlog.app.session.PastSessionItem
import java.time.LocalDate

/** A day of a week as Program shows it, with the day as written or as it will be trained. */
sealed interface ProgramDay {
    val day: PlannedDay
    val position: Position get() = day.position

    /**
     * A day still to train, as the session will prescribe it ([Changes.dayAhead]). [endsOn] is the
     * end of a date-ranged Change on it, which the day may fall after.
     */
    data class Ahead(override val day: PlannedDay, val next: Boolean, val endsOn: LocalDate?) : ProgramDay

    /** A day trained: the session logged for it. */
    data class Done(override val day: PlannedDay, val session: PastSessionItem) : ProgramDay

    /** A day passed without a session, by a skip: what it prescribed, marked skipped. */
    data class Skipped(override val day: PlannedDay) : ProgramDay
}

/** What [previous]'s last week becomes in a block's first week: each day's slots that differ. */
data class BlockComparison(val previous: Block, val days: List<DayComparison>)

data class DayComparison(val day: Day, val slots: List<SlotDiff>)

/**
 * The weeks as Program reads them (docs/design/program-and-today.md): a week's days, each resolved
 * through [Changes] as Today and the session resolve it, and a block against the one before.
 */
object ProgramWeek {
    /**
     * [block] against the block before it, as written: the previous block's last week beside this
     * block's first, day by day (Day A with Day A) and slot by slot; null for the first block.
     */
    fun comparison(program: Program, block: Block): BlockComparison? {
        val previous = program.blocks.getOrNull(program.blocks.indexOf(block) - 1) ?: return null
        val days = block.days.indices.mapNotNull { i ->
            val after = program.plannedDay(Position(block.weekStart, i)) ?: return@mapNotNull null
            val before = program.plannedDay(Position(previous.weekEnd, i))?.slots.orEmpty()
            val slots = after.slots.mapNotNull { slot ->
                val was = before.firstOrNull { it.id == slot.id }
                if (was != null && was.exercise == slot.exercise && was.sets == slot.sets) null else SlotDiff(was, slot)
            } + before.filter { was -> after.slots.none { it.id == was.id } }.map { SlotDiff(it, null) }
            slots.takeIf { it.isNotEmpty() }?.let { DayComparison(after.day, it) }
        }
        return BlockComparison(previous, days)
    }

    /**
     * The days of [week]: those before the position done (with the latest of [sessions] for each)
     * or skipped, the rest still to train, without dates.
     */
    fun days(active: ActiveProgram, week: Int, sessions: List<PastSessionItem>): List<ProgramDay> {
        val program = active.program
        val block = program.blockAt(week) ?: return emptyList()
        val next = program.ordinal(active.position)
        // The latest session of each day of this program; a repeated week trains its days again.
        val trained = sessions.filter { it.programId == active.id && it.position != null }
            .sortedBy { it.startedAt }.associateBy { it.position!! }
        return block.days.indices.mapNotNull { i ->
            val position = Position(week, i)
            if (program.ordinal(position) < next) {
                val planned = Changes.day(program, position, active.today, active.changes) ?: return@mapNotNull null
                return@mapNotNull trained[position]?.let { ProgramDay.Done(planned, it) } ?: ProgramDay.Skipped(planned)
            }
            val (day, dated) = Changes.dayAhead(program, active.position, position, active.today, active.changes) ?: return@mapNotNull null
            // Trained later than the diff's rule reads it, a day can fall past a date range's end.
            val endsOn = dated.filter { it.change.operations.isNotEmpty() }.mapNotNull { (it.change.scope as? Scope.Dates)?.to }.minOrNull()
            ProgramDay.Ahead(day, program.ordinal(position) == next, endsOn)
        }
    }
}
