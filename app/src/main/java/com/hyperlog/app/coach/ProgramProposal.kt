package com.hyperlog.app.coach

import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.library.Muscles
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.ProgramParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A new program from Claude (SPEC, Coaching → New program): the program file, each swap it made
 * from an exercise the file named to the closest library one, and the exercises it adds to the
 * library because nothing came close. Start program imports [source] and saves [newExercises].
 */
data class ProgramProposal(val program: Program, val source: String, val swaps: List<Swap>, val newExercises: List<Exercise>) {
    /** The file's exercise [from], by its name there, done as library exercise [to]. */
    data class Swap(val from: String, val to: String)

    sealed interface Result {
        data class Valid(val proposal: ProgramProposal) : Result
        data class Invalid(val errors: List<String>) : Result
    }

    companion object {
        /** The fields of a reply: `propose_program`'s input, or a pasted `{ "program": … }`. */
        val Fields = listOf("program", "swaps", "newExercises")

        /** The fields of a new exercise, as the library editor asks for them. */
        val ExerciseFields = listOf("id", "name", "equipment", "pattern", "grip", "muscles", "secondary")

        /**
         * Reads [input], with every problem's path: the program as import checks it (under
         * `program.`, its exercises from the library's [exercises] and the new ones), each swap,
         * and each new exercise by the library editor's rules. A new exercise must be new to the
         * library, by id and by name ([names]), and used by the program.
         */
        fun read(input: JsonObject, exercises: Set<String>, names: Map<String, String>, merged: Map<String, String> = emptyMap()): Result {
            val errors = mutableListOf<String>()
            (input.keys - Fields.toSet()).forEach { errors += "$it: unknown field" }
            val entries = list(input, "newExercises", errors)
            val added = entries.mapIndexedNotNull { i, el -> exercise(el, "newExercises[$i]", exercises, names, errors) }
            // The ids the reply adds, read again from the JSON, so the program is checked against
            // them even when an exercise has problems of its own.
            val declared = entries.map { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .map { id -> id?.takeIf { Id.matches(it) && it !in exercises } }
            declared.forEachIndexed { i, id -> if (id != null && declared.indexOf(id) < i) errors += "newExercises[$i].id: \"$id\" is added twice" }
            val swaps = list(input, "swaps", errors).mapIndexedNotNull { i, el -> swap(el, "swaps[$i]", exercises, errors) }
            val file = input["program"]
            if (file == null) {
                errors += "program: is required"
                return Result.Invalid(errors)
            }
            val program = when (val parsed = ProgramParser.parse(file.toString(), exercises + declared.filterNotNull(), merged)) {
                is ProgramParser.Result.Valid -> parsed.program
                is ProgramParser.Result.Invalid -> null.also { errors += parsed.errors.map { "program.$it" } }
            }
            if (program != null) {
                val used = program.exercises()
                declared.forEachIndexed { i, id -> if (id != null && id !in used) errors += "newExercises[$i]: the program doesn't use \"$id\"" }
            }
            if (errors.isNotEmpty() || program == null) return Result.Invalid(errors)
            return Result.Valid(ProgramProposal(program, file.toString(), swaps, added))
        }

        private fun list(o: JsonObject, key: String, errors: MutableList<String>): List<JsonElement> = when (val el = o[key]) {
            null -> emptyList()
            is JsonArray -> el
            else -> emptyList<JsonElement>().also { errors += "$key: must be a list" }
        }

        private fun swap(el: JsonElement, path: String, exercises: Set<String>, errors: MutableList<String>): Swap? {
            val o = obj(el, path, listOf("from", "to"), errors) ?: return null
            val from = text(o, path, "from", errors)
            val to = text(o, path, "to", errors)
            if (to != null && to !in exercises) errors += "$path.to: unknown exercise \"$to\""
            return if (from != null && to != null && to in exercises) Swap(from, to) else null
        }

        private fun exercise(el: JsonElement, path: String, exercises: Set<String>, names: Map<String, String>, errors: MutableList<String>): Exercise? {
            val o = obj(el, path, ExerciseFields, errors) ?: return null
            val before = errors.size
            val id = text(o, path, "id", errors)?.also { id ->
                when {
                    !Id.matches(id) -> errors += "$path.id: lowercase letters, digits and _ only"
                    id in exercises -> errors += "$path.id: the library already has \"$id\"${names[id]?.let { " ($it)" }.orEmpty()}: use it"
                }
            }
            val name = text(o, path, "name", errors)?.trim()?.also { name ->
                // An id the library has already says so; its name would say it twice.
                if (id == null || id !in exercises) names.entries.firstOrNull { it.value.equals(name, ignoreCase = true) }?.let { (known, kept) ->
                    errors += "$path.name: the library already has \"$kept\" ($known): use it"
                }
            }
            val equipment = key(o, path, "equipment", Equipment.entries.map { it.key }, errors)?.let(Equipment::ofKey)
            val pattern = key(o, path, "pattern", MovementPattern.entries.map { it.key }, errors)?.let(MovementPattern::ofKey)
            val grip = key(o, path, "grip", GripDemand.entries.map { it.key }, errors)?.let(GripDemand::ofKey)
            val muscles = muscles(o, path, "muscles", errors)
            if (muscles != null && muscles.isEmpty()) errors += "$path.muscles: name at least one primary muscle"
            val secondary = if (o["secondary"] == null) emptyList() else muscles(o, path, "secondary", errors)
            if (errors.size > before || id == null || name == null || equipment == null || pattern == null || grip == null || muscles == null || secondary == null) return null
            return Exercise(id, name, equipment, pattern, grip, muscles, secondary)
        }

        private fun obj(el: JsonElement, path: String, fields: List<String>, errors: MutableList<String>): JsonObject? {
            val o = el as? JsonObject ?: return null.also { errors += "$path: must be an object" }
            (o.keys - fields.toSet()).forEach { errors += "$path.$it: unknown field" }
            return o
        }

        private fun text(o: JsonObject, path: String, key: String, errors: MutableList<String>): String? {
            val el = o[key] ?: return null.also { errors += "$path.$key: is required" }
            val value = (el as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null.also { errors += "$path.$key: must be text" }
            return value.takeIf { it.isNotBlank() } ?: null.also { errors += "$path.$key: must not be blank" }
        }

        private fun key(o: JsonObject, path: String, key: String, keys: List<String>, errors: MutableList<String>): String? {
            val value = text(o, path, key, errors) ?: return null
            return value.takeIf { it in keys } ?: null.also { errors += "$path.$key: must be one of ${keys.joinToString()}, not \"$value\"" }
        }

        private fun muscles(o: JsonObject, path: String, key: String, errors: MutableList<String>): List<String>? {
            val list = o[key] as? JsonArray ?: return null.also { errors += "$path.$key: ${if (o[key] == null) "is required" else "must be a list of muscles"}" }
            val muscles = list.mapIndexed { i, el ->
                val muscle = (el as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (muscle == null || muscle !in Muscles) errors += "$path.$key[$i]: unknown muscle ${if (muscle == null) el.toString() else "\"$muscle\""}"
                muscle
            }
            return muscles.takeIf { m -> m.all { it != null && it in Muscles } }?.filterNotNull()
        }

        private val Id = Regex("[a-z0-9_]+")
    }
}

/** Every exercise a program names: each slot's and its alternatives'. */
fun Program.exercises(): Set<String> =
    blocks.flatMap { b -> b.days.flatMap { d -> d.groups.flatMap { g -> g.slots.flatMap { s -> listOf(s.exercise) + s.alternatives.map { it.exercise } } } } }.toSet()
