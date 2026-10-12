package com.hyperlog.app.program

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Reads a program file (docs/program-format.md) through a strict validator. Every problem is
 * reported with the exact path to it (`blocks[1].days[0].groups[2].slots[0].exercise: unknown
 * exercise "bb_row"`), all of them at once, so a file can be fixed in one pass.
 */
object ProgramParser {
    sealed interface Result {
        data class Valid(val program: Program) : Result
        data class Invalid(val errors: List<String>) : Result
    }

    /** The format version this app reads. */
    const val FORMAT = 1

    /**
     * Parses [json]. Exercise ids must be in [exercises]; [merged] (legacy id -> kept id) turns an
     * old id's error into a hint at the id to use.
     */
    fun parse(json: String, exercises: Set<String>, merged: Map<String, String> = emptyMap()): Result {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            return Result.Invalid(listOf("not valid JSON: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}"))
        }
        val reader = Reader(exercises, merged)
        val program = reader.program(root)
        return if (reader.errors.isEmpty() && program != null) Result.Valid(program) else Result.Invalid(reader.errors)
    }
}

private val Id = Regex("[a-z0-9_]+")
private val Range = Regex("""(\d+)\s*-\s*(\d+)""")

private class Reader(private val exercises: Set<String>, private val merged: Map<String, String>) {
    val errors = mutableListOf<String>()

    private fun error(path: String, message: String) {
        errors += if (path.isEmpty()) message else "$path: $message"
    }

