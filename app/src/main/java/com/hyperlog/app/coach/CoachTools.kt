package com.hyperlog.app.coach

import com.hyperlog.app.program.HoldPosition
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.TechniqueKind
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The tools a check-in's Claude replies through (SPEC, Coaching → What Claude is told): `ask` for
 * a question, `propose_change` and `propose_workout` for a reply. Only `ask` is `strict`: the API
 * refused the proposals' strict grammar as too large, so their input is checked by the validators,
 * and the self-fix loop sends problems back. A Change's operations come from [Operation]'s serializer and their
 * descriptions from [ChangeFormat], so a new operation or field fails here until it is described.
 * The validators still check ranges and ids, which a strict schema can't hold.
 */
object CoachTools {
    const val ASK = "ask"
    const val PROPOSE_CHANGE = "propose_change"
    const val PROPOSE_WORKOUT = "propose_workout"

    /** Every tool; with no program running ([program] null) there is no plan to change, so no `propose_change`. */
    fun all(program: Program?): List<CoachTool> = listOfNotNull(ask(), program?.let(::proposeChange), proposeWorkout())

    fun ask() = CoachTool(
        ASK,
        "Ask me one question, shown on its own screen with 2 to 4 short answers to tap. Ask only what the situation needs, " +
            "one question at a time, and at most four in a check-in.",
        obj(
            "question" to string("The question, one short sentence."),
            "answers" to array(string("An answer to tap, a few words."), "2 to 4 answers."),
            "own" to bool("Whether I may type my own answer instead."),
        ),
    )

    fun proposeChange(program: Program) = CoachTool(
        PROPOSE_CHANGE,
        "Propose one Change to my program: a summary, the sessions it covers and its operations. Hyperlog checks it and shows " +
            "me its diff; nothing changes until I accept. Slot edits follow a slot id into later blocks that reuse it; position " +
            "operations (skip_day, shift_program, repeat_week) run once, when I accept. No operation reorders exercises. If the " +
            "result lists problems, fix them and propose again.",
        obj(
            "summary" to string("One short sentence saying what changes and why."),
            "scope" to buildJsonObject {
                put("description", "The sessions the plan edits cover: dates (temporary, both included), \"rest_of_block\" (to the end of the block I'm in) or \"rest_of_program\" (from today on).")
                putJsonArray("anyOf") {
                    addJsonObject {
                        put("type", "string")
                        putJsonArray("enum") { add("rest_of_program"); add("rest_of_block") }
                    }
                    add(obj("from" to date("The first date covered."), "to" to date("The last date covered.")))
                }
            },
            "operations" to array(
                buildJsonObject { putJsonArray("anyOf") { operations(program).forEach { add(it) } } },
                "Applied in order. May be empty only with replaces.",
            ),
            "replaces" to array(integer("The id of a Change in force."), "Ids of Changes in force this one ends: accepting reverts them first."),
            optional = setOf("replaces"),
        ),
        strict = false,
    )

    fun proposeWorkout() = CoachTool(
        PROPOSE_WORKOUT,
        "Propose one workout: a day for today outside the program, which Hyperlog shows on Today. Training it doesn't move the " +
            "program on. No weights: Hyperlog suggests them from my history. If the result lists problems, fix them and propose again.",
        obj(
            "name" to string("A short name for the day (\"Hotel dumbbells, 45 min\")."),
            "summary" to string("One or two sentences on what it trains and why."),
            "groups" to array(
                obj(
                    "type" to buildJsonObject {
                        put("type", "string")
                        putJsonArray("enum") { add("straight"); add("superset") }
                        put("description", "straight has 1 slot; a superset 2, done alternately.")
                    },
                    "slots" to array(
                        obj(
                            "id" to string("Lowercase letters, digits and _, unique in the workout."),
                            "exercise" to string("A library exercise id."),
                            "note" to string("A cue for the slot."),
                            "sets" to array(setLine(technique = true), "The set lines, in order: work sets only."),
                            optional = setOf("note"),
                        ),
                        "1 slot, or 2 for a superset.",
                    ),
                ),
                "The exercises in order.",
            ),
        ),
        strict = false,
    )

    /** One schema for each operation, from its serializer: `op` and its fields, `reason` always. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun operations(program: Program): List<JsonObject> {
        val subclasses = Operation.serializer().descriptor.getElementDescriptor(1).elementDescriptors.associateBy { it.serialName }
        return ChangeFormat.operations().map { (op, _) ->
            val d = subclasses.getValue(op)
            val fields = (0 until d.elementsCount).map { i ->
                val name = d.getElementName(i)
                val schema = field(op, name, d.getElementDescriptor(i))
                Triple(name, schema, d.isElementOptional(i) && name in Optional)
            }
            obj(
                listOf("op" to buildJsonObject { put("type", "string"); put("const", op); put("description", ChangeFormat.description(op, program)) }) +
                    fields.map { it.first to it.second },
                optional = fields.filter { it.third }.map { it.first }.toSet(),
            )
        }
    }

    /**
     * The fields a schema leaves optional, as the serializer does; every other is required, since
     * a strict request allows few optional fields. `reason` is one Claude always gives.
     */
    private val Optional = setOf("slot", "technique", "week", "after")

