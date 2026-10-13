package com.hyperlog.app.progression

import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.program.PlannedSet
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.backoffWeight
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One main set as logged, with the prescription it was done under (null when done ad hoc). Only
 * its weight, full reps and RIR count: partials, drops and mini-sets never move a suggestion.
 */
data class WorkSet(
    val set: LoggedSet,
    val target: PlannedSet?,
    /** False for a weight kept past a weight check that nothing logged since has confirmed. */
    val counts: Boolean = true,
)

/**
 * One session of an exercise (on one machine, for machines): its main sets in order. For a
 * weighted bodyweight exercise [bodyweightKg] is the body's weight then, which the estimate adds to
 * the weight logged on the belt (SPEC, Bodyweight); 0 for everything else.
 */
data class Performance(val sets: List<WorkSet>, val deload: Boolean = false, val bodyweightKg: Double = 0.0)

/** The weight and target reps for the next set, and why ([why] is null when it just follows the set before). */
data class Suggestion(val kg: Double, val reps: Int, val kind: Kind, val why: String?) {
    enum class Kind {
        /** One rung up: last time reached the top of the range at the target RIR. */
        Up,
        /** Same weight, more reps: in the range, or the next rung is too big a jump. */
        More,
        /** Same weight: one rep short, or short in a deload. */
        Hold,
        /** One rung (about 5%) down: two or more reps short. */
        Down,
        /** A new rep range: worked out from the estimate, to check on the first set. */
        Estimated,
        /** Progress isn't possible on this equipment within the range. */
        Stuck,
        /** A deload week: last time's weight at the easier target. */
        Deload,
        /** The set before's weight, adjusted for the gap between its target and logged RIR. */
        Adjust,
        /** The set before's weight. */
        Follow,
        /** A back-off from its top set. */
        Backoff,
        /** No history yet: the starting weight from the old app. */
        Starting,
    }

    val estimated: Boolean get() = kind == Kind.Estimated

    /**
     * This suggestion moved by an accepted Change's load adjustment of [percent], to the nearest
     * rung, saying so after the reason.
     */
    fun adjusted(percent: Int, ladder: WeightLadder): Suggestion {
        val target = kg * (100 + percent) / 100.0
        val moved = ladder.rungs[ladder.nearestIndex(target)]
        val note = "${abs(percent)}% ${if (percent < 0) "lighter" else "heavier"} for a Change"
        return copy(kg = moved, why = listOfNotNull(why, note).joinToString(" · "))
    }
}

/** An e1RM and the set it comes from. */
data class Estimate(val kg: Double, val from: LoggedSet)

/**
 * Automatic progressive overload (SPEC): the suggestion for the next set of an exercise, from its
 * history and what has been logged today. Pure: the ladder is the only equipment it knows.
 */
object Progression {
    /** Sets with more full reps than this don't feed the estimate: RIR is unreliable there. */
    const val MAX_ESTIMATE_REPS = 12
    /** Sets this far from failure can raise the estimate but never lower it. */
    const val HIGH_RIR = 4
    /** Epley's constant, before the per-exercise correction. */
    const val EPLEY = 30.0
    /** How far past the top of the range "same weight, +1 rep" goes before progress is called impossible. */
    const val EXTRA_REPS = 2

    /**
     * The suggestion for set `today.sets.size` of [plan]. [history] is this exercise's past
     * sessions (on this machine), oldest first; [today] holds the sets logged so far and whether
     * this is a deload week. Null when there's nothing to go on.
     */
    fun suggest(plan: List<PlannedSet>, today: Performance, history: List<Performance>, ladder: WeightLadder): Suggestion? {
        val index = today.sets.size
        val target = plan.getOrNull(index) ?: return null
        target.backoff?.let { b ->
            val top = today.sets.getOrNull(b.from)?.set?.weightKg ?: return null
            return Suggestion(backoffWeight(top, b.percent, ladder), followReps(today, target), Suggestion.Kind.Backoff, null)
        }
        val previous = today.sets.withIndex().lastOrNull { it.value.counts && it.value.target?.backoff == null }
        return if (previous != null) withinSession(previous.index, previous.value, target, today, history, ladder)
        else nextSession(target, today.deload, history, ladder, today.bodyweightKg)
    }

    /** The exercise's current estimate from [history] (oldest first), or null with no usable set. */
    fun estimate(history: List<Performance>): Estimate? = estimate(history, constant(history))

    private fun estimate(history: List<Performance>, k: Double): Estimate? {
        var estimate: Estimate? = null
        for (p in history) {
            fun best(sets: List<WorkSet>) = sets.maxByOrNull { e1rm(it.set, k, p.bodyweightKg) }?.let { Estimate(e1rm(it.set, k, p.bodyweightKg), it.set) }
            val sets = p.sets.filter { it.counts && it.set.fullReps in 1..MAX_ESTIMATE_REPS }
            val normal = if (p.deload) null else best(sets.filter { it.set.rir < HIGH_RIR })
            // Deload and high-RIR sets only ever raise it.
            val guarded = best(sets.filter { p.deload || it.set.rir >= HIGH_RIR })
            if (normal != null) estimate = normal
            if (guarded != null && guarded.kg > (estimate?.kg ?: 0.0)) estimate = guarded
        }
        return estimate
    }

