package com.hyperlog.app.coach

import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.ProgramParser
import com.hyperlog.app.program.StarterProgram
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The check-in's tools (SPEC, What Claude is told): strict schemas within the API's limits, which
 * hold every reply the validators take.
 */
class CoachToolsTest {
    private val library = LibrarySeed.parse(File("src/main/assets/exercises.json").readText())
    private val ids = library.exercises.map { it.id }.toSet()
    private val program = (ProgramParser.parse(File("src/main/assets/${StarterProgram.ASSET}").readText(), ids, library.merged) as ProgramParser.Result.Valid).program
    private val tools = CoachTools.all(program)

    /** Every schema object in [schema], itself included. */
    private fun objects(schema: JsonElement): List<JsonObject> = when (schema) {
        is JsonObject -> listOf(schema) + schema.values.flatMap(::objects)
        is JsonArray -> schema.flatMap(::objects)
        else -> emptyList()
    }

    /** The properties of every object schema, with whether each is required. */
    private fun properties(schema: JsonObject) = objects(schema).filter { (it["type"] as? JsonPrimitive)?.contentOrNull == "object" }.flatMap { o ->
        val required = o.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet()
        o.getValue("properties").jsonObject.map { (name, value) -> Triple(name, value.jsonObject, name in required) }
    }

    @Test
    fun onlyAskIsStrict() {
        // Strict proposals compiled to a grammar the API refused ("too large"); the validators and
        // the self-fix loop check them instead.
        assertEquals(listOf(CoachTools.ASK), tools.filter { it.strict }.map { it.name })
    }

    @Test
    fun theSchemasAreStrictAndWithinTheApisLimits() {
        assertEquals(listOf("ask", "propose_change", "propose_workout"), tools.map { it.name })
        tools.forEach { tool ->
            objects(tool.inputSchema).filter { (it["type"] as? JsonPrimitive)?.contentOrNull == "object" }.forEach { o ->
                assertEquals(tool.name, JsonPrimitive(false), o["additionalProperties"])
                val names = o.getValue("properties").jsonObject.keys
                o.getValue("required").jsonArray.forEach { assertTrue(tool.name, it.jsonPrimitive.content in names) }
            }
            // Keywords a strict schema refuses.
            val keys = objects(tool.inputSchema).flatMap { it.keys }.toSet()
            listOf("minimum", "maximum", "multipleOf", "minLength", "maxLength", "maxItems", "oneOf").forEach {
                assertTrue("${tool.name} uses $it", it !in keys)
            }
        }
        // At most 20 strict tools, 24 optional parameters and 16 union-typed ones in a request.
        val strict = tools.filter { it.strict }
        val all = strict.flatMap { properties(it.inputSchema) }
        val optional = all.count { !it.third }
        val unions = all.count { (_, schema, _) -> "anyOf" in schema || schema["type"] is JsonArray } +
            strict.flatMap { objects(it.inputSchema) }.count { "items" in it && "anyOf" in it.getValue("items").jsonObject }
        assertTrue("$optional optional", optional <= 24)
        assertTrue("$unions unions", unions <= 16)
    }

