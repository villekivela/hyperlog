package com.hyperlog.app.coach

import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.library.Muscles
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.TechniqueKind
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The new program format as Ask Claude's prompt states it (SPEC, What Claude is told): a program
 * file (docs/program-format.md) as [ProgramParser] reads it, with the swaps and new exercises of
 * [ProgramProposal]. The example is built from the library the prompt lists, so the parser takes it.
 */
object ProgramFormat {
    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun text(library: List<Exercise>): String = buildString {
        appendLine("## Program format")
        appendLine(
            "A program replaces the one I run, from week 1, day 1; my past sessions are kept. Reply with one fenced json code block " +
                "holding {\"program\": {...}, \"swaps\": [...], \"newExercises\": [...]}:",
        )
        appendLine("- \"program\": a Hyperlog program file:")
        appendLine("  {\"format\": ${ProgramParser.FORMAT}, \"title\", \"author\"?, \"description\"?, \"blocks\": [...]}.")
        appendLine(
            "  A block is a run of weeks that train the same days: {\"id\", \"name\", \"weekStart\", \"weekEnd\", \"weeks\"?, \"days\": [...]}. " +
                "The first block starts in week 1 and each starts the week after the one before.",
        )
        appendLine("  A day is {\"id\" (unique in the block), \"name\" (\"Day A — Heavy bench, squat\"), \"weeks\"?, \"groups\": [...]}; the days are trained in order, once a week each.")
        appendLine(
            "  A group is {\"type\"?: \"straight\" or \"superset\", \"slots\": [...]}: straight has 1 slot, a superset 2, done alternately.",
        )
        appendLine(
            "  A slot is {\"id\": lowercase letters, digits and _, unique in the block (reuse it for the same place in other blocks), " +
                "\"exercise\": a library id, \"alternatives\"?: [ids], \"note\"?: the program's cue, \"sets\": [set lines]}.",
        )
        appendLine("  ${WorkoutFormat.SET_LINE} A set line may also carry \"weeks\"?.")
        appendLine(
            "  \"weeks\" holds week overrides, keyed by a week (\"3\") or a range (\"7-11\") inside the block: on a set line " +
                "{\"count\"? (0 removes the line), \"reps\"?, \"rir\"?, \"rest\"?, \"technique\"? (null removes it)}; on a day or a block, for every " +
                "set in it, {\"rir\"? or \"rirAdd\"? (−5 to 5), \"technique\": null?, \"fewerSets\"?, \"deload\": true?}.",
        )
        appendLine("  No weights: Hyperlog suggests them from my history. Warm-ups aren't written; every set line is work sets.")
        appendLine(
            "- \"swaps\": for each exercise the source names that the library lacks, the closest library exercise used instead: " +
                "{\"from\": its name in the source, \"to\": the library id}. Empty when there were none.",
        )
        appendLine(
            "- \"newExercises\": only when nothing in the library comes close, the exercise to add: {\"id\": lowercase letters, digits and _, " +
                "new to the library, \"name\", \"equipment\": ${keys(Equipment.entries.map { it.key })}, \"pattern\": ${keys(MovementPattern.entries.map { it.key })}, " +
                "\"grip\": ${keys(GripDemand.entries.map { it.key })}, \"muscles\": [primary muscles, at least one], \"secondary\": [muscles]}. " +
                "Muscles are ${Muscles.joinToString()}. Usually empty.",
        )
        appendLine("Unknown fields, exercises or muscles are rejected.")
        appendLine()
        appendLine("For example:")
        appendLine("```json")
        appendLine(
            pretty.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("program", example(library))
                    putJsonArray("swaps") {}
                    putJsonArray("newExercises") {}
                },
            ),
        )
        appendLine("```")
    }

    private fun keys(keys: List<String>) = keys.joinToString(" | ") { "\"$it\"" }

    /**
     * Four weeks of two days from the visible library: a ramp-in week a rep easier with no
     * techniques, then a block whose last week is a deload; a squat with back-offs, a press and a
     * row as a superset, a hinge, and a curl with myo-reps on its last set. The deload's top squat
     * set has fewer reps, a set line's own week override.
     */
    fun example(library: List<Exercise>): JsonObject {
        val visible = library.filter { !it.hidden }.sortedBy { it.id }
        fun pick(pattern: MovementPattern) = (visible.firstOrNull { it.pattern == pattern } ?: visible.first()).id
        fun JsonObjectBuilder.days(deload: Int?) = putJsonArray("days") {
            addJsonObject {
                put("id", "a")
                put("name", "Day A — Squat, press, row")
                putJsonArray("groups") {
                    addJsonObject { putJsonArray("slots") { add(slot("squat", pick(MovementPattern.Squat), line(1, "4-6", 1, 180, deload = deload), line(2, "6-8", 1, 180, backoff = true))) } }
                    addJsonObject {
                        put("type", "superset")
                        putJsonArray("slots") {
                            add(slot("press", pick(MovementPattern.HorizontalPush), line(3, "8-10", 1, 90)))
                            add(slot("row", pick(MovementPattern.HorizontalPull), line(3, "8-10", 1, 90)))
                        }
                    }
                }
            }
            addJsonObject {
                put("id", "b")
                put("name", "Day B — Hinge, curl")
                putJsonArray("groups") {
                    addJsonObject { putJsonArray("slots") { add(slot("hinge", pick(MovementPattern.Hinge), line(3, "6-8", 2, 180))) } }
                    addJsonObject {
                        putJsonArray("slots") {
                            add(slot("curl", pick(MovementPattern.ElbowFlexion), line(1, "10-12", 0, 60), line(1, "10-12", 0, 60, myo = true)))
                        }
                    }
                }
            }
        }
        return buildJsonObject {
            put("format", ProgramParser.FORMAT)
            put("title", "Full body, 2 days")
            putJsonArray("blocks") {
                addJsonObject {
                    put("id", "ramp_in")
                    put("name", "Ramp-in")
                    put("weekStart", 1)
                    put("weekEnd", 1)
                    putJsonObject("weeks") { putJsonObject("1") { put("rirAdd", 1); put("technique", JsonNull) } }
                    days(null)
                }
                addJsonObject {
                    put("id", "build")
                    put("name", "Build")
                    put("weekStart", 2)
                    put("weekEnd", 4)
                    putJsonObject("weeks") { putJsonObject("4") { put("fewerSets", 1); put("rirAdd", 2); put("technique", JsonNull); put("deload", true) } }
                    days(4)
                }
            }
        }
    }

    private fun slot(id: String, exercise: String, vararg sets: JsonObject) = buildJsonObject {
        put("id", id)
        put("exercise", exercise)
        putJsonArray("sets") { sets.forEach { add(it) } }
    }

    private fun line(count: Int, reps: String, rir: Int, rest: Int, backoff: Boolean = false, myo: Boolean = false, deload: Int? = null) = buildJsonObject {
        put("count", count)
        put("reps", reps)
        put("rir", rir)
        put("rest", rest)
        if (myo) putJsonObject("technique") { put("kind", TechniqueKind.MyoReps.key); put("miniSetReps", 4) }
        if (backoff) putJsonObject("backoff") { put("from", 0); put("percent", 10) }
        deload?.let { week -> putJsonObject("weeks") { putJsonObject("$week") { put("reps", "3-5") } } }
    }
}
