package com.hyperlog.app.session

import androidx.room.withTransaction
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.formatKg

/**
 * A logged set, or one of its drops, mini-sets or hold ([extension], by position), loaded into
 * the pad to be corrected (SPEC, Corrections). [entry] is what the pad holds for it; the set to
 * do keeps its own entry, so rest and the rest notification never see a correction.
 */
data class Correction(
    val sessionExerciseId: Long,
    /** The set's index in its exercise. */
    val set: Int,
    /** The drop, mini-set or hold's position in the set; null for the set itself. */
    val extension: Int?,
    /** What the corrected row is: null for a set. */
    val kind: Extension.Kind?,
    val entry: SetEntry,
) {
    /** A mini-set and a hold are done at the set's weight, so their weight can't be focused. */
    val weightLocked: Boolean get() = kind == Extension.Kind.MiniSet || kind == Extension.Kind.Hold

    /** What ✓ does with it: moves on to the next value, or has the corrected value to save. */
    internal fun done(): Step = when {
        entry.field == Field.Weight && entry.weightKg != null -> Step.Moved(copy(entry = entry.focus(Field.Reps)))
        kind != null -> entry.toExtension(kind)?.let { Step.SaveExtension(it) } ?: Step.Nothing
        else -> when (val result = entry.done()) {
            is SetEntry.Done.Moved -> Step.Moved(copy(entry = result.entry))
            is SetEntry.Done.Log -> Step.SaveSet(result.set)
            SetEntry.Done.Nothing -> Step.Nothing
        }
    }

    internal sealed interface Step {
        data class Moved(val correction: Correction) : Step
        data class SaveSet(val set: LoggedSet) : Step
        data class SaveExtension(val extension: Extension) : Step
        data object Nothing : Step
    }
}

/**
 * Loads logged rows into the pad and writes corrections back, for the running session and for
 * past ones alike. A correction changes only the values: the set keeps its technique and its
 * drops, mini-sets or hold. Progression reads the corrected rows from then on.
 */
internal class SetCorrections(private val db: HyperlogDatabase) {
    private val dao = db.sessions()

    /** Set [set] of [sessionExerciseId], or its [extension], as the pad's entry; null if there is no such row. */
    suspend fun load(sessionExerciseId: Long, set: Int, extension: Int? = null): Correction? {
        val row = dao.sets(sessionExerciseId).getOrNull(set) ?: return null
        if (extension == null) {
            val entry = SetEntry(
                weight = formatKg(row.weightKg),
                full = row.fullReps.toString(),
                partial = if (row.partialReps > 0) row.partialReps.toString() else "",
                rir = row.rir,
                field = Field.Reps,
                partialMode = false,
                fresh = true,
                right = row.rightReps?.toString(),
            )
            return Correction(sessionExerciseId, set, null, null, entry)
        }
        val ext = dao.extensions(row.id).getOrNull(extension) ?: return null
        val entry = SetEntry(formatKg(ext.weightKg), ext.reps.toString(), "", null, Field.Reps, partialMode = false, fresh = true)
        return Correction(sessionExerciseId, set, extension, Extension.Kind.valueOf(ext.kind), entry)
    }

    /**
     * Writes [correction]'s values. A corrected weight was typed on purpose, so it is no longer
     * one kept past a weight check; mini-sets and a hold follow the set's new weight.
     */
    suspend fun saveSet(correction: Correction, value: LoggedSet) = db.withTransaction {
        val row = dao.sets(correction.sessionExerciseId).getOrNull(correction.set) ?: return@withTransaction
        dao.correctSet(row.id, value.weightKg, value.fullReps, value.partialReps, value.rir, value.rightReps)
        if (value.weightKg != row.weightKg) dao.moveSetWeight(row.id, value.weightKg)
    }

    suspend fun saveExtension(correction: Correction, value: Extension) = db.withTransaction {
        val row = dao.sets(correction.sessionExerciseId).getOrNull(correction.set) ?: return@withTransaction
        val ext = dao.extensions(row.id).getOrNull(correction.extension ?: return@withTransaction) ?: return@withTransaction
        dao.correctExtension(ext.id, value.weightKg, value.reps)
    }
}
