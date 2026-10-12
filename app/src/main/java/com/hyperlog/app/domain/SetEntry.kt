package com.hyperlog.app.domain

import java.util.Locale

/** One set as it was done. Weight is in kg (per dumbbell for dumbbell exercises). */
data class LoggedSet(val weightKg: Double, val fullReps: Int, val partialReps: Int, val rir: Int)

/** The value in the active row that the pad types into. */
enum class Field { Weight, Reps }

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
 * Prefilled values are real values: [done] logs them as shown. The first key on a field replaces
 * its value ([fresh]); − and + step from it.
 */
data class SetEntry(
    val weight: String,
    val full: String,
    val partial: String,
    val rir: Int?,
    val field: Field,
    val partialMode: Boolean,
    val fresh: Boolean,
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
            partialMode -> partial
            else -> full
        }

    /** The reps cell: `12`, or `12 + 6` once partials are on (`12 +` before any are typed). */
    val repsText: String
        get() = if (partialMode || partial.isNotEmpty()) "$full + $partial".trimEnd() else full

    val weightKg: Double? get() = weight.toDoubleOrNull()?.takeIf { it > 0 }

    val canLog: Boolean get() = toLoggedSet() != null

    /** Whether ✓ does anything now: on the weight it needs a weight, on reps a whole set. */
    val canConfirm: Boolean get() = if (this.field == Field.Weight) weightKg != null else canLog

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
            partialMode -> 2
            else -> 3
        }

    fun selectRir(value: Int) = copy(rir = value)

    fun focus(to: Field) = copy(field = to, fresh = true, partialMode = if (to == Field.Weight) false else partialMode)

    fun partialMode(on: Boolean) = copy(field = Field.Reps, partialMode = on, fresh = true)

    fun done(): Done = when {
        field == Field.Weight && weightKg != null -> Done.Moved(focus(Field.Reps))
        field == Field.Weight -> Done.Nothing
        else -> toLoggedSet()?.let { Done.Log(it) } ?: Done.Nothing
    }

    fun toLoggedSet(): LoggedSet? {
        val kg = weightKg ?: return null
        val reps = full.toIntOrNull() ?: return null
        val selected = rir ?: return null
        if (reps <= 0) return null
        return LoggedSet(kg, reps, partial.toIntOrNull() ?: 0, selected)
    }

    private fun type(text: String) = when {
        field == Field.Weight -> copy(weight = text, fresh = false)
        partialMode -> copy(partial = text, fresh = false)
        else -> copy(full = text, fresh = false)
    }

    private fun step(by: Int, ladder: WeightLadder): SetEntry {
        val stepped = if (field == Field.Weight) {
            formatKg(ladder.step(weightKg ?: ladder.rungs.first(), by))
        } else {
            ((current.toIntOrNull() ?: 0) + by).coerceAtLeast(0).toString()
        }
        return type(stepped).copy(fresh = true)
    }

    companion object {
        /**
         * The entry for the next set: the previous set's weight, reps and RIR, or, with nothing
         * to go on, an empty weight to type first.
         */
        fun prefill(previous: LoggedSet?): SetEntry = if (previous == null) {
            SetEntry("", "", "", null, Field.Weight, partialMode = false, fresh = true)
        } else {
            SetEntry(
                weight = formatKg(previous.weightKg),
                full = previous.fullReps.toString(),
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

/** `25 × 10 @ 2`, `5.7 × 12 + 6p @ 0` (SPEC, Full and partial reps). */
fun LoggedSet.summary(): String = buildString {
    append("${formatKg(weightKg)} × $fullReps")
    if (partialReps > 0) append(" + ${partialReps}p")
    append(" @ $rir")
}