    /**
     * Epley's constant for this exercise, learned from sessions with sets of different reps at
     * different weights (a top set and its back-offs): `w1 (1 + e1/K) = w2 (1 + e2/K)`. The median
     * of every such pair, kept within 20–40; 30 until there are two pairs.
     */
    fun constant(history: List<Performance>): Double {
        val ks = history.filter { !it.deload }.flatMap { p ->
            val sets = p.sets.filter { it.counts && it.set.fullReps in 1..MAX_ESTIMATE_REPS && it.set.rir < HIGH_RIR }.map { it.set }
            val bw = p.bodyweightKg
            sets.flatMap { a ->
                sets.mapNotNull { b ->
                    val ea = effective(a)
                    val eb = effective(b)
                    if (a.weightKg > b.weightKg + 0.001 && eb - ea >= 3) ((b.weightKg + bw) * eb - (a.weightKg + bw) * ea) / (a.weightKg - b.weightKg) else null
                }
            }
        }.filter { it > 0 }.sorted()
        if (ks.size < 2) return EPLEY
        val median = if (ks.size % 2 == 1) ks[ks.size / 2] else (ks[ks.size / 2 - 1] + ks[ks.size / 2]) / 2
        return median.coerceIn(20.0, 40.0)
    }

    /** [bw]: today's bodyweight for a weighted bodyweight exercise, else 0; the ladder is the load on the belt. */
    private fun nextSession(target: PlannedSet, deload: Boolean, history: List<Performance>, ladder: WeightLadder, bw: Double): Suggestion? {
        val k = constant(history)
        val estimate = estimate(history, k)
        // Judged against the last session with this rep range (a day with its own range progresses
        // on its own); one done ad hoc is judged against today's prescription.
        fun topSets(p: Performance): List<WorkSet> {
            val judged = p.sets.filter { it.counts && it.target?.backoff == null }.ifEmpty { return emptyList() }
            val top = judged.maxOf { it.set.weightKg }
            return judged.filter { abs(it.set.weightKg - top) < 0.05 }
        }
        val last = history.lastOrNull { p -> topSets(p).firstOrNull()?.let { (it.target?.reps ?: target.reps) == target.reps } == true }
            ?: return estimate?.let { fromEstimate(it, k, target, ladder, "Estimated from ${it.from.text()}, check on the first set", bw) }
        val atTop = topSets(last)
        val top = atTop.first().set.weightKg
        val was = atTop.first().target ?: target
        val range = was.reps
        val sets = atTop.map { it.set }
        val lastly = "Last time ${sets.text()}"
        if (deload) {
            return Suggestion(top, predicted(estimate, k, top + bw, target.rir, range), Suggestion.Kind.Deload, "Deload week: last time's weight, ${target.rir ?: 0} in reserve")
        }
        val t = was.rir
        fun eff(s: LoggedSet) = s.fullReps + (if (t != null) s.rir else 0)
        val worst = sets.minOf(::eff)
        val topEff = range.max + (t ?: 0)
        val bottomEff = range.min + (t ?: 0)
        val fixed = range.min == range.max
        val fewest = sets.minOf { it.fullReps }
        if (worst >= topEff + (if (fixed) 1 else 0)) {
            val reached = if (fixed) "beat $range" else "top of $range"
            val up = ladder.step(top, 1)
            if (up <= top + 0.001) {
                return Suggestion(top, fewest, Suggestion.Kind.Stuck, "$lastly, $reached, and ${formatKg(top)} kg is the heaviest weight here: progress isn't possible")
            }
            // Judged on last time's sets themselves, however many reps they had.
            val reps = predictedRaw((top + last.bodyweightKg) * (1 + sets.minOf(::effective) / k), k, up + bw, t)
            if (reps >= range.min) {
                return Suggestion(up, reps.coerceIn(range.min, range.max), Suggestion.Kind.Up, "$lastly, $reached → ${formatKg(up)} kg")
            }
            val aim = fewest + 1
            return if (aim > range.max + EXTRA_REPS) {
                Suggestion(top, fewest, Suggestion.Kind.Stuck, "$lastly, past $range, and ${formatKg(up)} kg is too big a jump: progress isn't possible here")
            } else {
                Suggestion(top, aim, Suggestion.Kind.More, "$lastly, $reached; ${formatKg(up)} kg is too big a jump → $aim reps")
            }
        }
        val short = bottomEff - worst
        if (short >= 2 && !last.deload) {
            val below = ladder.rungs.filter { it < top - 0.001 }
            if (below.isNotEmpty()) {
                // One rung down, or about 5% where the ladder is fine-grained.
                val down = below.minBy { abs(it + bw - (top + bw) * 0.95) }
                return Suggestion(down, predicted(estimate, k, down + bw, t, range), Suggestion.Kind.Down, "$lastly, $short short of $range → ${formatKg(down)} kg")
            }
        }
        if (short >= 1) {
            val why = if (last.deload && short >= 2) "$lastly in a deload: same weight" else "$lastly, ${if (short == 1) "one" else "$short"} short of $range: same weight"
            return Suggestion(top, range.min, Suggestion.Kind.Hold, why)
        }
        val aim = (fewest + 1).coerceIn(range.min, range.max)
        val why = if (fixed) "$lastly, hit $range: beat it by a rep to move up" else "$lastly, in $range → $aim reps"
        return Suggestion(top, aim, Suggestion.Kind.More, why)
    }

