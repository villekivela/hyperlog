package com.hyperlog.app.domain

import com.hyperlog.app.program.Technique
import java.util.Locale

/**
 * One set as it was done. Weight is in kg (per dumbbell for dumbbell exercises). Drops, myo
 * mini-sets and an iso hold are [extensions] of the set: they record no RIR and never move
 * progression.
 */
data class LoggedSet(
    val weightKg: Double,
    val fullReps: Int,
    val partialReps: Int,
    /** Null when the set was logged without an RIR. */
    val rir: Int?,
    /** Its weight was kept past a weight check with a second ✓. */
    val kept: Boolean = false,
    /** The intensity technique it was done with. */
    val technique: Technique? = null,
    val extensions: List<Extension> = emptyList(),
    /**
     * A unilateral set split into left and right: [fullReps] are the left side's, these the
     * right's. Null when one number stands for each side.
     */
    val rightReps: Int? = null,
) {
    /** The reps progression reads: the weaker side of a split set. */
    val progressionReps: Int get() = rightReps?.let { minOf(it, fullReps) } ?: fullReps
}

/** A continuation of a set: a drop, a myo mini-set, or an iso hold (whose [reps] are the seconds held). */
data class Extension(val kind: Kind, val weightKg: Double, val reps: Int) {
    enum class Kind { Drop, MiniSet, Hold }
}

/** The value in the active row that the pad types into. [Right] is the right side of a split set. */
enum class Field { Weight, Reps, Right }

/** A key on the pad's grid. ✓ is not a key here: see [SetEntry.done]. */
sealed interface PadKey {
    data class Digit(val value: Int) : PadKey
    data object Dot : PadKey
    data object Backspace : PadKey
    data object Minus : PadKey
    data object Plus : PadKey
}

/**
 * What the pad has in the active row: the text of each value, the selected RIR, which value is
 * focused and whether reps go to full or partial. Values are text so that a half-typed weight
 * ("62.") survives as typed.
 *
 * Prefilled weight and RIR are real values: [done] logs them as shown. Reps are never prefilled.
 * The first key on a field replaces its value ([fresh]); − and + step from it.
 */
