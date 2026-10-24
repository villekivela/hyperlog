package com.hyperlog.app.session

/**
 * The order sets are done in (SPEC, Supersets and session order): groups in the day's order, a
 * superset's partners alternating set for set (A1 → A2 → A1…). An exercise started from the
 * session list stays on screen, with its superset partner, until all of their planned sets are
 * logged; then the session goes back to the first group with sets left.
 */
internal object SessionOrder {
    /** What comes after a set closes: the exercise to put on screen, and whether rest runs first. */
    data class Step(val exerciseId: Long, val rest: Boolean)

    /**
     * The step after set [setIndex] of [closedId] closes. [exercises] are in session order; the
     * closed set may not be in their sets yet.
     */
    fun after(exercises: List<SessionExercise>, closedId: Long, setIndex: Int): Step {
        val closed = exercises.first { it.id == closedId }
        // An ad-hoc exercise's sets are open-ended: it stays until another is picked.
        if (closed.prescription == null) return Step(closedId, rest = true)
        val logged = exercises.associate { it.id to if (it.id == closedId) it.setIndices.toSet() + setIndex else it.setIndices.toSet() }
        fun next(group: List<SessionExercise>) = firstLeft(group, logged)
        val group = group(exercises, closed)
        next(group)?.let { (exercise, index) ->
            // Straight on to the partner's set of the same round; full rest once the round is done.
            return Step(exercise.id, rest = exercise.id == closedId || index != setIndex)
        }
        val later = exercises.asSequence().map { group(exercises, it) }.firstNotNullOfOrNull { next(it) }
        return Step(later?.first?.id ?: closedId, rest = true)
    }

    /** [exercise] and its superset partner, in session order; just [exercise] for straight sets. */
    fun group(exercises: List<SessionExercise>, exercise: SessionExercise): List<SessionExercise> {
        val slot = exercise.slot ?: return listOf(exercise)
        return exercises.filter { it.id == exercise.id || it.slot?.group == slot.group }
    }

    /** [exercise]'s superset partner in this session, or null for straight sets. */
    fun partner(exercises: List<SessionExercise>, exercise: SessionExercise): SessionExercise? =
        group(exercises, exercise).firstOrNull { it.id != exercise.id }

    /**
     * [exercises] as the places they move between in the session's order: a superset's partners
     * together, every other exercise on its own.
     */
    fun blocks(exercises: List<SessionExercise>): List<List<SessionExercise>> =
        exercises.fold(mutableListOf<MutableList<SessionExercise>>()) { blocks, e ->
            val last = blocks.lastOrNull()
            if (last != null && e.slot != null && last.first().slot?.group == e.slot.group) last.add(e) else blocks.add(mutableListOf(e))
            blocks
        }

    /** The group's first planned set not logged yet ([logged] holds each one's logged set indices), taking the partners set for set. */
    private fun firstLeft(group: List<SessionExercise>, logged: Map<Long, Set<Int>>): Pair<SessionExercise, Int>? {
        val rounds = group.maxOf { it.prescription?.size ?: 0 }
        for (i in 0 until rounds) {
            group.firstOrNull { it.prescription != null && i < it.prescription.size && i !in logged.getValue(it.id) }?.let { return it to i }
        }
        return null
    }
}
