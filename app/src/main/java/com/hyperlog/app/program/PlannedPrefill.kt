package com.hyperlog.app.program

import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.domain.formatKg

/** A back-off set's weight: [topKg] minus [percent], rounded down to a rung (the lightest if none is below). */
fun backoffWeight(topKg: Double, percent: Int, ladder: WeightLadder): Double {
    val target = topKg * (100 - percent) / 100.0
    return ladder.rungs.lastOrNull { it <= target + 0.001 } ?: ladder.rungs.first()
}

/**
 * The pad's entry for the next set of [plan] after the [logged] ones. Prefills follow what just
 * happened (docs/design/logging-screen.md):
 * - the weight is the set before's, a back-off's is worked out from its top set, and the first set
 *   starts from last time's first set (empty with no history);
 * - reps are the set before's when it had the same range, capped at its top, else the top of the
 *   range;
 * - the RIR is the target's, else the set before's.
 */
fun plannedEntry(plan: List<PlannedSet>, logged: List<LoggedSet>, lastTime: List<LoggedSet>, ladder: WeightLadder): SetEntry {
    val i = logged.size
    val set = plan.getOrNull(i) ?: plan.last()
    val previous = logged.lastOrNull()
    val kg = plannedWeight(i, plan, logged, lastTime, ladder, activeKg = null)
    val reps = if (previous != null && plan.getOrNull(i - 1)?.reps == set.reps) previous.fullReps.coerceAtMost(set.reps.max) else set.reps.max
    return SetEntry(
        weight = kg?.let(::formatKg).orEmpty(),
        full = reps.toString(),
        partial = "",
        rir = set.rir ?: previous?.rir,
        field = if (kg == null) Field.Weight else Field.Reps,
        partialMode = false,
        fresh = true,
    )
}

/**
 * The weight set [index] of [plan] shows: logged, being typed ([activeKg], for the set after the
 * logged ones), or planned from the sets before it. Null when there is nothing to go on yet.
 */
fun plannedWeight(
    index: Int,
    plan: List<PlannedSet>,
    logged: List<LoggedSet>,
    lastTime: List<LoggedSet>,
    ladder: WeightLadder,
    activeKg: Double?,
): Double? {
    logged.getOrNull(index)?.let { return it.weightKg }
    if (index == logged.size && activeKg != null) return activeKg
    val backoff = plan.getOrNull(index)?.backoff
    return when {
        backoff != null -> plannedWeight(backoff.from, plan, logged, lastTime, ladder, activeKg)?.let { backoffWeight(it, backoff.percent, ladder) }
        index > 0 -> plannedWeight(index - 1, plan, logged, lastTime, ladder, activeKg)
        else -> lastTime.firstOrNull()?.weightKg
    }
}
