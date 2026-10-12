package com.hyperlog.app.program

import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.progression.Suggestion

/** A back-off set's weight: [topKg] minus [percent], rounded down to a rung (the lightest if none is below). */
fun backoffWeight(topKg: Double, percent: Int, ladder: WeightLadder): Double {
    val target = topKg * (100 - percent) / 100.0
    return ladder.rungs.lastOrNull { it <= target + 0.001 } ?: ladder.rungs.first()
}

/**
 * A drop set's drop weights (SPEC, Intensity techniques): each drop is the weight before it minus
 * the technique's percent, rounded down to a rung (the lightest if none is below).
 */
fun dropWeights(setKg: Double, technique: Technique.Dropset, ladder: WeightLadder): List<Double> {
    var kg = setKg
    return List(technique.drops) { backoffWeight(kg, technique.percent, ladder).also { kg = it } }
}

/**
 * The pad's entry for the next set of [plan] after the [logged] ones: [suggestion]'s weight and
 * reps (progression works them out from history and the sets before), and the target's RIR, else
 * the set before's. With no suggestion (no history yet) the weight is empty, to type first, and
 * the reps are the top of the range.
 */
fun plannedEntry(plan: List<PlannedSet>, logged: List<LoggedSet>, suggestion: Suggestion?): SetEntry {
    val set = plan.getOrNull(logged.size) ?: plan.last()
    val previous = logged.lastOrNull()
    val kg = suggestion?.kg
    return SetEntry(
        weight = kg?.let(::formatKg).orEmpty(),
        full = (suggestion?.reps ?: set.reps.max).toString(),
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
