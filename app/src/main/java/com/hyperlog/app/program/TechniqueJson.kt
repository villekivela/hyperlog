package com.hyperlog.app.program

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A technique as stored with a logged set or a session's change to a set: the program format's
 * technique object (`{"kind":"dropset","drops":2,"percent":25}`).
 */
object TechniqueJson {
    fun encode(technique: Technique): String = buildJsonObject {
        when (technique) {
            is Technique.Dropset -> { put("kind", "dropset"); put("drops", technique.drops); put("percent", technique.percent) }
            is Technique.MyoReps -> { put("kind", "myo_reps"); put("miniSetReps", technique.miniSetReps); put("maxMiniSets", technique.maxMiniSets) }
            is Technique.LengthenedPartials -> { put("kind", "lengthened_partials"); technique.partials?.let { put("partials", it) } }
            is Technique.IsoHold -> { put("kind", "iso_hold"); put("seconds", technique.seconds); put("position", technique.position.key) }
            Technique.Failure -> put("kind", "failure")
        }
    }.toString()

    fun decode(text: String): Technique {
        val o = Json.parseToJsonElement(text).jsonObject
        fun int(key: String) = o[key]?.jsonPrimitive?.int
        return when (o.string("kind")) {
            "dropset" -> Technique.Dropset(int("drops") ?: 2, int("percent") ?: 25)
            "myo_reps" -> Technique.MyoReps(int("miniSetReps") ?: 3, int("maxMiniSets") ?: 5)
            "lengthened_partials" -> Technique.LengthenedPartials(int("partials"))
            "iso_hold" -> Technique.IsoHold(int("seconds") ?: 20, HoldPosition.entries.first { it.key == o.string("position") })
            else -> Technique.Failure
        }
    }

    private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.content
}
