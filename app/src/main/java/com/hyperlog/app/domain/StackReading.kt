package com.hyperlog.app.domain

import com.hyperlog.app.domain.LadderSpec.Companion.KG_PER_LB
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Reads a weight stack (or a dumbbell rack) from a few weights as printed on it, and learns one
 * from weights logged on a machine (SPEC, Gyms and equipment → Setting up a stack from its labels).
 *
 * Both work the same way. A stack is cumulative plates of even steps, in kg or in lb; an lb stack
 * labelled in kg shows its true weights rounded or cut to the label's precision (`4.5 · 9.1 ·
 * 13.6`, or whole kg: `36 · 43 · 49`). Kg steps that fit cleanly win; otherwise the one lb step
 * that explains every label. Where the weights could be more than one stack, or too few are
 * given, the answer is to ask for one more rather than to guess.
 */
object StackReading {
    sealed interface Result {
        data class Read(val spec: LadderSpec.PinStack) : Result
        /** Not enough to go on: one more weight, or the plates in between, tells the stacks apart. */
        data class OneMore(val need: Need) : Result
        /** No stack gives these weights. */
        data object NoStack : Result
    }

    enum class Need {
        /** Too few weights, or they fit more than one stack: type one more. */
        More,
        /** The weights skip plates: type plates next to each other, from the lightest. */
        NextToEachOther,
    }

    /** A plate count for a stack whose top isn't given. */
    const val DEFAULT_PLATES = 20

    /** Kg steps a stack or rack is made in; anything else is more likely an lb stack's labels. */
    private val CleanKg = listOf(0.5, 1.0, 1.25, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.5, 8.0, 10.0, 12.5, 15.0, 20.0, 25.0)

    /** Lb steps stacks come in, heaviest first. */
    private val LbSteps = listOf(25.0, 20.0, 15.0, 12.5, 10.0, 7.5, 5.0, 2.5)

    /** One typed weight, and whether `…` came before it (fill in the steps up to it). */
    data class Typed(val value: Double, val afterGap: Boolean)

    /**
     * The weights in [text]: separated by spaces, commas or semicolons, `…` or `..` to fill in the
     * steps up to the next one (`1, 2, 3 … 10, 12.5, 15 … 45`). A decimal comma works too (`12,5`).
     * Null when something isn't a weight.
     */
    fun parse(text: String): List<Typed>? {
        // A comma between digits with no space after it is a decimal comma, unless the word has more of them (`1,2,3`).
        val words = text.replace("…", " … ").replace(Regex("\\.{2,}"), " … ").trim()
            .split(Regex("[\\s;]+")).filter { it.isNotEmpty() }
        val out = mutableListOf<Typed>()
        var gap = false
        for (raw in words) {
            val word = raw.trim(',')
            if (word.isEmpty()) continue
            val parts = if (word.count { it == ',' } == 1 && Regex("\\d,\\d").containsMatchIn(word)) listOf(word.replace(',', '.'))
            else word.split(",").filter { it.isNotEmpty() }
            for (part in parts) {
                if (part == "…") { gap = true; continue }
                val v = part.toDoubleOrNull()?.takeIf { it > 0 && it < 1000 } ?: return null
                out += Typed(v, gap)
                gap = false
            }
        }
        return out
    }

    /**
     * The stack in [text], typed as its labels read in [unit]: two or three plates next to each
     * other from the lightest, and optionally `… top`. Steps that change partway are written out
     * (`1, 2 … 10, 12.5, 15 … 45`) and taken as typed.
     */
    fun read(text: String, unit: WeightUnit, builtIn: List<Double> = emptyList(), pinPlates: Boolean = true): Result {
        val typed = parse(text) ?: return Result.NoStack
        val gaps = typed.indices.filter { typed[it].afterGap }
        // Only a last `… top`: the plates before it are read, the top sets the length.
        if (gaps.isEmpty() || gaps == listOf(typed.lastIndex)) {
            val top = if (gaps.isEmpty()) null else typed.last().value
            val head = if (top == null) typed else typed.dropLast(1)
            return read(head.map { it.value }, unit, top, builtIn, pinPlates)
        }
        val plates = walk(typed) ?: return Result.OneMore(Need.More)
        return Result.Read(LadderSpec.PinStack(unit, plates, labelKg = exactLabels(unit), builtIn = builtIn, pinPlates = pinPlates))
    }

    /** The plates [typed] gives taken exactly as typed, `…` filled in with the step before it; null when it doesn't add up. */
    fun exact(text: String): List<Double>? = parse(text)?.let(::walk)

    private fun walk(typed: List<Typed>): List<Double>? {
        if (typed.isEmpty() || typed.first().afterGap) return null
        val plates = mutableListOf(typed.first().value)
        var step: Double? = null
        for (i in 1 until typed.size) {
            val a = plates.last()
            val b = typed[i].value
            if (b <= a + 1e-9) return null
            if (typed[i].afterGap) {
                val s = step ?: return null
                val n = (b - a) / s
                val whole = n.roundToInt()
                if (abs(n - whole) > 1e-6 && i != typed.lastIndex) return null
                val count = if (abs(n - whole) <= 1e-6) whole else floor(n).toInt()
                repeat(count) { plates += round3(a + (it + 1) * s) }
            } else {
                step = round3(b - a)
                plates += b
            }
            if (plates.size > LadderSpec.MAX_PINS) return null
        }
        return plates
    }

    /** The stack whose plates next to each other, from the lightest, read [printed] in [unit], up to [top] (else [DEFAULT_PLATES] plates). */
    fun read(printed: List<Double>, unit: WeightUnit, top: Double? = null, builtIn: List<Double> = emptyList(), pinPlates: Boolean = true): Result {
        val v = printed.map(::round3).distinct().sorted()
        if (v.size < 2) return Result.OneMore(Need.More)
        val model = when (unit) {
            WeightUnit.Lb -> evenSteps(v, clean = false)?.let { Model(WeightUnit.Lb, it.first, it.second, it.third, exactLabels(WeightUnit.Lb), false) }
                ?: return Result.OneMore(Need.NextToEachOther)
            WeightUnit.Kg -> readKgLabels(v) ?: return if (fitsWithGaps(v)) Result.OneMore(Need.NextToEachOther) else Result.NoStack
        }
        if (model == AMBIGUOUS) return Result.OneMore(Need.More)
        return Result.Read(model.stack(top, builtIn, pinPlates))
    }

    /** How a stack was read: a lighter [first] plate (true weight), then [start] in [step]s, all in [unit], with its labels. */
    private data class Model(val unit: WeightUnit, val first: Double?, val start: Double, val step: Double, val labelKg: Double, val cut: Boolean) {
        fun stack(top: Double?, builtIn: List<Double>, pinPlates: Boolean, atLeast: Double? = null): LadderSpec.PinStack {
            val base = LadderSpec.PinStack.steps(unit, start, step, start, first, labelKg, cut, builtIn, pinPlates)
            val count = when {
                top != null -> {
                    // The plate at or below the top, as its label reads.
                    var n = 1
                    while (n < LadderSpec.MAX_PINS && labelOf(start + n * step) <= top + 1e-6) n++
                    n
                }
                else -> {
                    var n = DEFAULT_PLATES - (if (first != null) 1 else 0)
                    while (atLeast != null && n < LadderSpec.MAX_PINS && labelOf(start + (n - 1) * step) < atLeast - 0.05) n++
                    n
                }
            }
            return base.copy(plates = base.plates.dropLast(1) + List(count) { round3(start + it * step) })
        }

        fun labelOf(w: Double): Double = LadderSpec.PinStack(unit, listOf(w), labelKg, cut).pins.single()
    }

    private val AMBIGUOUS = Model(WeightUnit.Kg, null, 0.0, 0.0, 0.0, false)

    /** First (or null), start and step of plates [v] next to each other: even steps, or a lighter first plate then even steps. */
    private fun evenSteps(v: List<Double>, clean: Boolean): Triple<Double?, Double, Double>? {
        val d = v.zipWithNext { a, b -> round3(b - a) }
        fun ok(s: Double) = !clean || CleanKg.any { abs(it - s) < 1e-6 }
        if (d.all { abs(it - d[0]) < 1e-6 } && ok(d[0])) return Triple(null, v[0], d[0])
        if (v.size >= 3 && d.drop(1).all { abs(it - d[1]) < 1e-6 } && d[0] < d[1] && ok(d[1])) return Triple(v[0], v[1], d[1])
        return null
    }

    /** Kg labels [v]: clean kg steps, else the one lb step that explains them all, else even kg steps of any size. */
    private fun readKgLabels(v: List<Double>): Model? {
        evenSteps(v, clean = true)?.let { (f, s, d) -> return Model(WeightUnit.Kg, f, s, d, exactLabels(WeightUnit.Kg), false) }
        val p = precision(v)
        val lb = LbSteps.mapNotNull { s -> lbFit(v, s, p, consecutive = true)?.let { s to it } }
        val irregular = if (v.size >= 3 && lb.isEmpty()) LbSteps.mapNotNull { s ->
            lbFit(v.drop(1), s, p, consecutive = true)?.let { l1 -> lbPlate(v[0], p)?.takeIf { it < l1 }?.let { f -> s to (f to l1) } }
        } else emptyList()
        when {
            lb.size == 1 -> {
                val (s, l0) = lb.single()
                return Model(WeightUnit.Lb, null, l0, s, labelPrecision(p), cutLabels(v, null, l0, s, p))
            }
            lb.size > 1 -> return AMBIGUOUS
            irregular.size == 1 -> {
                val (s, fl) = irregular.single()
                return Model(WeightUnit.Lb, fl.first, fl.second, s, labelPrecision(p), cutLabels(v, fl.first, fl.second, s, p))
            }
            irregular.size > 1 -> return AMBIGUOUS
        }
        // Even kg steps of an odd size, but only from three plates: two always have a step.
        if (v.size >= 3) evenSteps(v, clean = false)?.let { (f, s, d) -> return Model(WeightUnit.Kg, f, s, d, exactLabels(WeightUnit.Kg), false) }
        return null
    }

    /** Whether [v] are plates of some stack with plates between them left out. */
    private fun fitsWithGaps(v: List<Double>): Boolean {
        val p = precision(v)
        return CleanKg.any { s -> v.all { abs((it - v[0]) / s - ((it - v[0]) / s).roundToInt()) < 1e-6 } && s < v.zipWithNext { a, b -> b - a }.max() } ||
            LbSteps.any { s -> lbFit(v, s, p, consecutive = false) != null }
    }

    /**
     * The true lb weight of [v]'s first plate on a stack of [s] lb steps whose labels read [v] to
     * precision [p] (plates next to each other when [consecutive]); null when none does. Stacks
     * are built in 2.5 lb, so the first plate is a multiple of it.
     */
    private fun lbFit(v: List<Double>, s: Double, p: Double, consecutive: Boolean): Double? {
        val tol = tolerance(p)
        for (grain in listOf(2.5)) {
            val lo = ceil((v[0] - tol) / KG_PER_LB / grain).toLong()
            val hi = floor((v[0] + tol) / KG_PER_LB / grain).toLong()
            val fits = (lo..hi).map { it * grain }.filter { it > 0 }.mapNotNull { l0 ->
                var last = -1L
                var err = 0.0
                for ((i, kg) in v.withIndex()) {
                    val n = if (consecutive) i.toLong() else ((kg / KG_PER_LB - l0) / s).roundToLong()
                    if (n <= last) return@mapNotNull null
                    val e = abs((l0 + n * s) * KG_PER_LB - kg)
                    if (e > tol + 1e-9) return@mapNotNull null
                    err += e
                    last = n
                }
                l0 to err
            }
            fits.minByOrNull { it.second }?.let { return it.first }
        }
        return null
    }

    /** The 2.5 lb plate whose label is [kg], for a lighter first plate. */
    private fun lbPlate(kg: Double, p: Double): Double? {
        val l = (kg / KG_PER_LB / 2.5).roundToInt() * 2.5
        return l.takeIf { l > 0 && abs(l * KG_PER_LB - kg) <= tolerance(p) + 1e-9 }
    }

    /** Whether the labels [v] are the true weights cut, not rounded, to [p]: only cutting gives them all. */
    private fun cutLabels(v: List<Double>, first: Double?, l0: Double, s: Double, p: Double): Boolean {
        val trues = listOfNotNull(first) + List(v.size - (if (first != null) 1 else 0)) { l0 + it * s }
        fun labels(cut: Boolean) = LadderSpec.PinStack(WeightUnit.Lb, trues, labelPrecision(p), cut).plates.map { w ->
            LadderSpec.PinStack(WeightUnit.Lb, listOf(w), labelPrecision(p), cut).pins.single()
        }
        return labels(false) != v && labels(true) == v
    }

    /** The precision the labels [v] are printed to: whole kg, half kg, tenths or hundredths. */
    private fun precision(v: List<Double>): Double = listOf(1.0, 0.5, 0.1).firstOrNull { p -> v.all { abs(it / p - (it / p).roundToInt()) < 1e-6 } } ?: 0.01

    /** How far a label may be from the true weight: a whole-kg label can be cut from up to 0.99 above it. */
    private fun tolerance(p: Double) = if (p >= 0.1) p else 0.02

    private fun labelPrecision(p: Double) = if (p >= 0.1) p else 0.1

    private fun exactLabels(unit: WeightUnit) = if (unit == WeightUnit.Lb) 0.1 else 0.01

    /**
     * The stack that explains the weights [loggedKg] logged on a machine whose ladder is still a
     * guess ([current]), when they don't fit it: kg steps if they fit, else the one lb step that
     * does. Null until three different weights are logged, while they fit the guess, or while
     * more than one stack fits them.
     */
    fun learn(loggedKg: List<Double>, current: LadderSpec, pinPlates: List<Double>): LadderSpec.PinStack? {
        val v = loggedKg.map(::round3).sorted().fold(listOf<Double>()) { acc, w -> if (acc.isNotEmpty() && w - acc.last() < 0.05) acc else acc + w }
        if (v.size < 3) return null
        val rungs = current.ladder(pinPlates).rungs
        if (v.all { w -> rungs.any { abs(it - w) < 0.05 } }) return null
        val keep = current as? LadderSpec.PinStack
        val builtIn = keep?.builtIn.orEmpty()
        val takes = keep?.pinPlates ?: true
        // Kg steps: the largest that every weight is on.
        listOf(10.0, 5.0, 2.5).firstOrNull { s -> v.all { abs((it - v[0]) / s - ((it - v[0]) / s).roundToInt()) < 1e-6 } }?.let { s ->
            val start = ((v[0] % s).let { if (it < 1e-6) s else it }).let(::round3)
            return Model(WeightUnit.Kg, null, start, s, exactLabels(WeightUnit.Kg), false).stack(null, builtIn, takes, atLeast = v.last())
        }
        val p = precision(v)
        val fits = LbSteps.filter { s -> lbFit(v, s, p, consecutive = false) != null }
        val s = fits.firstOrNull() ?: return null
        if (fits.any { abs((s / it) - (s / it).roundToInt()) > 1e-6 }) return null
        val l0 = lbFit(v, s, p, consecutive = false)!!
        val start = (l0 % s).let { if (it < 1e-6) s else it }
        val cut = cutLabels(listOf(v[0]), null, l0, s, p)
        return Model(WeightUnit.Lb, null, start, s, labelPrecision(p), cut).stack(null, builtIn, takes, atLeast = v.last())
    }

    /** The stack's steps in words: `5 kg steps`, `10 lb steps`, `2.5, then 5 kg steps`, `1, then 2.5 kg steps`. */
    fun steps(spec: LadderSpec.PinStack): String {
        val unit = spec.unit.name.lowercase()
        val runs = spec.segments.filter { !it.single }.map { formatKg(it.step) }.distinct()
        val lead = spec.first?.let { "${formatKg(if (spec.unit == WeightUnit.Lb) spec.pins.first() else it)}, then " }.orEmpty()
        return when (runs.size) {
            0 -> "${formatKg(spec.pins.first())} kg"
            1 -> "$lead${runs.single()} $unit steps"
            else -> "$lead${runs.joinToString(", then ")} $unit steps"
        }
    }

    /**
     * The stack in one line, as the setup checks it: `1–10 kg by 1 · 12.5–45 by 2.5`, an lb stack
     * by its labels and lb step (`4.5–90.7 kg by 10 lb`), a lighter first plate as `2.5, then`.
     */
    fun summary(spec: LadderSpec.PinStack): String {
        val lb = spec.unit == WeightUnit.Lb
        val label = { w: Double -> formatKg(LadderSpec.PinStack(spec.unit, listOf(w), spec.labelKg, spec.labelsCut).pins.single()) }
        val segs = spec.segments
        // No even run of three: just the weights.
        if (spec.plates.size > 2 && segs.none { it.count >= 3 }) return spec.plates.joinToString(" · ") { label(it) } + " kg"
        var unitPlaced = false
        return buildString {
            segs.forEachIndexed { i, seg ->
                if (i > 0) append(if (segs[i - 1].single) ", then " else " · ")
                if (seg.single) {
                    append(label(seg.from))
                    if (i == segs.lastIndex && !unitPlaced) append(" kg")
                } else {
                    append(label(seg.from)).append("–").append(label(seg.to))
                    if (!unitPlaced) { append(" kg"); unitPlaced = true }
                    append(" by ").append(formatKg(seg.step))
                    if (lb) append(" lb")
                }
            }
        }
    }

    private fun round3(x: Double) = Math.round(x * 1000) / 1000.0
}
