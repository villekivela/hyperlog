package com.hyperlog.app.progression

import kotlin.math.abs

/** What a record set beat (SPEC, Records). */
enum class Record {
    /** Its estimate is above every earlier set's. */
    Estimate,
    /** More full reps than ever at a weight lifted before. */
    Reps,
}

/** Records (SPEC, Records), worked out from the logged sets and never stored. */
object Records {
    /**
     * The record each of [today]'s sets makes, or null, against [history] (the exercise's earlier
     * sessions, on the same machine for machines, oldest first) and today's sets before it.
     */
    fun of(history: List<Performance>, today: Performance): List<Record?> {
        // The first session has nothing to beat, and a deload is easier on purpose.
        if (history.none { it.sets.isNotEmpty() } || today.deload) return today.sets.map { null }
        // The constant the session started with, so a later set never takes an earlier one's mark away.
        val k = Progression.constant(history)
        var best = history.flatMap { p -> p.sets.mapNotNull { Progression.estimateOf(it, p, k) } }.maxOrNull()
        val earlier = history.flatMap { it.sets }.toMutableList()
        return today.sets.map { set ->
            val estimate = Progression.estimateOf(set, today, k)
            val beaten = best
            val atWeight = earlier.filter { abs(it.set.weightKg - set.set.weightKg) < 0.05 }
            val record = when {
                estimate != null && beaten != null && estimate > beaten + SAME_KG -> Record.Estimate
                set.counts && atWeight.isNotEmpty() && set.set.fullReps > atWeight.maxOf { it.set.fullReps } -> Record.Reps
                else -> null
            }
            if (estimate != null) best = maxOf(beaten ?: estimate, estimate)
            earlier += set
            record
        }
    }

    /** Estimates this close are the same: rounding never makes a record. */
    private const val SAME_KG = 0.001
}
