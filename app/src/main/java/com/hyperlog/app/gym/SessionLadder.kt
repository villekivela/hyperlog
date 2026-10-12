package com.hyperlog.app.gym

import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.library.Equipment

/** The ladder a session uses for an exercise, and whether it is the real equipment's. */
data class SessionLadder(val ladder: WeightLadder, val known: Boolean)

/**
 * Dumbbell exercises use the shared dumbbell ladder and barbell exercises the shared plates.
 * Until a session knows its gym and machine (#5), everything else steps in 2.5 kg and isn't
 * checked against a ladder.
 */
fun SharedLadders.ladderFor(equipment: Equipment): SessionLadder = when (equipment) {
    Equipment.Dumbbell -> SessionLadder(dumbbells.ladder(), known = true)
    Equipment.Barbell -> SessionLadder(plates.ladder(), known = true)
    else -> SessionLadder(WeightLadder.Default, known = false)
}
