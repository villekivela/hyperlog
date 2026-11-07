package com.hyperlog.app.coach

import com.hyperlog.app.history.addSets
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.MuscleGroup
import com.hyperlog.app.library.Muscles
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program

/** Where a muscle's weekly sets stand against its range. */
enum class VolumeStatus { Under, Within, Over }

/** Where [sets] stand against this range. */
fun NumberRange.statusOf(sets: Double): VolumeStatus = when {
    sets < low -> VolumeStatus.Under
    sets > high -> VolumeStatus.Over
    else -> VolumeStatus.Within
}

/** One muscle's weekly [sets] against its [range], a [focused] muscle's the raised one. */
data class MuscleVolume(val muscle: String, val sets: Double, val range: NumberRange, val focused: Boolean, val status: VolumeStatus)

/** A Change's sets per muscle once it applies ([rows]), and each muscle's sets [before] it, in [week]. */
data class ChangeVolume(val rows: List<MuscleVolume>, val before: Map<String, Double>, val week: Int)

/**
 * A program's hard sets a week per muscle against the training style (SPEC, Coaching → New
 * program → Sets per muscle), counted as History counts them.
 */
object ProgramVolume {
    /**
     * The muscles a program exercise has as a primary muscle, in body order, with their sets in the
     * program's first week that isn't eased or a deload (the first week when every one is), against
     * [standing]'s range, else the program's own. A muscle of a focused group is measured against the
     * raised range; with a focus the others are measured against maintenance.
     */
    fun of(program: Program, exercises: Map<String, Exercise>, standing: NumberRange): List<MuscleVolume> {
        val week = (1..program.weeks).firstOrNull { w -> days(program, w).none { it.eased || it.deload } } ?: 1
        return of(days(program, week), program, exercises, standing)
    }

    /** As [of], for the [days] of one week, which [program] frames (its focus and its volume). */
    fun of(days: List<PlannedDay>, program: Program, exercises: Map<String, Exercise>, standing: NumberRange): List<MuscleVolume> {
        val sets = mutableMapOf<String, Double>()
        val listed = mutableSetOf<String>()
        for (slot in days.flatMap { it.slots }) {
            val exercise = exercises[slot.exercise] ?: continue
            listed += exercise.muscles
            exercise.addSets(slot.setsFor(slot.exercise).size, sets)
        }
        return Muscles.filter { it in listed }.map { muscle ->
            val focused = MuscleGroup.of(muscle) in program.focus
            val measured = range(program, muscle, standing)
            val n = sets.getValue(muscle)
            MuscleVolume(muscle, n, measured, focused, measured.statusOf(n))
        }
    }

    /** The range [muscle] is measured against: the raised one in a focused group, maintenance beside a focus. */
    fun range(program: Program?, muscle: String, standing: NumberRange): NumberRange {
        val range = program?.targets?.volume ?: standing
        val focus = program?.focus.orEmpty()
        return when {
            MuscleGroup.of(muscle) in focus -> range.raised()
            focus.isNotEmpty() -> range.maintenance()
            else -> range
        }
    }

    /**
     * A [proposed] Change's sets per muscle in the plan once it applies, with the sets before it
     * ([ChangeVolume.before]); null when it changes no muscle's sets, or the program has finished.
     */
    fun change(active: ActiveProgram, proposed: Change, exercises: Map<String, Exercise>, standing: NumberRange): ChangeVolume? {
        val (week, was, now) = active.weekWith(proposed) ?: return null
        val before = of(was, active.program, exercises, standing)
        val after = of(now, active.program, exercises, standing)
        // A muscle the Change takes out of the plan altogether drops to 0 sets.
        val gone = before.filter { b -> after.none { it.muscle == b.muscle } }.map { it.copy(sets = 0.0, status = VolumeStatus.Under) }
        val rows = (after + gone).sortedBy { Muscles.indexOf(it.muscle) }
        val sets = before.associate { it.muscle to it.sets }
        if (rows.all { sets[it.muscle] == it.sets }) return null
        return ChangeVolume(rows, sets, week)
    }

    private fun days(program: Program, week: Int): List<PlannedDay> {
        val block = program.blockAt(week) ?: return emptyList()
        return block.days.indices.mapNotNull { program.plannedDay(Position(week, it)) }
    }
}
