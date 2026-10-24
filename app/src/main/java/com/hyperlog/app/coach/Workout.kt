package com.hyperlog.app.coach

import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Day
import com.hyperlog.app.program.Group
import com.hyperlog.app.program.PlannedDay
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.program.SetSpec
import com.hyperlog.app.program.Slot
import com.hyperlog.app.program.TechniqueJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A day Claude builds for one date, outside the program (SPEC, Coaching → Workout; GLOSSARY):
 * its name, why it looks the way it does, and a program day's groups, slots and set lines without
 * week overrides. Training it doesn't move the program's position. [ProgramParser.workout] reads
 * it; [WorkoutJson] writes it.
 */
data class Workout(val name: String, val summary: String, val groups: List<Group>) {
    /** The workout as a day to train, read like a program day: labels `1`, `2a`/`2b`, its sets expanded. */
    fun day(): PlannedDay = Program(name, null, listOf(Block(BLOCK, name, 1, 1, listOf(Day(BLOCK, name, groups)))))
        .plannedDay(Position(1, 0))!!

    companion object {
        /** The id of the one block and day a workout reads as. */
        const val BLOCK = "workout"

        /** Reads a stored workout, already checked when it was added: library exercises are hidden, never deleted. */
        fun stored(json: String): Workout? = (ProgramParser.workout(json, AnyId) as? ProgramParser.WorkoutResult.Valid)?.workout
    }
}

/** Accepts every exercise id, for workouts already checked. */
private object AnyId : Set<String> by emptySet() {
    override fun contains(element: String) = true
}

/** Writes a workout as SPEC's JSON, `{ "workout": { name, summary, groups } }`, which [ProgramParser.workout] reads back. */
object WorkoutJson {
    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun encode(workout: Workout): String = element(workout).toString()

    /** Indented, for the prompt. */
    fun pretty(workout: Workout): String = pretty.encodeToString(JsonObject.serializer(), element(workout))

    private fun element(workout: Workout): JsonObject = buildJsonObject {
        putJsonObject("workout") {
            put("name", workout.name)
            put("summary", workout.summary)
            putJsonArray("groups") {
                workout.groups.forEach { group ->
                    addJsonObject {
                        if (group.superset) put("type", "superset")
                        putJsonArray("slots") { group.slots.forEach { add(slot(it)) } }
                    }
                }
            }
        }
    }

    private fun slot(slot: Slot) = buildJsonObject {
        put("id", slot.id)
        put("exercise", slot.exercise)
        if (slot.alternatives.isNotEmpty()) putJsonArray("alternatives") {
            slot.alternatives.forEach { a ->
                if (a.sets == null) add(kotlinx.serialization.json.JsonPrimitive(a.exercise))
                else addJsonObject { put("exercise", a.exercise); putJsonArray("sets") { a.sets.forEach { add(set(it)) } } }
            }
        }
        slot.note?.let { put("note", it) }
        putJsonArray("sets") { slot.sets.forEach { add(set(it)) } }
    }

    private fun set(set: SetSpec) = buildJsonObject {
        if (set.count != 1) put("count", set.count)
        if (set.reps.min == set.reps.max) put("reps", set.reps.min) else put("reps", "${set.reps.min}-${set.reps.max}")
        set.rir?.let { put("rir", it) }
        put("rest", set.rest)
        set.technique?.let { put("technique", Json.parseToJsonElement(TechniqueJson.encode(it))) }
        set.backoff?.let { b -> putJsonObject("backoff") { put("from", b.from); put("percent", b.percent) } }
    }
}

/**
 * The workout format as the prompt states it. The example is built from the library the prompt
 * lists and written by [WorkoutJson], so the paste accepts it.
 */
object WorkoutFormat {
    /** A set line, as a workout and a program file write it. */
    val SET_LINE = "A set line is {\"count\"?: 1 to 10, \"reps\": 8 or \"8-10\", \"rir\"?: 0 to 10, \"rest\"?: seconds, 0 to 600 (120 when left out), " +
        "\"technique\"?: {\"kind\": ${com.hyperlog.app.program.TechniqueKind.keys.joinToString { "\"$it\"" }}, ...}, " +
        "\"backoff\"?: {\"from\": an earlier line's index, \"percent\": 1 to 50}}."

    fun text(library: List<Exercise>): String = buildString {
        appendLine("## Workout format")
        appendLine("A workout is one day for today, outside the program: Hyperlog shows it on Today, and training it doesn't move the program on.")
        appendLine("Reply with one fenced json code block holding {\"workout\": {...}}:")
        appendLine("- \"name\": a short name for the day (\"Hotel dumbbells, 45 min\").")
        appendLine("- \"summary\": one or two sentences on what it trains and why.")
        appendLine(
            "- \"groups\": the exercises in order, as in a Hyperlog program file. A group is {\"type\"?: \"straight\" or \"superset\", \"slots\": [...]}: " +
                "straight has 1 slot, a superset 2, done alternately.",
        )
        appendLine(
            "  A slot is {\"id\": lowercase letters, digits and _, unique in the workout, \"exercise\": a library id, \"alternatives\"?: [ids], " +
                "\"note\"?: a cue, \"sets\": [set lines]}.",
        )
        appendLine("  $SET_LINE")
        appendLine("  No week overrides and no weights: Hyperlog suggests the weights from my history.")
        appendLine("Unknown fields or exercises are rejected.")
        appendLine()
        appendLine("For example:")
        appendLine("```json")
        appendLine(WorkoutJson.pretty(example(library)))
        appendLine("```")
    }

    /**
     * A short workout from the visible library: a row, then a press and a curl as a superset, so
     * the example names real ids and shows both kinds of group.
     */
    fun example(library: List<Exercise>): Workout {
        val visible = library.filter { !it.hidden }.sortedBy { it.id }
        fun pick(pattern: MovementPattern) = visible.firstOrNull { it.pattern == pattern } ?: visible.first()
        val row = pick(MovementPattern.HorizontalPull)
        val press = pick(MovementPattern.HorizontalPush)
        val curl = pick(MovementPattern.ElbowFlexion)
        return Workout(
            name = "Upper body, 45 min",
            summary = "A row first, then a press and a curl as a superset to keep it short.",
            groups = listOf(
                Group(false, listOf(Slot("row", row.id, emptyList(), null, listOf(SetSpec(3, RepRange(8, 12), rir = 1, rest = 120))))),
                Group(
                    true,
                    listOf(
                        Slot("press", press.id, emptyList(), null, listOf(SetSpec(2, RepRange(8, 10), rir = 2, rest = 90))),
                        Slot("curl", curl.id, emptyList(), null, listOf(SetSpec(2, RepRange(10, 12), rir = 1, rest = 90))),
                    ),
                ),
            ),
        )
    }
}