data class SetEntry(
    val weight: String,
    val full: String,
    val partial: String,
    val rir: Int?,
    val field: Field,
    val partialMode: Boolean,
    val fresh: Boolean,
    /** The right side's reps of a unilateral set split into left and right; null when not split. */
    val right: String? = null,
) {
    sealed interface Done {
        /** ✓ on the weight moves to reps. */
        data class Moved(val entry: SetEntry) : Done
        data class Log(val set: LoggedSet) : Done
        /** Nothing to log yet: ✓ looks disabled. */
        data object Nothing : Done
    }

    /** The text of the value the pad is typing into. */
    val current: String
        get() = when {
            this.field == Field.Weight -> weight
            this.field == Field.Right -> right.orEmpty()
            partialMode -> partial
            else -> full
        }

    /** The reps cell: `12`, or `12 + 6` once partials are on (`12 +` before any are typed). */
    val repsText: String
        get() {
            val left = if (partialMode || partial.isNotEmpty()) "$full + $partial".trimEnd() else full
            return if (right != null) "$left / $right" else left
        }

    /** The typed weight. 0 is a weight: a bodyweight set, or nothing on the belt. */
    val weightKg: Double? get() = weight.toDoubleOrNull()?.takeIf { it >= 0 }

    val canLog: Boolean get() = toLoggedSet() != null

    /** Whether ✓ does anything now: on the weight it needs a weight, on reps a whole set. */
    val canConfirm: Boolean
        get() = when {
            this.field == Field.Weight -> weightKg != null
            this.field == Field.Reps && right != null -> full.toIntOrNull()?.let { it > 0 } == true
            else -> canLog
        }

    fun press(key: PadKey, ladder: WeightLadder): SetEntry = when (key) {
        is PadKey.Digit -> type(((if (fresh) "" else current) + key.value).take(maxChars))
        PadKey.Dot -> if (field != Field.Weight) this else {
            val base = if (fresh) "" else current
            type(if ('.' in base) base else (base.ifEmpty { "0" } + ".").take(maxChars))
        }
        PadKey.Backspace -> type(current.dropLast(1))
        PadKey.Minus -> step(-1, ladder)
        PadKey.Plus -> step(1, ladder)
    }

    /** Room for `102.5` kg, `100` reps and `20` partials, so every value fits its cell. */
    private val maxChars: Int
        get() = when {
            this.field == Field.Weight -> 5
            this.field == Field.Right -> 3
            partialMode -> 2
            else -> 3
        }

    /** Picking the selected RIR again clears it. */
    fun selectRir(value: Int) = copy(rir = if (rir == value) null else value)

    fun focus(to: Field) = copy(field = to, fresh = true, partialMode = if (to == Field.Weight) false else partialMode)

    fun partialMode(on: Boolean) = copy(field = Field.Reps, partialMode = on, fresh = true)

    /**
     * Splits a unilateral set into left and right ([full] becomes the left side, the right starts
     * on the same reps), or back to one number for both sides.
     */
    fun split(on: Boolean) = if (on) copy(right = right ?: full, fresh = true)
    else copy(right = null, field = if (field == Field.Right) Field.Reps else field, fresh = true)

    fun done(): Done = when {
        field == Field.Weight && weightKg != null -> Done.Moved(focus(Field.Reps))
        // A split set takes the left side, then the right.
        field == Field.Reps && right != null && full.toIntOrNull()?.let { it > 0 } == true -> Done.Moved(focus(Field.Right))
        field == Field.Weight -> Done.Nothing
        else -> toLoggedSet()?.let { Done.Log(it) } ?: Done.Nothing
    }

    fun toLoggedSet(): LoggedSet? {
        val kg = weightKg ?: return null
        val reps = full.toIntOrNull() ?: return null
        if (reps <= 0) return null
        val rightReps = if (right == null) null else right.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return LoggedSet(kg, reps, partial.toIntOrNull() ?: 0, rir, rightReps = rightReps)
    }

    /** A drop, mini-set or hold as typed: a weight and its reps (a hold's seconds); no RIR. */
    fun toExtension(kind: Extension.Kind): Extension? {
        val kg = weightKg ?: return null
        val reps = full.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return Extension(kind, kg, reps)
    }

    private fun type(text: String) = when {
        field == Field.Weight -> copy(weight = text, fresh = false)
        field == Field.Right -> copy(right = text, fresh = false)
        partialMode -> copy(partial = text, fresh = false)
        else -> copy(full = text, fresh = false)
    }

    private fun step(by: Int, ladder: WeightLadder): SetEntry {
        val stepped = if (field == Field.Weight) {
            // With no weight yet, either key starts on the lightest rung rather than stepping past it.
            formatKg(weightKg?.let { ladder.step(it, by) } ?: ladder.rungs.first())
        } else {
            ((current.toIntOrNull() ?: 0) + by).coerceAtLeast(0).toString()
        }
        return type(stepped).copy(fresh = true)
    }

    companion object {
        /**
         * The entry for the next set: the previous set's weight and RIR, or, with nothing to go on,
         * an empty weight to type first. Reps are never prefilled.
         */
        fun prefill(previous: LoggedSet?): SetEntry = if (previous == null) {
            SetEntry("", "", "", null, Field.Weight, partialMode = false, fresh = true)
        } else {
            SetEntry(
                weight = formatKg(previous.weightKg),
                full = "",
                partial = "",
                rir = previous.rir,
                field = Field.Reps,
                partialMode = false,
                fresh = true,
            )
        }
    }
}

/** `60`, `62.5`, `5.7`: whole numbers without a decimal, else one decimal. */
fun formatKg(kg: Double): String =
    if (kg == Math.floor(kg)) kg.toLong().toString()
    else String.format(Locale.ROOT, "%.2f", kg).trimEnd('0').trimEnd('.')

/**
 * `25 × 10 @ 2`, `5.7 × 12 + 6p @ 0` (SPEC, Full and partial reps), with what extended the set
 * after it: drops (`, 18 × 7, 12 × 6`), mini-sets (` + 4 + 3`) or a hold (` + 20 s`). A split set
 * reads `10/9`.
 */
fun LoggedSet.summary(): String = buildString {
    append("${formatKg(weightKg)} × $fullReps")
    rightReps?.let { append("/$it") }
    if (partialReps > 0) append(" + ${partialReps}p")
    rir?.let { append(" @ $it") }
    extensions.forEach {
        when (it.kind) {
            Extension.Kind.Drop -> append(", ${formatKg(it.weightKg)} × ${it.reps}")
            Extension.Kind.MiniSet -> append(" + ${it.reps}")
            Extension.Kind.Hold -> append(" + ${it.reps} s")
        }
    }
}
