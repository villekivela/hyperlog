package com.hyperlog.app.program

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A session's own sets for one of its exercises, edited on the fly, stored as a JSON array of
 * resolved set lines: `[{"reps":"6-8","rir":1,"rest":180,"technique":{…},"backoff":{"from":0,"percent":10}}]`.
 * The shape is the program format's set line without `count` or `weeks`, so a Change can carry it.
 */
object PlannedSetJson {
    fun encode(sets: List<PlannedSet>): String = buildJsonArray {
        sets.forEach { set ->
            add(
                buildJsonObject {
                    put("reps", set.reps.toString().replace('–', '-'))
                    set.rir?.let { put("rir", it) }
                    put("rest", set.rest)
                    set.technique?.let { put("technique", Json.parseToJsonElement(TechniqueJson.encode(it))) }
                    set.backoff?.let { b -> put("backoff", buildJsonObject { put("from", b.from); put("percent", b.percent) }) }
                },
            )
        }
    }.toString()

    fun decode(text: String): List<PlannedSet> = Json.parseToJsonElement(text).jsonArray.map { element ->
        val o = element.jsonObject
        val reps = o.getValue("reps").jsonPrimitive.content.split('-').map { it.trim().toInt() }
        PlannedSet(
            reps = RepRange(reps.first(), reps.last()),
            rir = o["rir"]?.jsonPrimitive?.intOrNull,
            rest = o.getValue("rest").jsonPrimitive.int,
            technique = (o["technique"] as? JsonObject)?.let { TechniqueJson.decode(it.toString()) },
            backoff = (o["backoff"] as? JsonObject)?.let { b ->
                Backoff(b.getValue("from").jsonPrimitive.int, b.getValue("percent").jsonPrimitive.int)
            },
        )
    }
}