    fun program(root: JsonElement): Program? {
        val o = obj(root, "", setOf("format", "title", "author", "description", "blocks")) ?: return null
        val format = int(o, "", "format", required = true)
        if (format != null && format != ProgramParser.FORMAT) error("format", "must be ${ProgramParser.FORMAT}")
        val title = string(o, "", "title", required = true)
        val author = string(o, "", "author")
        string(o, "", "description")
        val blocks = list(o, "", "blocks", required = true) { el, path -> block(el, path) }
        if (blocks != null) {
            // Read the weeks and ids again from the file, so a block with problems inside still
            // has its place in the sequence checked.
            val raw = (o["blocks"] as JsonArray).map { it as? JsonObject }
            fun rawInt(b: JsonObject?, key: String) = (b?.get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            var expected: Int? = 1
            raw.forEachIndexed { i, b ->
                val start = rawInt(b, "weekStart")
                val end = rawInt(b, "weekEnd")
                if (expected != null && start != null && start != expected) {
                    error("blocks[$i].weekStart", if (i == 0) "the first block must start in week 1" else "must be $expected, the week after the previous block")
                }
                expected = end?.let { it + 1 }
            }
            duplicates(raw.map { (it?.get("id") as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }) { i, id ->
                error("blocks[$i].id", "\"$id\" is used by another block")
            }
        }
        if (title == null || blocks == null || blocks.any { it == null }) return null
        return Program(title, author, blocks.filterNotNull())
    }

    private fun block(el: JsonElement, path: String): Block? {
        val o = obj(el, path, setOf("id", "name", "weekStart", "weekEnd", "days", "weeks")) ?: return null
        val id = id(o, path)
        val name = string(o, path, "name", required = true)
        val start = int(o, path, "weekStart", required = true, min = 1)
        val end = int(o, path, "weekEnd", required = true, min = 1)
        val weeks = if (start != null && end != null && end < start) {
            error("$path.weekEnd", "must not be before weekStart ($start)")
            null
        } else if (start != null && end != null) WeekRange(start, end) else null
        val days = list(o, path, "days", required = true) { e, p -> day(e, p, weeks) }
        val adjustments = adjustments(o, path, weeks)
        days?.let { ds ->
            duplicates(ds.map { it?.id }) { i, d -> error("$path.days[$i].id", "\"$d\" is used by another day in this block") }
            // Changes name a slot by id, so a slot id means one place in the block.
            val slots = ds.mapIndexed { d, day ->
                day?.groups?.flatMapIndexed { g, group -> group.slots.mapIndexed { s, slot -> "$path.days[$d].groups[$g].slots[$s].id" to slot.id } }
                    .orEmpty()
            }.flatten()
            duplicates(slots.map { it.second }) { i, s -> error(slots[i].first, "\"$s\" is used by another slot in this block") }
        }
        if (id == null || name == null || weeks == null || days == null || days.any { it == null } || adjustments == null) return null
        return Block(id, name, weeks.from, weeks.to, days.filterNotNull(), adjustments)
    }

    private fun day(el: JsonElement, path: String, weeks: WeekRange?): Day? {
        val o = obj(el, path, setOf("id", "name", "groups", "weeks")) ?: return null
        val id = id(o, path)
        val name = string(o, path, "name", required = true)
        val groups = list(o, path, "groups", required = true) { e, p -> group(e, p, weeks) }
        val adjustments = adjustments(o, path, weeks)
        if (id == null || name == null || groups == null || groups.any { it == null } || adjustments == null) return null
        return Day(id, name, groups.filterNotNull(), adjustments)
    }

    private fun group(el: JsonElement, path: String, weeks: WeekRange?): Group? {
        val o = obj(el, path, setOf("type", "slots")) ?: return null
        val type = string(o, path, "type") ?: "straight"
        val superset = when (type) {
            "straight" -> false
            "superset" -> true
            else -> {
                error("$path.type", "must be \"straight\" or \"superset\", not \"$type\"")
                return null
            }
        }
        val slots = list(o, path, "slots", required = true) { e, p -> slot(e, p, weeks) } ?: return null
        val wanted = if (superset) 2 else 1
        if (slots.size != wanted) {
            error("$path.slots", if (superset) "a superset has 2 slots, not ${slots.size}" else "straight sets have 1 slot, not ${slots.size}")
            return null
        }
        if (slots.any { it == null }) return null
        return Group(superset, slots.filterNotNull())
    }

    private fun slot(el: JsonElement, path: String, weeks: WeekRange?): Slot? {
        val o = obj(el, path, setOf("id", "exercise", "alternatives", "note", "sets")) ?: return null
        val id = id(o, path)
        val exercise = exercise(o["exercise"], "$path.exercise")
        val note = string(o, path, "note")
        val alternatives = if (o["alternatives"] == null) emptyList() else list(o, path, "alternatives") { e, p -> alternative(e, p, weeks) }
        val sets = sets(o, path, weeks)
        alternatives?.let { alts ->
            alts.forEachIndexed { i, a ->
                if (a != null && a.exercise == exercise) error("$path.alternatives[$i]", "\"${a.exercise}\" is already the slot's exercise")
            }
            duplicates(alts.map { it?.exercise }) { i, e -> error("$path.alternatives[$i]", "\"$e\" is listed twice") }
        }
        if (id == null || exercise == null || alternatives == null || alternatives.any { it == null } || sets == null) return null
        return Slot(id, exercise, alternatives.filterNotNull(), note, sets)
    }

    private fun alternative(el: JsonElement, path: String, weeks: WeekRange?): Alternative? {
        if (el is JsonPrimitive && el.isString) return exercise(el, path)?.let { Alternative(it) }
        val o = obj(el, path, setOf("exercise", "sets"), what = "an exercise id or an object") ?: return null
        val exercise = exercise(o["exercise"], "$path.exercise")
        val sets = if (o["sets"] == null) null else sets(o, path, weeks) ?: return null
        return exercise?.let { Alternative(it, sets) }
    }

    private fun exercise(el: JsonElement?, path: String): String? {
        if (el == null) return null.also { error(path, "is required") }
        val id = (el as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return null.also { error(path, "must be an exercise id") }
        if (id in exercises) return id
        val kept = merged[id]
        error(path, if (kept != null) "unknown exercise \"$id\" (the library calls it \"$kept\")" else "unknown exercise \"$id\"")
        return null
    }

    private fun sets(o: JsonObject, path: String, weeks: WeekRange?): List<SetSpec>? {
        val lines = list(o, path, "sets") { e, p -> set(e, p, weeks) } ?: return null
        lines.forEachIndexed { i, line ->
            val b = line?.backoff ?: return@forEachIndexed
            val source = lines.getOrNull(b.from)
            when {
                b.from >= i -> error("$path.sets[$i].backoff.from", if (i == 0) "the first line can't be a back-off" else "must point at an earlier line (0 to ${i - 1})")
                source == null -> Unit
                source.backoff != null -> error("$path.sets[$i].backoff.from", "line ${b.from} is a back-off itself")
                source.weeks.any { it.count == 0 } -> error(
                    "$path.sets[$i].backoff.from",
                    "line ${b.from} is removed in some weeks, so the back-off would have no top set",
                )
            }
        }
        if (lines.any { it == null }) return null
        return lines.filterNotNull()
    }

    private fun set(el: JsonElement, path: String, weeks: WeekRange?): SetSpec? {
        val o = obj(el, path, setOf("count", "reps", "rir", "rest", "technique", "backoff", "weeks")) ?: return null
        val count = int(o, path, "count", min = 1, max = 10) ?: if (o["count"] == null) 1 else null
        val reps = reps(o["reps"], "$path.reps", required = true)
        val rir = int(o, path, "rir", min = 0, max = 10)
        val rest = int(o, path, "rest", min = 0, max = 600) ?: if (o["rest"] == null) SetSpec.DEFAULT_REST_SECONDS else null
        val technique = technique(o["technique"], "$path.technique")
        val backoff = o["backoff"]?.let { backoff(it, "$path.backoff") }
        val overrides = overrides(o, path, weeks)
        val ok = count != null && reps != null && rest != null && technique is Ok && overrides != null &&
            (o["rir"] == null || rir != null) && (o["backoff"] == null || backoff != null)
        if (!ok) return null
        return SetSpec(count!!, reps!!, rir, rest!!, (technique as Ok).value, backoff, overrides!!)
    }

    private fun backoff(el: JsonElement, path: String): BackoffSpec? {
        val o = obj(el, path, setOf("from", "percent")) ?: return null
        val from = int(o, path, "from", min = 0) ?: if (o["from"] == null) 0 else return null
        val percent = int(o, path, "percent", min = 1, max = 50) ?: if (o["percent"] == null) BackoffSpec.DEFAULT_PERCENT else return null
        return BackoffSpec(from, percent)
    }

    private fun reps(el: JsonElement?, path: String, required: Boolean): RepRange? {
        if (el == null) return if (required) null.also { error(path, "is required") } else null
        val p = el as? JsonPrimitive
        val message = "must be a number of reps (8) or a range (\"8-10\")"
        if (p == null) return null.also { error(path, message) }
        if (!p.isString) {
            val n = p.intOrNull?.takeIf { p.content == it.toString() } ?: return null.also { error(path, message) }
            if (n !in 1..100) return null.also { error(path, "must be from 1 to 100") }
            return RepRange(n, n)
        }
        val m = Range.matchEntire(p.content.trim()) ?: return null.also { error(path, message) }
        val (lo, hi) = m.destructured.toList().map { it.toInt() }
        return when {
            lo < 1 || hi > 100 -> null.also { error(path, "must be from 1 to 100") }
            lo >= hi -> null.also { error(path, "\"${p.content}\": the range must go up; write a fixed number as $lo") }
            else -> RepRange(lo, hi)
        }
    }

    /** A technique, explicitly none (`null`), or a problem. */
    private sealed interface Read
    private data class Ok(val value: Technique?) : Read
    private data object Bad : Read

    private fun technique(el: JsonElement?, path: String): Read {
        if (el == null || el is JsonNull) return Ok(null)
        val o = el as? JsonObject ?: return Bad.also { error(path, "must be an object with a \"kind\"") }
        val kind = (o["kind"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val fields = when (kind) {
            "dropset" -> setOf("kind", "drops", "percent")
            "myo_reps" -> setOf("kind", "miniSetReps", "maxMiniSets")
            "lengthened_partials" -> setOf("kind", "partials")
            "iso_hold" -> setOf("kind", "seconds", "position")
            "failure" -> setOf("kind")
            null -> return Bad.also {
                error("$path.kind", if (o["kind"] == null) "is required" else "must be a technique name")
            }
            else -> return Bad.also {
                error("$path.kind", "unknown technique \"$kind\" (dropset, myo_reps, lengthened_partials, iso_hold, failure)")
            }
        }
        val saved = errors.size
        unknownFields(o, path, fields)
        val technique = when (kind) {
            "dropset" -> Technique.Dropset(
                drops = int(o, path, "drops", min = 1, max = 3) ?: 2,
                percent = int(o, path, "percent", min = 5, max = 50) ?: 25,
            )
            "myo_reps" -> Technique.MyoReps(
                miniSetReps = int(o, path, "miniSetReps", min = 1, max = 10) ?: 3,
                maxMiniSets = int(o, path, "maxMiniSets", min = 1, max = 10) ?: 5,
            )
            "lengthened_partials" -> Technique.LengthenedPartials(int(o, path, "partials", min = 1, max = 30))
            "iso_hold" -> {
                val seconds = int(o, path, "seconds", required = true, min = 1, max = 300)
                val position = string(o, path, "position", required = true)?.let { p ->
                    HoldPosition.entries.firstOrNull { it.key == p }
                        ?: null.also { error("$path.position", "must be \"stretch\" or \"contraction\", not \"$p\"") }
                }
                if (seconds != null && position != null) Technique.IsoHold(seconds, position) else null
            }
            else -> Technique.Failure
        }
        return if (errors.size > saved || technique == null) Bad else Ok(technique)
    }

    private fun weekKeys(o: JsonObject, path: String, weeks: WeekRange?, read: (JsonObject, String, WeekRange) -> Unit): Boolean {
        val w = o["weeks"] ?: return true
        val map = w as? JsonObject ?: return false.also { error("$path.weeks", "must be an object of week numbers (\"1\" or \"7-11\")") }
        val saved = errors.size
        val seen = mutableListOf<WeekRange>()
        map.forEach { (key, value) ->
            val p = "$path.weeks.\"$key\""
            val range = Range.matchEntire(key.trim())?.destructured?.toList()?.map { it.toInt() }?.let { WeekRange(it[0], it[1]) }
                ?: key.trim().toIntOrNull()?.let { WeekRange(it, it) }
            when {
                range == null -> error(p, "must be a week (\"1\") or weeks (\"7-11\")")
                range.to < range.from -> error(p, "the weeks must go up")
                weeks != null && (range.from < weeks.from || range.to > weeks.to) ->
                    error(p, "week${if (range.from == range.to) " ${range.from} is" else "s $key are"} outside the block's weeks ${weeks.from}–${weeks.to}")
                seen.any { it.from <= range.to && range.from <= it.to } -> error(p, "overlaps another entry in weeks")
                else -> {
                    seen += range
                    val entry = value as? JsonObject
                    if (entry == null) error(p, "must be an object") else read(entry, p, range)
                }
            }
        }
        return errors.size == saved
    }

    private fun overrides(o: JsonObject, path: String, weeks: WeekRange?): List<SetOverride>? {
        val result = mutableListOf<SetOverride>()
        val ok = weekKeys(o, path, weeks) { e, p, range ->
            unknownFields(e, p, setOf("count", "reps", "rir", "rest", "technique"))
            val technique = if (e.containsKey("technique")) technique(e["technique"], "$p.technique") else Ok(null)
            result += SetOverride(
                weeks = range,
                count = int(e, p, "count", min = 0, max = 10),
                reps = reps(e["reps"], "$p.reps", required = false),
                rir = int(e, p, "rir", min = 0, max = 10),
                rest = int(e, p, "rest", min = 0, max = 600),
                setsTechnique = e.containsKey("technique"),
                technique = (technique as? Ok)?.value,
            )
        }
        return if (ok) result else null
    }

    private fun adjustments(o: JsonObject, path: String, weeks: WeekRange?): List<WeekAdjustment>? {
        val result = mutableListOf<WeekAdjustment>()
        val ok = weekKeys(o, path, weeks) { e, p, range ->
            unknownFields(e, p, setOf("rir", "rirAdd", "technique", "fewerSets"))
            if (e.containsKey("technique") && e["technique"] !is JsonNull) {
                error("$p.technique", "can only be null here (no techniques); set a technique on the set itself")
            }
            if (e.containsKey("rir") && e.containsKey("rirAdd")) error(p, "use rir or rirAdd, not both")
            result += WeekAdjustment(
                weeks = range,
                rir = int(e, p, "rir", min = 0, max = 10),
                rirAdd = int(e, p, "rirAdd", min = -5, max = 5) ?: 0,
                noTechniques = e.containsKey("technique"),
                fewerSets = int(e, p, "fewerSets", min = 1, max = 10) ?: 0,
            )
        }
        return if (ok) result else null
    }

    // ------------------------------------------------------------------ primitives

    private fun obj(el: JsonElement, path: String, fields: Set<String>, what: String = "an object"): JsonObject? {
        val o = el as? JsonObject ?: return null.also { error(path, "must be $what") }
        unknownFields(o, path, fields)
        return o
    }

    private fun unknownFields(o: JsonObject, path: String, fields: Set<String>) {
        o.keys.filter { it !in fields }.forEach { error(path, "unknown field \"$it\"") }
    }

    private fun id(o: JsonObject, path: String): String? {
        val id = string(o, path, "id", required = true) ?: return null
        if (!Id.matches(id)) return null.also { error("$path.id", "\"$id\" must be lowercase letters, digits and _") }
        return id
    }

    private fun string(o: JsonObject, path: String, key: String, required: Boolean = false): String? {
        val el = o[key] ?: return if (required) null.also { error(join(path, key), "is required") } else null
        val p = (el as? JsonPrimitive)?.takeIf { it.isString } ?: return null.also { error(join(path, key), "must be text") }
        if (required && p.content.isBlank()) return null.also { error(join(path, key), "must not be empty") }
        return p.content
    }

    private fun int(o: JsonObject, path: String, key: String, required: Boolean = false, min: Int? = null, max: Int? = null): Int? {
        val el = o[key] ?: return if (required) null.also { error(join(path, key), "is required") } else null
        val p = el as? JsonPrimitive
        val n = p?.takeIf { !it.isString }?.intOrNull?.takeIf { p.content == it.toString() }
            ?: return null.also { error(join(path, key), "must be a whole number") }
        if ((min != null && n < min) || (max != null && n > max)) {
            val range = when {
                min != null && max != null -> "from $min to $max"
                min != null -> "$min or more"
                else -> "$max or less"
            }
            return null.also { error(join(path, key), "must be $range") }
        }
        return n
    }

    /** Reads a required non-empty array, item by item, so every item's problems are reported. */
    private fun <T> list(o: JsonObject, path: String, key: String, required: Boolean = true, item: (JsonElement, String) -> T?): List<T?>? {
        val p = join(path, key)
        val el = o[key] ?: return if (required) null.also { error(p, "is required") } else null
        val array = el as? JsonArray ?: return null.also { error(p, "must be a list") }
        if (array.isEmpty()) return null.also { error(p, "must not be empty") }
        return array.mapIndexed { i, e -> item(e, "$p[$i]") }
    }

    private fun join(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"

    private inline fun duplicates(values: List<String?>, report: (Int, String) -> Unit) {
        val seen = mutableSetOf<String>()
        values.forEachIndexed { i, v -> if (v != null && !seen.add(v)) report(i, v) }
    }
}
