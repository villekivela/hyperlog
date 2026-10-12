package com.hyperlog.app.rest

import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.FollowUp

/**
 * What the rest notification shows (SPEC, Rest on the lock screen): the countdown and the set it
 * leads to. Rest rules decide whether there is one at all; a drop, a superset partner's set or a
 * running hold has none.
 */
data class RestNotice(
    val kind: Kind,
    /** When the countdown reaches zero; after it, the notification counts up from here. */
    val endsAt: Long,
    val totalMs: Long,
    /** The set after it; null when the session has none on screen. */
    val next: NextSet?,
) {
    enum class Kind {
        /** Rest after a set or a superset round. */
        Rest,
        /** The short breathing pause before a myo mini-set. */
        Breathing,
    }
}

/** The set coming up: `DB Bench Press · Set 3`, `25 kg × 8–10 @ 1`. */
data class NextSet(
    val exercise: String,
    /** The set's number, from 1. */
    val set: Int,
    /** The myo mini-set's number, from 1; null for a set. */
    val miniSet: Int? = null,
    val weightKg: Double?,
    /** Target reps (`8–10`), or the prefilled reps without a target. */
    val reps: String,
    /** Target RIR; null without a target. */
    val rir: Int? = null,
)

/**
 * The rest notification for this session as it is now, or null when no rest or breathing pause
 * runs. Once the day's planned sets are all logged nothing is left to rest for (Day done).
 */
fun ActiveSession.restNotice(): RestNotice? {
    val ends = restEndsAt ?: return null
    val current = current
    val entry = entry
    if (breathing) {
        val miniSet = followUp as? FollowUp.MiniSet ?: return null
        val next = current?.let {
            NextSet(it.exercise.name, set = it.sets.size, miniSet = miniSet.index + 1, weightKg = entry?.weight?.toDoubleOrNull(), reps = miniSet.target.toString())
        }
        return RestNotice(RestNotice.Kind.Breathing, ends, restTotalMs, next)
    }
    if (current != null && current.done && exercises.all { it.plan == null || it.done }) return null
    val next = if (current == null || current.done || entry == null) null else {
        val index = current.sets.size
        val planned = current.plan?.getOrNull(index)
        NextSet(
            current.exercise.name, set = index + 1, weightKg = entry.weight.toDoubleOrNull(),
            reps = planned?.reps?.toString() ?: entry.full, rir = planned?.rir,
        )
    }
    return RestNotice(RestNotice.Kind.Rest, ends, restTotalMs, next)
}
