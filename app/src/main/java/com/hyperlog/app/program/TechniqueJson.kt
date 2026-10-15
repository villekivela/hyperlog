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
        put("kind", technique.kind.key)
        when (technique) {
            is Technique.Dropset -> { put("drops", technique.drops); put("percent", technique.percent) }
            is Technique.MyoReps -> put("miniSetReps", technique.miniSetReps)
            is Technique.LengthenedPartials -> technique.partials?.let { put("partials", it) }
            is Technique.IsoHold -> { put("seconds", technique.seconds); put("position", technique.position.key) }
            Technique.Failure -> Unit
        }
    }.toString()

    fun decode(text: String): Technique {
        val o = Json.parseToJsonElement(text).jsonObject
        fun int(key: String) = o[key]?.jsonPrimitive?.int
        return when (TechniqueKind.ofKey(o.string("kind"))) {
            TechniqueKind.Dropset -> Technique.Dropset(int("drops") ?: 2, int("percent") ?: 25)
            TechniqueKind.MyoReps -> Technique.MyoReps(int("miniSetReps") ?: 3)
            TechniqueKind.LengthenedPartials -> Technique.LengthenedPartials(int("partials"))
            TechniqueKind.IsoHold -> Technique.IsoHold(int("seconds") ?: 20, HoldPosition.entries.first { it.key == o.string("position") })
            TechniqueKind.Failure, null -> Technique.Failure
        }
    }

    private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.content
}
