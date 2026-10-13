package com.hyperlog.app.coach

import com.hyperlog.app.program.Position

/**
 * The Program screen's actions as Changes (SPEC, Program): each is shown as a diff and applies
 * only once accepted, like any other Change. Their position moves run once; a deload covers the
 * rest of the program so it holds whenever the week is trained.
 */
object ProgramActions {
    fun skipDay(summary: String) = Change(summary, Scope.RestOfProgram, listOf(Operation.SkipDay()))

    fun repeatWeek(summary: String) = Change(summary, Scope.RestOfProgram, listOf(Operation.RepeatWeek()))

    fun deloadWeek(summary: String, week: Int) = Change(summary, Scope.RestOfProgram, listOf(Operation.DeloadWeek(week)))

    fun shift(summary: String, weeks: Int) = Change(summary, Scope.RestOfProgram, listOf(Operation.ShiftProgram(weeks)))

    /** The week Repeat week runs again from [position]: the one of the last day trained. */
    fun repeatedWeek(position: Position): Int = (if (position.day > 0) position.week else position.week - 1).coerceAtLeast(1)

    /** Shift program offers 1 to this many weeks later. */
    const val MAX_SHIFT_WEEKS = 8
}
