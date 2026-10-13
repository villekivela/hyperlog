package com.hyperlog.app.program

/** A run of sets written as one, e.g. `2 × 6–8 @ 1` with its techniques' symbols after it. */
data class PlanChunk(
    val count: Int,
    val reps: RepRange,
    val rir: Int?,
    val backoff: Boolean,
    val techniques: List<Technique> = emptyList(),
)

/**
 * Sets as they are summed up on Today and in the session list: neighbours with the same reps, RIR
 * and back-off count together, keeping their techniques (`2 × 10–12 @ 0` with myo-reps on the last).
 */
fun planChunks(sets: List<SetPrescription>): List<PlanChunk> = buildList {
    sets.forEach { set ->
        val last = lastOrNull()
        val backoff = set.backoff != null
        if (last != null && last.reps == set.reps && last.rir == set.rir && last.backoff == backoff) {
            set(size - 1, last.copy(count = last.count + 1, techniques = last.techniques + listOfNotNull(set.technique)))
        } else {
            add(PlanChunk(1, set.reps, set.rir, backoff, listOfNotNull(set.technique)))
        }
    }
}
