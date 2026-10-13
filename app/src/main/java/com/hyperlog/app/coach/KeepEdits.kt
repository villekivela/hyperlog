package com.hyperlog.app.coach

import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.RepRange

/**
 * One exercise of a program session as it stands: its slot (null when added in the session), the
 * exercise done in it, whether it was taken out, its own sets when they were edited, and what was
 * logged.
 */
data class EditedExercise(
    val slotId: String?,
    val exerciseId: String,
    val removed: Boolean,
    val sets: List<SetPrescription>?,
    val logged: List<LoggedSet>,
)

/**
 * "Keep for the rest of the block" (SPEC, Edit on the fly): a session's edits as a Change for the
 * rest of the block. Swaps, removals, set counts, an RIR change shared by every set, and exercises
 * added with sets logged are kept. Reps targets, techniques and the order stay in the session:
 * no operation says them.
 */
object KeepEdits {
    /** The Change [exercises] (in session order) make to [day]; null when there is nothing to keep. */
    fun change(day: PlannedDay, exercises: List<EditedExercise>, summary: String): Change? {
        val operations = buildList {
            var lastSlot: String? = null
            exercises.forEach { e ->
                val slot = day.slot(e.slotId)
                when {
                    e.slotId == null -> {
                        if (!e.removed && e.logged.isNotEmpty()) add(added(day, e, lastSlot))
                    }
                    slot == null -> Unit
                    e.removed -> add(Operation.Remove(slot.id))
                    else -> {
                        if (e.exerciseId != slot.exercise) add(Operation.Swap(slot.id, e.exerciseId))
                        val planned = slot.setsFor(e.exerciseId)
                        val own = e.sets
                        if (own != null && own.isNotEmpty()) {
                            if (own.size != planned.size) add(Operation.AdjustSets(slot.id, own.size))
                            sharedRirChange(planned, own)?.let { add(Operation.AdjustRir(slot.id, it)) }
                        }
                    }
                }
                // An added exercise follows the last slot that stays.
                if (slot != null && !e.removed) lastSlot = slot.id
            }
        }
        return if (operations.isEmpty()) null else Change(summary, Scope.RestOfBlock, operations)
    }

    private fun added(day: PlannedDay, e: EditedExercise, after: String?): Operation.Add {
        val reps = e.logged.map { it.fullReps }
        val set = ChangeSet(count = e.logged.size, reps = RepRange(reps.min().coerceAtLeast(1), reps.max().coerceAtLeast(1)), rir = e.logged.minOf { it.rir })
        return Operation.Add(day.day.id, e.exerciseId, listOf(set), after = after)
    }

    /** The RIR change every set shared, if one did. */
    private fun sharedRirChange(planned: List<SetPrescription>, own: List<SetPrescription>): Int? {
        val deltas = planned.zip(own).map { (p, o) -> if (p.rir == null || o.rir == null) null else o.rir - p.rir }
        val first = deltas.firstOrNull() ?: return null
        return first.takeIf { it != 0 && deltas.all { d -> d == first } }
    }
}
