package com.hyperlog.app.legacy

import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.StartingWeightEntity
import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.progression.Progression
import com.hyperlog.app.progression.Suggestion
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The starting weights imported from a legacy backup (SPEC, Fresh start). Importing replaces an
 * earlier import and adds no sessions; an exercise's starting weight is used only until it has
 * its own history.
 */
class StartingWeights(db: HyperlogDatabase) {
    private val dao = db.startingWeights()

    /** How many exercises have a starting weight. */
    val count: Flow<Int> = dao.observeCount()

    /** Reads [json] as a legacy backup and keeps its starting weights for [seed]'s exercises. */
    suspend fun import(json: String, seed: LibrarySeed): LegacyBackup.Result {
        val result = LegacyBackup.startingWeights(json, seed.exercises.map { it.id }.toSet(), seed.merged)
        if (result is LegacyBackup.Result.Read) {
            dao.replaceAll(result.weights.map { StartingWeightEntity(it.exerciseId, it.kg, it.reps, it.rir, it.doneAt) })
        }
        return result
    }

    suspend fun get(exerciseId: String): StartingWeight? =
        dao.get(exerciseId)?.let { StartingWeight(it.exerciseId, it.weightKg, it.reps, it.rir, it.doneAt) }
}

/** `60 kg × 8 @ 2 RIR, 12 Sep`; a bodyweight set is `9 reps @ 1 RIR, 12 Sep`. */
fun StartingWeight.text(zone: ZoneId = ZoneId.systemDefault()): String {
    val set = if (kg > 0) "${formatKg(kg)} kg × $reps" else "$reps reps"
    val rir = rir?.let { " @ $it RIR" }.orEmpty()
    return "$set$rir, ${DATE.format(Instant.ofEpochMilli(doneAt).atZone(zone))}"
}

/**
 * A free weight's first suggestion from its starting weight. With a [target], the old set is
 * worked into the target's range and RIR through its estimate, rounded down to a rung; ad hoc it is
 * the old set itself on the ladder.
 */
fun StartingWeight.suggestion(target: SetPrescription?, ladder: WeightLadder): Suggestion {
    val reps = target?.reps?.let { reps.coerceIn(it.min, it.max) } ?: reps
    val ideal = if (target == null || kg <= 0) kg
    else kg * (1 + (this.reps + (rir ?: 0)) / Progression.EPLEY) / (1 + (reps + (target.rir ?: 0)) / Progression.EPLEY)
    val suggested = if (kg <= 0) 0.0 else ladder.rungs.lastOrNull { it <= ideal + 0.001 } ?: ladder.rungs.first()
    val moved = kotlin.math.abs(suggested - kg) >= 0.05 || reps != this.reps
    val why = "From old app: ${text()}" + if (moved) " → " + (if (suggested > 0) "${formatKg(suggested)} kg × $reps" else "$reps reps") else ""
    val short = (if (suggested > 0) "${formatKg(suggested)} × $reps" else "$reps reps") + " · from the old app"
    return Suggestion(suggested, reps, Suggestion.Kind.Starting, why, short)
}

private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