    private fun withinSession(
        previousIndex: Int,
        previous: WorkSet,
        target: PlannedSet,
        today: Performance,
        history: List<Performance>,
        ladder: WeightLadder,
    ): Suggestion {
        val set = previous.set
        val bw = today.bodyweightKg
        val was = previous.target ?: target
        val n = previousIndex + 1
        if (was.reps != target.reps) {
            val all = history + today
            val k = constant(all)
            estimate(all, k)?.let { return fromEstimate(it, k, target, ladder, "Estimated from ${it.from.text()}, check this set", bw) }
        }
        val follow = Suggestion(set.weightKg, followReps(today, target), Suggestion.Kind.Follow, null)
        val t = was.rir ?: return follow
        val range = target.reps
        // About 5% per point of effective reps off the target, capped at ±10%.
        val gap = set.fullReps + set.rir - (set.fullReps.coerceIn(range.min, range.max) + t)
        if (gap == 0) return follow
        val kg = ladder.rungs[ladder.nearestIndex((set.weightKg + bw) * (1 + (0.05 * gap).coerceIn(-0.10, 0.10)) - bw)]
        if (abs(kg - set.weightKg) < 0.05) return follow
        val reason = when {
            set.fullReps < range.min -> "below $range"
            set.fullReps > range.max -> "above $range"
            else -> "target @ $t"
        }
        return Suggestion(kg, followReps(today, target).coerceIn(range.min, range.max), Suggestion.Kind.Adjust, "Set $n was ${set.text()}, $reason → ${formatKg(kg)} kg")
    }

    private fun fromEstimate(estimate: Estimate, k: Double, target: PlannedSet, ladder: WeightLadder, why: String, bw: Double): Suggestion {
        val t = target.rir ?: 0
        val ideal = estimate.kg / (1 + (target.reps.min + t) / k) - bw
        // Rounded down: RIR is usually under-reported, so the true capacity is a little higher.
        val kg = ladder.rungs.lastOrNull { it <= ideal + 0.001 } ?: ladder.rungs.first()
        return Suggestion(kg, predicted(estimate, k, kg + bw, target.rir, target.reps), Suggestion.Kind.Estimated, why)
    }

    /** Reps at [kg] for [rir] in reserve, as the estimate predicts, kept inside [range]. */
    private fun predicted(estimate: Estimate?, k: Double, kg: Double, rir: Int?, range: RepRange): Int =
        (estimate?.let { predictedRaw(it.kg, k, kg, rir) } ?: range.min).coerceIn(range.min, range.max)

    private fun predictedRaw(e1rm: Double, k: Double, kg: Double, rir: Int?): Int =
        (k * (e1rm / kg - 1) - (rir ?: 0)).roundToInt()

    /** The set before's reps when it had the same range, capped at its top; else the top of the range. */
    private fun followReps(today: Performance, target: PlannedSet): Int {
        val previous = today.sets.lastOrNull() ?: return target.reps.max
        return if (previous.target?.reps == target.reps) previous.set.fullReps.coerceAtMost(target.reps.max) else target.reps.max
    }

    private fun effective(s: LoggedSet) = s.fullReps + s.rir

    private fun e1rm(s: LoggedSet, k: Double, bw: Double) = (s.weightKg + bw) * (1 + effective(s) / k)

    /** `41.5 kg × 10 @ 2`. */
    private fun LoggedSet.text() = "${formatKg(weightKg)} kg × $fullReps @ $rir"

    /** `41.5 kg × 10, 10 @ 2`, or `41.5 kg × 10 @ 2, 9 @ 1` when the RIRs differ. Sets share a weight. */
    private fun List<LoggedSet>.text(): String {
        val kg = "${formatKg(first().weightKg)} kg × "
        return if (all { it.rir == first().rir }) kg + joinToString(", ") { "${it.fullReps}" } + " @ ${first().rir}"
        else kg + joinToString(", ") { "${it.fullReps} @ ${it.rir}" }
    }
}
