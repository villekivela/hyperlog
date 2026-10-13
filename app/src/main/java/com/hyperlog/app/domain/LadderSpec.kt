package com.hyperlog.app.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

enum class WeightUnit { Kg, Lb }

/**
 * How an equipment item's weight ladder is described (SPEC, Gyms and equipment): a start and step
 * in kg or lb, an explicit list, or a base weight plus plates. [ladder] turns it into rungs in kg.
 */
sealed interface LadderSpec {
    fun ladder(): WeightLadder

    /**
     * The ladder at a gym whose small [pinPlates] can hang on a stack's pin. Only a [PinStack]
     * takes them; every other kind is as [ladder] gives it.
     */
    fun ladder(pinPlates: List<Double>): WeightLadder = ladder()

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

    /**
     * A bar or plate-loaded machine: [baseKg] plus any of the [pairs], each loaded one per side.
     * A machine with no pairs of its own loads its gym's plates ([withPlates]).
     */
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

    /**
     * A selectorised stack (SPEC, Stacks with add-ons): the pin picks one of the stack's [plates]
     * (cumulative weights in [unit], lightest first, such as 1 to 10 by 1, then 12.5 to 45 by
     * 2.5). On top of the pin go any number of the machine's own [builtIn] add-ons (kg, one entry
     * per plate, so two 2.5 kg plates are `[2.5, 2.5]`) and, when it takes them ([pinPlates]), any
     * of the gym's small plates hung on the pin.
     *
     * The pin weights are the stack's printed labels: true weights rounded (or, [labelsCut], cut)
     * to [labelKg], so an lb stack labelled in whole kg reads `36 · 43 · 50`.
     */
    data class PinStack(
        val unit: WeightUnit,
        val plates: List<Double>,
        val labelKg: Double = if (unit == WeightUnit.Lb) 0.1 else 0.01,
        val labelsCut: Boolean = false,
        val builtIn: List<Double> = emptyList(),
        val pinPlates: Boolean = true,
    ) : LadderSpec {
        /** The stack as runs of even steps, for setup and its summary. */
        val segments: List<Segment> get() = segmentsOf(plates)

        /** A lighter first plate before the steps (2.5, then 5, 10, 15); null when the steps start at once. */
        val first: Double? get() = segments.takeIf { it.size > 1 && it.first().single }?.first()?.from

        /** Where the (first run of) even steps starts, and its step. */
        val start: Double get() = segments.firstOrNull { !it.single }?.from ?: plates.first()
        val step: Double get() = segments.firstOrNull { !it.single }?.step ?: 0.0
        val top: Double get() = plates.last()

        /** The pin's weights, lightest first, as their labels read in kg. */
        val pins: List<Double>
            get() = plates.map { label(if (unit == WeightUnit.Lb) it * KG_PER_LB else it) }.distinct()

        private fun label(kg: Double): Double {
            val n = kg / labelKg
            val whole = if (labelsCut) floor(n + 1e-6) else Math.round(n).toDouble()
            return round(whole * labelKg, 100)
        }

        override fun ladder(): WeightLadder = ladder(emptyList())

        override fun ladder(pinPlates: List<Double>): WeightLadder = WeightLadder(loads(pinPlates).map { it.kg }.ifEmpty { listOf(0.0) })

        /** How to load [kg] on this stack at a gym with [pinPlates]; null when it isn't a rung. */
        fun load(kg: Double, pinPlates: List<Double>): PinLoad? =
            loads(pinPlates).firstOrNull { abs(it.kg - kg) < 0.05 }

        /**
         * Every rung with its loading, lightest first. Rungs within 0.1 kg of each other are one
         * rung, loaded the handiest way: fewest extras, built-in add-ons before loose plates.
         */
        private fun loads(gymPlates: List<Double>): List<PinLoad> {
            val loose = if (pinPlates) gymPlates.filter { it > 0 } else emptyList()
            val extras = combinations(builtIn.filter { it > 0 }).flatMap { b -> combinations(loose).map { b to it } }
            val all = pins.flatMap { pin -> extras.map { (b, p) -> PinLoad(pin, b, p) } }
            val handiest = compareBy<PinLoad>({ it.builtIn.size + it.onPin.size }, { it.onPin.size }, { -it.pin })
            val kept = mutableListOf<PinLoad>()
            all.sortedBy { it.kg }.forEach { load ->
                val last = kept.lastOrNull()
                when {
                    last == null || load.kg - last.kg >= NEAR -> kept += load
                    handiest.compare(load, last) < 0 -> kept[kept.lastIndex] = load
                }
            }
            return kept
        }

        override fun encode() = listOf(
            "pin", unit.name, plates.joinToString(",") { num(it) }, num(labelKg),
            if (labelsCut) "cut" else "round", builtIn.joinToString(",") { num(it) }, if (pinPlates) "1" else "0",
        ).joinToString(";")

        companion object {
            /** A stack of even steps from [start] to [top] (the plate at or below it), after an optional lighter [first] plate. */
            fun steps(
                unit: WeightUnit, start: Double, step: Double, top: Double, first: Double? = null,
                labelKg: Double = if (unit == WeightUnit.Lb) 0.1 else 0.01, labelsCut: Boolean = false,
                builtIn: List<Double> = emptyList(), pinPlates: Boolean = true,
            ): PinStack {
                val count = if (step > 0) (((top - start) / step) + 1e-6).toInt().coerceIn(0, MAX_PINS - 1) + 1 else 1
                val plates = listOfNotNull(first?.takeIf { it < start }) + List(count) { Math.round((start + it * step) * 1000) / 1000.0 }
                return PinStack(unit, plates, labelKg, labelsCut, builtIn, pinPlates)
            }
        }
    }