    /** What each operation field is, by name; one not described fails. */
    private val Fields = mapOf(
        "slot" to "A slot id from the program.",
        "exercise" to "A library exercise id.",
        "day" to "A day id from the program.",
        "after" to "The slot id on that day to add it after; the end when left out.",
        "sets" to "",
        "percent" to "Percent, not 0: −10 is 10% lighter.",
        "by" to "Reps in reserve to add, not 0: +1 is one more in reserve.",
        "technique" to "The technique to remove (${TechniqueKind.keys.joinToString()}); any when left out.",
        "week" to "The program week; the one I'm in when left out.",
        "weeks" to "Weeks, not 0: positive starts the rest later, negative earlier.",
        "reason" to "Why, in a few words.",
    )

    @OptIn(ExperimentalSerializationApi::class)
    private fun field(op: String, name: String, d: SerialDescriptor): JsonObject {
        val description = Fields[name] ?: error("No tool description for field \"$name\" of operation \"$op\"")
        return when {
            // add's sets are set lines; adjust_sets' is a count.
            d.kind == StructureKind.LIST -> array(setLine(technique = false), "The set lines, in order.")
            name == "sets" -> integer("How many sets, 1 to ${ChangeValidator.MAX_SETS}.")
            d.kind == PrimitiveKind.INT || d.kind == PrimitiveKind.LONG -> integer(description)
            d.kind == PrimitiveKind.STRING -> string(description)
            else -> error("No tool schema for field \"$name\" of operation \"$op\" (${d.kind})")
        }
    }

    /** A program file's set line: every field given, and with [technique] an intensity technique on it. */
    private fun setLine(technique: Boolean) = obj(
        listOf(
            "count" to integer("Sets on this line, 1 to ${ChangeValidator.MAX_SETS}."),
            "reps" to buildJsonObject {
                put("description", "Reps: a number (8) or a range (\"8-10\").")
                putJsonArray("anyOf") { add(integer("")); add(string("")) }
            },
            "rir" to integer("Target reps in reserve, 0 to ${ChangeValidator.MAX_RIR}."),
            "rest" to integer("Rest after each set, in seconds."),
        ) + if (technique) listOf("technique" to techniques()) else emptyList(),
        optional = setOf("technique"),
    )

    private fun techniques() = buildJsonObject {
        put("description", "An intensity technique on these sets' last set.")
        putJsonArray("anyOf") {
            TechniqueKind.entries.forEach { kind ->
                val fields = listOf("kind" to buildJsonObject { put("type", "string"); put("const", kind.key) }) + when (kind) {
                    TechniqueKind.Dropset -> listOf("drops" to integer("Drops, 1 to 3."), "percent" to integer("Weight off each drop, in percent."))
                    TechniqueKind.MyoReps -> listOf("miniSetReps" to integer("Reps in each mini-set."))
                    TechniqueKind.LengthenedPartials -> listOf("partials" to integer("Partial reps after the full ones."))
                    TechniqueKind.IsoHold -> listOf(
                        "seconds" to integer("Seconds held."),
                        "position" to buildJsonObject {
                            put("type", "string")
                            putJsonArray("enum") { HoldPosition.entries.forEach { add(it.key) } }
                        },
                    )
                    TechniqueKind.Failure -> emptyList()
                }
                add(obj(fields))
            }
        }
    }

    private fun obj(vararg properties: Pair<String, JsonObject>, optional: Set<String> = emptySet()) = obj(properties.toList(), optional)

    private fun obj(properties: List<Pair<String, JsonObject>>, optional: Set<String> = emptySet()) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
        put("required", JsonArray(properties.map { it.first }.filter { it !in optional }.map(::JsonPrimitive)))
        put("additionalProperties", false)
    }

    private fun typed(type: String, description: String, more: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("type", type)
        if (description.isNotEmpty()) put("description", description)
        more()
    }

    private fun string(description: String) = typed("string", description)
    private fun date(description: String) = typed("string", description) { put("format", "date") }
    private fun integer(description: String) = typed("integer", description)
    private fun bool(description: String) = typed("boolean", description)
    private fun array(items: JsonObject, description: String) = typed("array", description) { put("items", items) }
}
