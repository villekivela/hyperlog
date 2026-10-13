package com.hyperlog.app.gym

import com.hyperlog.app.domain.WeightLadder
import com.hyperlog.app.domain.withPlates
import com.hyperlog.app.library.Equipment

/** The ladder a session uses for an exercise, and whether it is the real equipment's. */
data class SessionLadder(val ladder: WeightLadder, val known: Boolean)

/**
 * Dumbbell exercises use the gym's dumbbell rack and barbell exercises the gym's plates; the EZ,
 * trap and safety bars the same plates on their own bar. Machines and cables use their
 * [machine]'s ladder once the session knows it, a plate-loaded one with no pairs of its own the
 * gym's plates. Everything else, and a machine not picked yet,
 * steps in 2.5 kg and isn't checked against a ladder. A stack takes the gym's [pinPlates].
 */
fun SharedLadders.ladderFor(equipment: Equipment, machine: EquipmentItem? = null, pinPlates: List<Double> = emptyList()): SessionLadder = when {
    equipment == Equipment.Dumbbell -> SessionLadder(dumbbells.ladder(), known = true)
    equipment == Equipment.Barbell -> SessionLadder(plates.ladder(), known = true)
    equipment in bars -> SessionLadder(barPlates(equipment)!!.ladder(), known = true)
    // A guessed ladder steps −/+ but isn't checked: typed weights are kept as typed.
    equipment.perMachine && machine != null -> SessionLadder(machine.ladder.withPlates(plates.pairs).ladder(pinPlates), known = machine.state == LadderState.Confirmed)
    else -> SessionLadder(WeightLadder.Default, known = false)
}
