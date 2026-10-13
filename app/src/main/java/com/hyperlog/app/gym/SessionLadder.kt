package com.hyperlog.app.gym

import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.library.Equipment

/** The ladder a session uses for an exercise, and whether it is the real equipment's. */
data class SessionLadder(val ladder: WeightLadder, val known: Boolean)

/**
 * Dumbbell exercises use the shared dumbbell ladder and barbell exercises the shared plates; the
 * EZ, trap and safety bars the shared plates on their own bar. Machines and cables use their
 * [machine]'s ladder once the session knows it. Everything else, and a machine not picked yet,
 * steps in 2.5 kg and isn't checked against a ladder.
 */
fun SharedLadders.ladderFor(equipment: Equipment, machine: EquipmentItem? = null): SessionLadder = when {
    equipment == Equipment.Dumbbell -> SessionLadder(dumbbells.ladder(), known = true)
    equipment == Equipment.Barbell -> SessionLadder(plates.ladder(), known = true)
    equipment in bars -> SessionLadder(barPlates(equipment)!!.ladder(), known = true)
    equipment.perMachine && machine != null -> SessionLadder(machine.ladder.ladder(), known = true)
    else -> SessionLadder(WeightLadder.Default, known = false)
}