    @Test
    fun everyOperationHasItsSchemaAndTheExampleChangeFitsIt() {
        val change = tools.single { it.name == CoachTools.PROPOSE_CHANGE }.inputSchema
        val ops = change.getValue("properties").jsonObject.getValue("operations").jsonObject.getValue("items").jsonObject.getValue("anyOf").jsonArray
            .map { it.jsonObject.getValue("properties").jsonObject.getValue("op").jsonObject.getValue("const").jsonPrimitive.content }
        assertEquals(ChangeFormat.operations().map { it.first }, ops)
        val plan = ActiveProgram(1, program, Position(1, 1), today = LocalDate.of(2026, 10, 13))
        val example = Json.parseToJsonElement(ChangeJson.encode(ChangeFormat.example(plan)))
        assertEquals(emptyList<String>(), fits(change, example))
        // One with every operation, each with its reason and set lines in full, as Claude writes them.
        val every = Json.parseToJsonElement(
            """{"summary": "All", "scope": {"from": "2026-10-13", "to": "2026-10-19"}, "replaces": [], "operations": [
              {"op": "swap", "slot": "a", "exercise": "b", "reason": "r"}, {"op": "remove", "slot": "a", "reason": "r"},
              {"op": "add", "day": "a", "exercise": "b", "sets": [{"count": 2, "reps": "8-10", "rir": 2, "rest": 90}], "reason": "r"},
              {"op": "adjust_sets", "slot": "a", "sets": 2, "reason": "r"}, {"op": "adjust_load", "percent": -10, "reason": "r"},
              {"op": "adjust_rir", "slot": "a", "by": 1, "reason": "r"}, {"op": "remove_technique", "reason": "r"},
              {"op": "skip_day", "reason": "r"}, {"op": "deload_week", "week": 2, "reason": "r"},
              {"op": "shift_program", "weeks": 1, "reason": "r"}, {"op": "repeat_week", "reason": "r"}]}""",
        )
        assertEquals(emptyList<String>(), fits(change, every))
        assertTrue(ChangeJson.decode(every.toString()).operations.size == 11)
        // And the schema turns down what the format doesn't have.
        assertTrue(fits(change, Json.parseToJsonElement("""{"summary": "x", "scope": "next_week", "operations": []}""")).isNotEmpty())
    }

    @Test
    fun aWorkoutAsTheSchemaHasItIsOneTheParserTakes() {
        val schema = tools.single { it.name == CoachTools.PROPOSE_WORKOUT }.inputSchema
        val row = library.exercises.first { !it.hidden }.id
        val workout = Json.parseToJsonElement(
            """{"name": "Hotel, 45 min", "summary": "Back first.", "groups": [
              {"type": "straight", "slots": [{"id": "row", "exercise": "$row", "note": "Pause", "sets": [
                {"count": 3, "reps": "8-12", "rir": 1, "rest": 120},
                {"count": 1, "reps": 10, "rir": 0, "rest": 90, "technique": {"kind": "dropset", "drops": 2, "percent": 25}}]}]}]}""",
        )
        assertEquals(emptyList<String>(), fits(schema, workout))
        val read = ProgramParser.workout("""{"workout": $workout}""", ids)
        assertTrue(read.toString(), read is ProgramParser.WorkoutResult.Valid)
    }

    /** The places [value] breaks [schema], for the keywords the tools use. */
    private fun fits(schema: JsonObject, value: JsonElement, path: String = "$"): List<String> {
        schema["anyOf"]?.let { options ->
            return if (options.jsonArray.any { fits(it.jsonObject, value, path).isEmpty() }) emptyList() else listOf("$path: no anyOf fits")
        }
        schema["const"]?.let { if (it != value) return listOf("$path: not $it") }
        schema["enum"]?.let { if (value !in it.jsonArray) return listOf("$path: not in ${it}") }
        return when (schema["type"]?.jsonPrimitive?.content) {
            "object" -> {
                val o = value as? JsonObject ?: return listOf("$path: not an object")
                val properties = schema.getValue("properties").jsonObject
                val required = schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }
                required.filter { it !in o }.map { "$path.$it: required" } +
                    (o.keys - properties.keys).map { "$path.$it: not allowed" } +
                    o.filterKeys { it in properties }.flatMap { (k, v) -> fits(properties.getValue(k).jsonObject, v, "$path.$k") }
            }
            "array" -> (value as? JsonArray)?.flatMapIndexed { i, v -> fits(schema.getValue("items").jsonObject, v, "$path[$i]") } ?: listOf("$path: not an array")
            "string" -> if ((value as? JsonPrimitive)?.isString == true) emptyList() else listOf("$path: not a string")
            "integer" -> if ((value as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull != null) emptyList() else listOf("$path: not an integer")
            "boolean" -> if ((value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull != null) emptyList() else listOf("$path: not a boolean")
            else -> emptyList()
        }
    }
}
