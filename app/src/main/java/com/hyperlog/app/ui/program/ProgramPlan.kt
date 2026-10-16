package com.hyperlog.app.ui.program

import com.hyperlog.app.coach.Changes
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.coach.SlotDiff
import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Day
import com.hyperlog.app.program.Program
import com.hyperlog.app.coach.ordinal
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Position
import com.hyperlog.app.session.PastSessionItem
import java.time.LocalDate

/** A day of a week as Program shows it. */
sealed interface PlanDay {
    val position: Position

    /**
     * A day still to train, as the session will prescribe it on [date]: the date it is read for,
     * one week of program days per week of dates from the next day, as the diff reads a Change.
     */
    data class Ahead(val day: PlannedDay, val next: Boolean, val date: LocalDate, val endsOn: LocalDate?) : PlanDay {
        override val position get() = day.position
    }

    /** A day trained: the session logged for it, and the day as written, for its name. */
    data class Done(val day: PlannedDay, val session: PastSessionItem) : PlanDay {
        override val position get() = day.position
    }

    /** A day passed without a session, by a skip: its plan, marked skipped. */
    data class Skipped(val day: PlannedDay) : PlanDay {
        override val position get() = day.position
    }
}

/** What [previous]'s last week becomes in a block's first week: each day's slots that differ. */
data class BlockComparison(val previous: Block, val days: List<DayComparison>)

data class DayComparison(val day: Day, val slots: List<SlotDiff>)

/**
 * The plan as Program reads it (docs/design/program-and-today.md): a week's days, each resolved
 * through [Changes.day] as Today and the session resolve it.
 */
object ProgramPlan {
    /**
     * [block] against the block before it, as written: the previous block's last week beside this
     * block's first, day by day (Day A with Day A) and slot by slot; null for the first block.
     */
    fun from(program: Program, block: Block): BlockComparison? {
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

    fun week(active: ActiveProgram, week: Int, sessions: List<PastSessionItem>): List<PlanDay> {
        val program = active.program
        val block = program.blockAt(week) ?: return emptyList()
        val from = program.ordinal(active.position)
        val perWeek = program.blockAt(active.position.week)?.days?.size ?: block.days.size
        // The latest session of each day of this program; a repeated week trains its days again.
        val trained = sessions.filter { it.programId == active.id && it.position != null }
            .sortedBy { it.startedAt }.associateBy { it.position!! }
        return block.days.indices.mapNotNull { i ->
            val position = Position(week, i)
            val ahead = program.ordinal(position) - from
            if (ahead < 0) {
                val planned = Changes.day(program, position, active.today, active.changes) ?: return@mapNotNull null
                return@mapNotNull trained[position]?.let { PlanDay.Done(planned, it) } ?: PlanDay.Skipped(planned)
            }
            val date = active.today.plusWeeks((ahead / perWeek).toLong())
            val day = Changes.day(program, position, date, active.changes) ?: return@mapNotNull null
            val written = program.plannedDay(position) ?: return@mapNotNull null
            // Trained later than its date, a day can fall past a date-ranged Change's end.
            val endsOn = active.changes.filter { it.change.operations.isNotEmpty() && it.covers(written, date) }
                .mapNotNull { (it.change.scope as? Scope.Dates)?.to }.minOrNull()
            PlanDay.Ahead(day, ahead == 0, date, endsOn)
        }
    }
}
