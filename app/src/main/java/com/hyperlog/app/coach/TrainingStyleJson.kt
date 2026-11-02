package com.hyperlog.app.coach

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The training style as it is kept (`training_style` in `setting`: the training targets and About
 * me) and a program's `targets`, read by one reader. Kept JSON is read leniently, a bad field
 * falling back to its default; a program file reports each problem with its path.
 */
object TrainingStyleJson {
    /** Volume's ends: weekly sets per muscle. */
    val VOLUME = 1..40

    /** A rep range's ends. */
    val REPS = 1..30

    /** What the `training_style` row holds. */
    data class Kept(val targets: TrainingTargets = TrainingTargets(), val aboutMe: AboutMe = AboutMe())

    fun encode(kept: Kept): String = buildJsonObject {
        val (targets, aboutMe) = kept
        putJsonObject("targets") {
            put("goal", targets.goal.key)
            put("volume", targets.volume.key)
            put("acrossBlock", targets.acrossBlock.key)
            put("frequency", targets.frequency.key)
            put("failure", targets.failure.key)
            put("calibration", targets.calibration)
            putJsonObject("repRanges") {
                put("compound", targets.compound.key)
                put("isolation", targets.isolation.key)
            }
        }
        putJsonObject("aboutMe") {
            aboutMe.age?.let { put("age", it.key) }
            aboutMe.progress?.let { put("progress", it.key) }
            if (aboutMe.comingBack) put("comingBack", true)
            putJsonArray("aches") {
                aboutMe.aches.forEach { (area, note) -> add(buildJsonObject { put("area", area.key); put("note", note) }) }
            }
            putJsonArray("avoid") { aboutMe.avoid.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("favour") { aboutMe.favour.forEach { add(JsonPrimitive(it)) } }
            aboutMe.anythingElse?.let { put("anythingElse", it) }
        }
    }.toString()

    /** The kept training targets and About me; nothing kept, or anything unreadable, is the default. */
    fun decode(text: String?): Kept {
        val root = text?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return Kept()
        val ignore: (String, String) -> Unit = { _, _ -> }
        val targets = (root["targets"] as? JsonObject)?.let { targets(it, "targets", ignore).over(TrainingTargets()) } ?: TrainingTargets()
        return Kept(targets, (root["aboutMe"] as? JsonObject)?.let(::aboutMe) ?: AboutMe())
    }

    private fun aboutMe(o: JsonObject): AboutMe {
        fun string(key: String) = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun ids(key: String) = (o[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        val aches = (o["aches"] as? JsonArray).orEmpty().mapNotNull { el ->
            val ache = el as? JsonObject ?: return@mapNotNull null
            val area = BodyArea.entries.firstOrNull { it.key == (ache["area"] as? JsonPrimitive)?.content } ?: return@mapNotNull null
            area to ((ache["note"] as? JsonPrimitive)?.content ?: "")
        }.sortedBy { it.first }.toMap()
        return AboutMe(
            age = AgeBand.entries.firstOrNull { it.key == string("age") },
            progress = Progress.entries.firstOrNull { it.key == string("progress") },
            comingBack = (o["comingBack"] as? JsonPrimitive)?.booleanOrNull == true,
            aches = aches,
            avoid = ids("avoid"),
            favour = ids("favour"),
            anythingElse = string("anythingElse")?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Reads a `targets` object at [path], reporting each problem through [error] (path, message);
     * a field with a problem is left out.
     */
    fun targets(o: JsonObject, path: String, error: (String, String) -> Unit): ProgramTrainingTargets {
        val fields = setOf("goal", "volume", "acrossBlock", "frequency", "failure", "calibration", "repRanges")
        o.keys.filter { it !in fields }.forEach { error(path, "unknown field \"$it\"") }
        fun <T> choice(key: String, entries: List<T>, keyOf: (T) -> String): T? {
            val el = o[key] ?: return null
            val value = (el as? JsonPrimitive)?.takeIf { it.isString }?.content
            return entries.firstOrNull { keyOf(it) == value }
                ?: null.also { error("$path.$key", "must be ${or(entries.map { "\"${keyOf(it)}\"" })}") }
        }
        val calibration = o["calibration"]?.let { el ->
            (el as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: null.also { error("$path.calibration", "must be true or false") }
        }
        var compound: NumberRange? = null
        var isolation: NumberRange? = null
        o["repRanges"]?.let { el ->
            val ranges = el as? JsonObject ?: return@let error("$path.repRanges", "must be an object")
            ranges.keys.filter { it !in setOf("compound", "isolation") }.forEach { error("$path.repRanges", "unknown field \"$it\"") }
            compound = range(ranges, "compound", "$path.repRanges", REPS, error)
            isolation = range(ranges, "isolation", "$path.repRanges", REPS, error)
        }
        return ProgramTrainingTargets(
            goal = choice("goal", TrainingGoal.entries) { it.key },
            volume = range(o, "volume", path, VOLUME, error),
            acrossBlock = choice("acrossBlock", AcrossBlock.entries) { it.key },
            frequency = choice("frequency", Frequency.entries) { it.key },
            failure = choice("failure", FailureRule.entries) { it.key },
            calibration = calibration,
            compound = compound,
            isolation = isolation,
        )
    }

    private fun range(o: JsonObject, key: String, path: String, ends: IntRange, error: (String, String) -> Unit): NumberRange? {
        val el = o[key] ?: return null
        val match = (el as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.let(com.hyperlog.app.program.RangeText::matchEntire)
        val range = match?.let { NumberRange(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        if (range == null || range.low !in ends || range.high !in ends || range.low > range.high) {
            error("$path.$key", "must be a range like \"8-14\", each end ${ends.first}–${ends.last}, the low end not above the high")
            return null
        }
        return range
    }

    /** `a, b or c`. */
    fun or(items: List<String>) = if (items.size == 1) items.single() else items.dropLast(1).joinToString() + " or " + items.last()

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
