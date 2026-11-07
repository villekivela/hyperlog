package com.hyperlog.app.history

import com.hyperlog.app.coach.CoachLines
import java.time.LocalDate
import kotlin.math.abs

/**
 * How a lift has moved, as the exercise sheet's lift trend says it (GLOSSARY, Lift trend). It
 * measures nothing of its own: Progress's Moving (the estimate over about 4 weeks, moved 1 % or
 * more) and the coach line's stall (no higher in 3 or more sessions), so the numbers agree.
 */
sealed interface LiftTrend {
    /** The estimate went [fromKg] to [toKg] over about 4 weeks, as Progress's Moving has it. */
    data class Moved(val fromKg: Double, val toKg: Double) : LiftTrend {
        val percent: Double get() = (toKg - fromKg) / fromKg * 100
    }

    /** Under 1 % either way over about 4 weeks, at [kg]. */
    data class Steady(val kg: Double) : LiftTrend

    /** No higher in its last [sessions] counted sessions, as the coach line counts them. */
    data class Stalled(val sessions: Int) : LiftTrend

    companion object {
        /**
         * From one line's [points] (on one machine, for machines: ADR 0002), leaving out the
         * [eased] sessions (a deload or an eased day), as the coach line does. Under 2 sessions
         * left, or a latest one from before the window, there is none. A lift that moved says so,
         * as Progress does, before a stall.
         */
        fun of(points: List<TrendPoint>, eased: Set<Long>, today: LocalDate): LiftTrend? {
            val counted = points.filter { it.estimateKg != null && it.sessionId !in eased }
            if (counted.size < 2) return null
            val moved = movedOver(counted, today)
            if (moved != null && abs((moved.second - moved.first) / moved.first * 100) >= MovingPercent) return Moved(moved.first, moved.second)
            CoachLines.stallRun(counted.map { it.estimateKg!! })?.let { return Stalled(it) }
            return moved?.let { Steady(it.second) }
        }
    }
}
