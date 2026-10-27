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
import com.hyperlog.app.ui.today.date
import com.hyperlog.app.week.Rotation
import com.hyperlog.app.week.Turn
import java.time.LocalDate

/** A day of a week as Program shows it, with the day as written or as it will be trained. */
sealed interface ProgramDay {
    val day: PlannedDay
    val position: Position get() = day.position

    /** Passed without a session, skipped or missed: its plan is there to read, not to train. */
    val passed: Boolean get() = this is Skipped || this is Missed

    /**
     * What tells this day from the others in its list: a week trained again holds the same day
     * twice, missed and to come, so a dated day goes by its date and a trained one by its session.
     */
    val key: String get() = when (this) {
        is Done -> "s${session.id}"
        is Missed -> "m$date"
        is Ahead -> date?.let { "a$it" } ?: "p${position.week}-${position.day}"
        is Skipped -> "p${position.week}-${position.day}"
    }

    /**
     * A day still to train, as the session will prescribe it ([Changes.dayAhead]). [endsOn] is the
     * end of a date-ranged Change on it, which the day may fall after.
     */
    data class Ahead(override val day: PlannedDay, val next: Boolean, val endsOn: LocalDate?, val date: LocalDate? = null) : ProgramDay

    /** A day trained: the session logged for it. */
    data class Done(override val day: PlannedDay, val session: PastSessionItem) : ProgramDay

    /** A day passed without a session, by a skip: what it prescribed, marked skipped. */
    data class Skipped(override val day: PlannedDay) : ProgramDay

    /** A training day passed without a session, on [date]: its day used up (ADR 0006). */
    data class Missed(override val day: PlannedDay, val date: LocalDate) : ProgramDay
}

/**
 * What [previous]'s last week becomes in a block's first week: the block-wide [rule] before → after,
 * and each day's slots written differently.
 */
data class BlockComparison(val previous: Block, val days: List<DayComparison>, val rule: Pair<BlockRule, BlockRule> = BlockRule() to BlockRule())

/** A block's override for a week, every set of it: [WeekOverride] without its weeks. */
data class BlockRule(val rir: Int? = null, val rirAdd: Int = 0, val noTechniques: Boolean = false, val fewerSets: Int = 0, val deload: Boolean = false)

data class DayComparison(val day: Day, val slots: List<SlotDiff>)

/**
 * The weeks as Program reads them (docs/design/program-and-today.md): a week's days, each resolved
 * through [Changes] as Today and the session resolve it, and a block against the one before.
 */
object ProgramWeek {
    /**
     * [block] against the block before it: the previous block's last week beside this block's
     * first. A block-wide override is named once, as [BlockComparison.rule]; slots are compared as
     * written without it, day by day (Day A with Day A) and slot by slot. Null for the first block.
     */
    fun comparison(program: Program, block: Block): BlockComparison? {
        val previous = program.blocks.getOrNull(program.blocks.indexOf(block) - 1) ?: return null
        val written = program.copy(blocks = program.blocks.map { it.copy(weeks = emptyList()) })
        val days = block.days.indices.mapNotNull { i ->
            val after = written.plannedDay(Position(block.weekStart, i)) ?: return@mapNotNull null
            val before = written.plannedDay(Position(previous.weekEnd, i))?.slots.orEmpty()
            val slots = after.slots.mapNotNull { slot ->
                val was = before.firstOrNull { it.id == slot.id }
                if (was != null && was.exercise == slot.exercise && was.sets == slot.sets) null else SlotDiff(was, slot)
            } + before.filter { was -> after.slots.none { it.id == was.id } }.map { SlotDiff(it, null) }
            slots.takeIf { it.isNotEmpty() }?.let { DayComparison(after.day, it) }
        }
        return BlockComparison(previous, days, previous.rule(previous.weekEnd) to block.rule(block.weekStart))
    }

    private fun Block.rule(week: Int): BlockRule =
        weeks.firstOrNull { week in it.weeks }?.let { BlockRule(it.rir, it.rirAdd, it.noTechniques, it.fewerSets, it.deload) } ?: BlockRule()

    /**
     * The days of [week]: those before the position done (with the latest of [sessions] for each)
     * or skipped, the rest still to train, without dates.
     */
    fun days(active: ActiveProgram, week: Int, sessions: List<PastSessionItem>): List<ProgramDay> {
        val program = active.program
        val block = program.blockAt(week) ?: return emptyList()
        val next = program.ordinal(active.position)
        // The latest session of each day of this program; a repeated week trains its days again.
        val ofProgram = sessions.filter { it.programId == active.id && it.position != null }.sortedBy { it.startedAt }
        active.rotation?.let { rotation -> rotated(active, rotation, week, ofProgram)?.let { return it } }
        val trained = ofProgram.associateBy { it.position!! }
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

    /**
     * With training days, the days [week]'s dates hold, by date: those trained, those missed and
     * those to come, each read for its date. Null for a week the rotation hasn't reached, before it
     * started, which reads as a sequence.
     */
    private fun rotated(active: ActiveProgram, rotation: Rotation, week: Int, sessions: List<PastSessionItem>): List<ProgramDay>? {
        val missed = rotation.missed.filter { it.position.week == week }
        val planned = rotation.planned.filter { it.position.week == week }
        // Every session of the week: with more training days than days, a day comes round twice.
        val done = sessions.filter { it.position!!.week == week }
        if (missed.isEmpty() && planned.isEmpty() && done.none { it.date() >= rotation.since }) return null
        val first = rotation.planned.firstOrNull()
        val dated = done.mapNotNull { s -> s.date().let { date -> active.dayOf(Turn(date, s.position!!))?.let { date to ProgramDay.Done(it, s) } } } +
            missed.mapNotNull { t -> active.dayOf(t)?.let { t.date to ProgramDay.Missed(it, t.date) } } +
            planned.mapNotNull { t -> active.dayOf(t)?.let { t.date to ProgramDay.Ahead(it, t == first, null, t.date) } }
        return dated.sortedBy { it.first }.map { it.second }
    }

}
