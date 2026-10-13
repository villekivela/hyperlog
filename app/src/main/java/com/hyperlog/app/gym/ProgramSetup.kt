package com.hyperlog.app.gym

import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.Program
import com.hyperlog.app.session.namedFor

/**
 * What "Add the equipment your program uses" adds at a gym (SPEC, Gyms and equipment): a machine
 * for each of the program's machine exercises the gym has none for yet, and the cable exercises,
 * which share the gym's one cable [station] (a [newStation] when it has none).
 */
data class ProgramSetup(val machines: List<Exercise>, val cables: List<Exercise>, val station: EquipmentItem?) {
    val newStation: Boolean get() = cables.isNotEmpty() && station == null
    val isEmpty: Boolean get() = machines.isEmpty() && cables.isEmpty()
}

/**
 * The setup [program] needs at [gym]: its slots' exercises on machines and cables (not their
 * alternatives), leaving out those the gym remembers a machine for or has marked as not there.
 */
fun programSetup(program: Program, library: Map<String, Exercise>, gym: Gym): ProgramSetup {
    val exercises = program.blocks.flatMap { it.days }.flatMap { it.groups }.flatMap { it.slots }.map { it.exercise }.distinct()
        .mapNotNull { library[it] }
        .filter { it.id !in gym.machines && it.id !in gym.missing }
    return ProgramSetup(
        machines = exercises.filter { it.equipment == Equipment.Machine || it.equipment == Equipment.SmithMachine },
        cables = exercises.filter { it.equipment == Equipment.Cable },
        station = gym.items.firstOrNull { it.cableStation && !it.archived },
    )
}

/** A machine already at the gym for [exercise], by its name. */
internal fun Gym.machineFor(exercise: Exercise): EquipmentItem? = namedFor(exercise, items)
