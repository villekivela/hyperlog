package com.hyperlog.app.gym

import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.PlannedSlot

/** A swap for one session: the slot's [exercise] isn't at the gym; [replacement] null when nothing fits. */
data class SessionSwap(val slot: String, val exercise: String, val replacement: String?)

/**
 * Swaps for exercises a gym has no equipment for (SPEC, Gyms and equipment): the slot's
 * alternatives first, then library exercises with the same movement pattern, none of them missing
 * at the gym either. They hold for one session; the plan is untouched.
 */
object MissingSwaps {
    /** The swaps for the slots of [day] whose exercise is [missing] at the gym. */
    fun forDay(day: PlannedDay, library: List<Exercise>, missing: Set<String>): List<SessionSwap> {
        val byId = library.associateBy { it.id }
        return day.slots.filter { it.exercise in missing }.map { slot ->
            SessionSwap(slot.id, slot.exercise, candidates(slot, byId, library) { it.id !in missing }.firstOrNull()?.id)
        }
    }

    /** The swap for [exercise], not at the gym, as [forDay] picks it; null when nothing fits. */
    fun replacement(slot: PlannedSlot?, exercise: String, library: List<Exercise>, missing: Set<String>): String? {
        val byId = library.associateBy { it.id }
        val s = slot?.copy(exercise = exercise, inPlaceOf = slot.inPlaceOf ?: slot.exercise.takeIf { it != exercise })
            ?: PlannedSlot("", "", 0, exercise, emptyList(), null, emptyList())
        return candidates(s, byId, library) { it.id !in missing }.firstOrNull()?.id
    }

    /**
     * Exercises that can stand in for [slot]'s, best first: its alternatives in the program's
     * order, then library exercises with the same movement pattern (most shared primary muscles,
     * then the least grip, then by name). Hidden exercises and the slot's own are left out.
     */
    private fun candidates(slot: PlannedSlot, byId: Map<String, Exercise>, library: List<Exercise>, allow: (Exercise) -> Boolean): List<Exercise> {
        val current = byId[slot.exercise] ?: return emptyList()
        val alternatives = (listOfNotNull(slot.inPlaceOf) + slot.alternatives).distinct().mapNotNull { byId[it] }
            .filter { it.id != current.id && !it.hidden && allow(it) }
        val others = library.filter { it.pattern == current.pattern && it.id != current.id && !it.hidden && allow(it) && it !in alternatives }
            .sortedWith(compareByDescending<Exercise> { e -> e.muscles.count { it in current.muscles } }.thenBy { it.grip.ordinal }.thenBy { it.name })
        return alternatives + others
    }
}
