package com.hyperlog.app.domain

import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.MovementPattern

/** A lighter set before the work sets. It is never logged, and counts toward neither volume nor progression. */
data class WarmupSet(val weightKg: Double, val reps: Int)

/**
 * Warm-ups ramp from the first work set's weight by percentage, each rounded to the nearest rung
 * of the equipment's ladder (SPEC, Screens → Today). Heavy lifts get 4 ramp sets when the first
 * work set is 6 reps or fewer, else 3; isolation exercises get 1. A warm-up that doesn't climb
 * above the one before, or reaches the work weight, is left out. On a bar or plate-loaded
 * machine warm-ups keep to 5 kg steps, so the small plates only go on for the work sets.
 */
object Warmups {
    private val Heavy = listOf(0.45 to 8, 0.60 to 5, 0.75 to 3, 0.85 to 2)
    private val Medium = listOf(0.45 to 8, 0.65 to 5, 0.85 to 3)
    private val Isolation = listOf(0.60 to 8)

    /** Movement patterns that work one joint, so one warm-up does. */
    private val SingleJoint = setOf(
        MovementPattern.KneeExtension, MovementPattern.KneeFlexion, MovementPattern.CalfRaise,
        MovementPattern.HipAbduction, MovementPattern.HipAdduction, MovementPattern.ChestFly,
        MovementPattern.LateralRaise, MovementPattern.RearDeltFly, MovementPattern.Shrug,
        MovementPattern.ElbowFlexion, MovementPattern.ElbowExtension, MovementPattern.Wrist,
        MovementPattern.CoreFlexion, MovementPattern.Rotation, MovementPattern.Neck, MovementPattern.Hang,
    )

    /**
     * The warm-ups before a first work set of [workKg] for [reps] (planned or typed; null while
     * not known) on [ladder]. Bodyweight exercises have none. [plateLoaded]: the weight is loaded
     * with plates.
     */
    fun ramp(workKg: Double, exercise: Exercise, reps: Int?, ladder: WeightLadder, plateLoaded: Boolean = false): List<WarmupSet> {
        if (exercise.equipment == Equipment.Bodyweight || exercise.equipment == Equipment.WeightedBodyweight) return emptyList()
        val scheme = when {
            exercise.pattern in SingleJoint -> Isolation
            reps != null && reps <= 6 -> Heavy
            else -> Medium
        }
        val rungs = if (!plateLoaded) ladder else ladder.rungs.filter { isFiveKgStep(it) && it < workKg }
            .takeIf { it.isNotEmpty() }?.let(::WeightLadder) ?: ladder
        val ramp = mutableListOf<WarmupSet>()
        for ((percent, n) in scheme) {
            val kg = rungs.rungs[rungs.nearestIndex(workKg * percent)]
            if (kg >= workKg - 0.01) break
            if (ramp.lastOrNull()?.let { kg <= it.weightKg } == true) continue
            ramp += WarmupSet(kg, n)
        }
        return ramp
    }

    /** [ramp] with the warm-ups typed by hand in place of theirs; a typed one past the ramp's end is left out. */
    fun withTyped(ramp: List<WarmupSet>, typed: Map<Int, WarmupSet>): List<WarmupSet> =
        ramp.mapIndexed { i, w -> typed[i] ?: w }

    /** Warm-ups typed by hand, by index, as stored with the session exercise: `0=40.0:8,2=55.0:5`. */
    fun encode(typed: Map<Int, WarmupSet>): String =
        typed.toSortedMap().entries.joinToString(",") { (i, w) -> "$i=${w.weightKg}:${w.reps}" }

    /**
     * The warm-ups [encode] wrote, by index; null for nothing stored or a value it can't read. A
     * list without indices (`40.0:8,55.0:5`, written before each warm-up was kept on its own) is
     * read as every warm-up typed.
     */
    fun decode(text: String?): Map<Int, WarmupSet>? = text?.takeIf { it.isNotBlank() }?.split(",")?.withIndex()?.associate { (position, part) ->
        val index = if ('=' in part) part.substringBefore('=').toIntOrNull() ?: return null else position
        val (kg, reps) = part.substringAfter('=').split(":").takeIf { it.size == 2 } ?: return null
        index to WarmupSet(kg.toDoubleOrNull() ?: return null, reps.toIntOrNull() ?: return null)
    }

    private fun isFiveKgStep(kg: Double) = Math.abs(kg / 5 - Math.round(kg / 5)) < 0.001
}
