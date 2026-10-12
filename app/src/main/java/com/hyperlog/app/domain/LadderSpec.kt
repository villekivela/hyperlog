package com.hyperlog.app.domain

import java.util.Locale
import kotlin.math.roundToLong

enum class WeightUnit { Kg, Lb }

/**
 * How an equipment item's weight ladder is described (SPEC, Gyms and equipment): a start and step
 * in kg or lb, an explicit list, or a base weight plus plates. [ladder] turns it into rungs in kg.
 */
sealed interface LadderSpec {
    fun ladder(): WeightLadder

    /** For storage; [decode] reads it back. */
    fun encode(): String

    /** A stack: [count] rungs from [start], [step] apart, in [unit]. Lb rungs show to 0.1 kg. */
    data class Steps(val start: Double, val step: Double, val unit: WeightUnit, val count: Int) : LadderSpec {
        override fun ladder() = WeightLadder(
            List(count.coerceAtLeast(1)) {
                val w = start + it * step
                if (unit == WeightUnit.Lb) round(w * KG_PER_LB, 10) else round(w, 100)
            }.distinct(),
        )

        override fun encode() = "steps;${unit.name};${num(start)};${num(step)};$count"
    }

    data class Explicit(val kgs: List<Double>) : LadderSpec {
        override fun ladder() = WeightLadder(kgs.filter { it > 0 }.distinct().sorted().ifEmpty { listOf(0.0) })
        override fun encode() = "list;" + kgs.joinToString(",") { num(it) }
    }

    /** A bar or plate-loaded machine: [baseKg] plus any of the [pairs], each loaded one per side. */
    data class Plates(val baseKg: Double, val pairs: List<Double>) : LadderSpec {
        override fun ladder(): WeightLadder {
            var sums = setOf(0.0)
            pairs.filter { it > 0 }.forEach { p -> sums = sums + sums.map { round(it + 2 * p, 100) } }
            return WeightLadder(sums.map { round(baseKg + it, 100) }.sorted())
        }

        override fun encode() = "plates;${num(baseKg)};" + pairs.joinToString(",") { num(it) }

        /**
         * The plate calculator: the plates on each side for [totalKg], heaviest first and as few
         * as the pairs allow. Empty for the bar (or sled) alone; null when these plates can't
         * make the weight.
         */
        fun perSide(totalKg: Double): List<Double>? {
            val side = round((totalKg - baseKg) / 2, 100)
            if (side < 0) return null
            val plates = pairs.filter { it > 0 }.sortedDescending()
            var best: List<Double>? = null
            // Heaviest first, so among loadings with as few plates the first found is the heaviest.
            fun search(from: Int, left: Double, chosen: List<Double>) {
                if (best != null && chosen.size >= best!!.size) return
                if (left < 0.005) { best = chosen; return }
                for (i in from until plates.size) {
                    if (i > from && plates[i] == plates[i - 1]) continue
                    if (plates[i] <= left + 0.005) search(i + 1, round(left - plates[i], 100), chosen + plates[i])
                }
            }
            search(0, side, emptyList())
            return best
        }
    }

    companion object {
        const val KG_PER_LB = 0.45359237

        /** The one-tap preset for the usual lb cable stack: 2.5 lb, then 5 lb steps. */
        fun lbStack(count: Int = 40) = Steps(start = 2.5, step = 5.0, unit = WeightUnit.Lb, count = count)

        /** SPEC default: 2 kg steps to 20 kg, then 22.5 and 25, then 5 kg steps (to 60). */
        val DefaultDumbbells = Explicit(
            (2..20 step 2).map { it.toDouble() } + listOf(22.5, 25.0) + (30..60 step 5).map { it.toDouble() },
        )

        /** One shared set of barbell plates, as pairs on a 20 kg bar. */
        val DefaultPlates = Plates(20.0, listOf(20.0, 20.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25))

        fun decode(text: String): LadderSpec? = runCatching {
            val parts = text.split(";")
            fun list(s: String) = if (s.isBlank()) emptyList() else s.split(",").map { it.toDouble() }
            when (parts[0]) {
                "steps" -> Steps(parts[2].toDouble(), parts[3].toDouble(), WeightUnit.valueOf(parts[1]), parts[4].toInt())
                "list" -> Explicit(list(parts[1]))
                "plates" -> Plates(parts[1].toDouble(), list(parts[2]))
                else -> null
            }
        }.getOrNull()

        private fun round(x: Double, per: Int) = (x * per).roundToLong().toDouble() / per
        private fun num(x: Double) = String.format(Locale.ROOT, "%s", x)
    }
}
