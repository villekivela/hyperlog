package com.hyperlog.app.domain

import kotlin.math.abs

/** The weights an equipment item can actually be set to, in kg, lightest first. */
class WeightLadder(val rungs: List<Double>) {
    init {
        require(rungs.isNotEmpty()) { "A weight ladder needs at least one rung" }
    }

    /** Index of the rung closest to [kg]. */
    fun nearestIndex(kg: Double): Int = rungs.indices.minBy { abs(rungs[it] - kg) }

    /**
     * Moves [by] rungs from [kg]. A weight between rungs first snaps to the rung in the direction
     * of travel, so − from 61 gives 60, not 57.5.
     */
    fun step(kg: Double, by: Int): Double {
        val nearest = nearestIndex(kg)
        val onRung = abs(rungs[nearest] - kg) < 0.05
        val from = when {
            onRung -> nearest
            by < 0 -> rungs.indexOfLast { it < kg }.let { if (it < 0) 0 else it + 1 }
            else -> rungs.indexOfFirst { it > kg }.let { if (it < 0) rungs.lastIndex else it - 1 }
        }
        return rungs[(from + by).coerceIn(rungs.indices)]
    }

    /** Up to [count] rungs centred on the one nearest [kg], for the pad's weight strip. */
    fun around(kg: Double, count: Int = 5): List<Double> {
        val half = count / 2
        val first = (nearestIndex(kg) - half).coerceIn(0, (rungs.size - count).coerceAtLeast(0))
        return rungs.subList(first, (first + count).coerceAtMost(rungs.size))
    }

    /** `1.1 · 3.4 · 5.7 · 7.9 … 89.6 kg`: the first rungs and the top, for lists and setup. */
    fun preview(first: Int = 4): String {
        val shown = if (rungs.size <= first + 1) rungs.joinToString(" · ") { formatKg(it) }
        else rungs.take(first).joinToString(" · ") { formatKg(it) } + " … " + formatKg(rungs.last())
        return "$shown kg"
    }

    companion object {
        fun steps(start: Double, step: Double, count: Int) =
            WeightLadder(List(count) { round1(start + it * step) })

        /**
         * 2.5 kg steps, for equipment the session doesn't know yet (machines and cables until #5
         * picks the machine).
         */
        val Default = steps(start = 2.5, step = 2.5, count = 120)

        private fun round1(x: Double) = Math.round(x * 10) / 10.0
    }
}
