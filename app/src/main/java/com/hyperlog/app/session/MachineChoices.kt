package com.hyperlog.app.session

import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.library.Exercise

// "Which machine?" (SPEC, Gyms and equipment): which of the gym's machines to offer first, and the
// one taken without asking.

/** Words that don't tell machines apart. */
private val Filler = setOf("machine", "seated", "lying", "standing", "the", "and", "with")

private fun words(name: String): Set<String> =
    name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in Filler }.toSet()

/**
 * The gym's machines for [exercise]: first the ones of its kind (plate-loaded for a plate-loaded
 * exercise, else the rest), those sharing a word with its name at the top; then every other one.
 */
fun machineChoices(exercise: Exercise, items: List<EquipmentItem>): Pair<List<EquipmentItem>, List<EquipmentItem>> {
    val (fit, other) = items.filter { !it.archived }.partition { (it.ladder is LadderSpec.Plates) == exercise.plateLoaded }
    val named = words(exercise.name)
    return fit.sortedByDescending { (words(it.name) intersect named).isNotEmpty() } to other
}

/** A machine named for the exercise ("Leg extension" for Leg Extension): it is taken without asking. */
fun namedFor(exercise: Exercise, items: List<EquipmentItem>): EquipmentItem? {
    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    return items.firstOrNull { !it.archived && norm(it.name) == norm(exercise.name) }
}