    /** A run of a stack: [from] to [to] in [step]s; a [single] plate has no step. */
    data class Segment(val from: Double, val to: Double, val step: Double) {
        val single: Boolean get() = step <= 0.0
        val count: Int get() = if (single) 1 else ((to - from) / step + 1e-6).toInt() + 1
    }

    companion object {
        const val KG_PER_LB = 0.45359237

        /** The one-tap preset for the usual lb cable stack: 2.5 lb, then 5 lb steps. */
        fun lbStack(count: Int = 40) = Steps(start = 2.5, step = 5.0, unit = WeightUnit.Lb, count = count)

        /** SPEC default dumbbell rack: 1 to 10 kg by 1, then 12.5 to 50 kg by 2.5. */
        val DefaultDumbbells = Explicit((1..10).map { it.toDouble() } + (0..15).map { 12.5 + it * 2.5 })

        /** The dumbbells every install had until 0.1.2: 2 kg steps to 20 kg, then 22.5 and 25, then 5 kg steps to 60. */
        val OldDefaultDumbbells = Explicit(
            (2..20 step 2).map { it.toDouble() } + listOf(22.5, 25.0) + (30..60 step 5).map { it.toDouble() },
        )

        /**
         * A typical commercial gym's plates, as pairs (one entry per pair): 8 pairs of 20, then 2
         * pairs each of 15, 10, 5, 2.5 and 1.25 kg (SPEC, Gyms and equipment). A gym has these until
         * set; a session with no gym loads them too.
         */
        val DefaultGymPlates: List<Double> = List(8) { 20.0 } + listOf(15.0, 10.0, 5.0, 2.5, 1.25).flatMap { listOf(it, it) }

        /** The one shared set of plates every install had until 0.1.4: 20, 20, 20, 15, 10, 5, 2.5, 1.25. */
        val OldDefaultPairs = listOf(20.0, 20.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

        /** A 20 kg barbell with the typical plates. */
        val DefaultPlates = Plates(20.0, DefaultGymPlates)

        /** SPEC default for a selectorised machine: pin steps of 5 kg from 5 to 100, taking the gym's pin plates. */
        // Lazy: built through PinStack, whose own initialisation can be what started this one.
        val DefaultPinStack: PinStack by lazy { PinStack.steps(WeightUnit.Kg, start = 5.0, step = 5.0, top = 100.0) }

        /** The lb choice for a gym's machines: 2.5 lb, then 5 lb steps, 40 plates. */
        val LbPinStack: PinStack by lazy { PinStack.steps(WeightUnit.Lb, start = 2.5, step = 5.0, top = 197.5) }

        /** A gym's small plates for the pin until set: 1.25 and 2.5 kg. */
        val DefaultPinPlates = listOf(1.25, 2.5)

        /** The most pin positions a stack has. */
        const val MAX_PINS = 60

        /** Rungs closer than this are the same weight. */
        private const val NEAR = 0.15

        fun decode(text: String): LadderSpec? = runCatching {
            val parts = text.split(";")
            fun list(s: String) = if (s.isBlank()) emptyList() else s.split(",").map { it.toDouble() }
            when (parts[0]) {
                "steps" -> Steps(parts[2].toDouble(), parts[3].toDouble(), WeightUnit.valueOf(parts[1]), parts[4].toInt())
                "list" -> Explicit(list(parts[1]))
                "plates" -> Plates(parts[1].toDouble(), list(parts[2]))
                "pin" -> PinStack(
                    unit = WeightUnit.valueOf(parts[1]), plates = list(parts[2]).sorted().ifEmpty { return null },
                    labelKg = parts[3].toDouble(), labelsCut = parts[4] == "cut", builtIn = list(parts[5]), pinPlates = parts[6] == "1",
                )
                else -> null
            }
        }.getOrNull()

        /**
         * [plates] as runs of even steps. A plate where the step changes ends its run, except
         * that a run would be only two plates and the second starts the next run's step: then the
         * first stands alone (2.5, then 5, 10, 15 is a lighter first plate and steps of 5).
         */
        fun segmentsOf(plates: List<Double>): List<Segment> {
            val p = plates.sorted()
            val out = mutableListOf<Segment>()
            var i = 0
            fun near(a: Double, b: Double) = abs(a - b) < 1e-6
            while (i < p.size) {
                if (i == p.lastIndex) { out += Segment(p[i], p[i], 0.0); break }
                val step = p[i + 1] - p[i]
                var j = i + 1
                while (j + 1 < p.size && near(p[j + 1] - p[j], step)) j++
                if (j == i + 1 && i + 2 < p.size) {
                    out += Segment(p[i], p[i], 0.0); i += 1; continue
                }
                out += Segment(p[i], p[j], step)
                i = j + 1
            }
            return out
        }

        /** Every multiset drawn from [plates], each plate used at most once, the empty one first. */
        internal fun combinations(plates: List<Double>): List<List<Double>> {
            var out = listOf(emptyList<Double>())
            plates.sortedDescending().forEach { p -> out = out + out.map { it + p } }
            return out.distinctBy { it.sorted() }
        }

        private fun round(x: Double, per: Int) = (x * per).roundToLong().toDouble() / per
        private fun num(x: Double) = String.format(Locale.ROOT, "%s", x)
    }
}

/**
 * How a [LadderSpec.PinStack] rung is loaded: the [pin] (its label), the machine's own add-ons
 * used, and the gym's plates hung [onPin].
 */
data class PinLoad(val pin: Double, val builtIn: List<Double>, val onPin: List<Double>) {
    val kg: Double get() = Math.round((pin + builtIn.sum() + onPin.sum()) * 100) / 100.0

    /** `pin 36 + 2 add-ons + 2.5 on the pin`, the loading in words. */
    fun hint(): String = buildString {
        append("pin ").append(formatKg(pin))
        when (builtIn.size) {
            0 -> Unit
            1 -> append(" + add-on")
            else -> append(" + ${builtIn.size} add-ons")
        }
        if (onPin.isNotEmpty()) append(" + ").append(onPin.sortedDescending().joinToString(" + ") { formatKg(it) }).append(" on the pin")
    }
}

/** This ladder at a gym with [plates]: a plate-loaded machine with no pairs of its own loads them; anything else is as it is. */
fun LadderSpec.withPlates(plates: List<Double>): LadderSpec =
    if (this is LadderSpec.Plates && pairs.isEmpty()) copy(pairs = plates) else this
