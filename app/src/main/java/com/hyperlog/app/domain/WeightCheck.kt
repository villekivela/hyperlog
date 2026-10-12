package com.hyperlog.app.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/** The weight a new set is compared with: the previous set, or last time's first set. */
data class Reference(val kg: Double, val fromThisSession: Boolean)

/**
 * Why a weight about to be logged looks wrong, and three real weights to pick instead (SPEC,
 * Weight check). The first ✓ shows it; a second ✓ keeps the weight.
 */
data class WeightCheck(val reason: String, val options: List<Double>) {
    companion object {
        /**
         * Checks [kg] against [reference] (1.5× or more, or half or less) and, when [ladderKnown],
         * against [ladder]. A ladder that is only a stand-in for the real equipment isn't checked.
         */
        fun of(kg: Double, reference: Reference?, ladder: WeightLadder, ladderKnown: Boolean): WeightCheck? {
            if (reference != null && reference.kg > 0) {
                val ratio = kg / reference.kg
                val what = if (reference.fromThisSession) "last set" else "last time"
                val reason = when {
                    // 1.6×, 2.4×, then whole numbers: 10×, not 10.2×.
                    ratio >= 3 -> "${ratio.roundToInt()}× $what"
                    ratio >= 1.5 -> "${formatKg(Math.round(ratio * 10) / 10.0)}× $what"
                    ratio <= 0.5 -> "1/${(1 / ratio).roundToInt()} of $what"
                    else -> null
                }
                if (reason != null) return WeightCheck(reason, ladder.around(reference.kg, 3))
            }
            val onLadder = ladder.rungs.any { abs(it - kg) < 0.05 }
            if (ladderKnown && !onLadder) return WeightCheck("Not on this equipment", ladder.around(kg, 3))
            return null
        }
    }
}
