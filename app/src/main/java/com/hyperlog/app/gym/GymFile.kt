package com.hyperlog.app.gym

import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.StackReading
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.library.Exercise
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.DayOfWeek

/**
 * A gym file (docs/gym-format.md): a gym with its equipment, read through a strict validator that
 * reports every problem with its path (`equipment[2].serves[0]: unknown exercise "bb_row"`), all
 * at once, like a program file. Importing it adds a gym and never changes one already there.
 */
data class GymFile(
    val name: String,
    val usualDays: Set<DayOfWeek> = emptySet(),
    /** The gym's small plates for the pin; null for the default. */
    val pinPlates: List<Double>? = null,
    /** The dumbbell rack; null for the usual one. */
    val dumbbells: LadderSpec? = null,
    val bars: List<Bar> = emptyList(),
    val equipment: List<Item> = emptyList(),
    /** The gym's plates as pairs, one entry per pair; null for the typical gym's. */
    val plates: List<Double>? = null,
) {
    /** A bar at the gym: its [kind] (`barbell`, `ez_bar`, `trap_bar`, `safety_bar`, `swiss_bar`, `axle_bar`) and weight. */
    data class Bar(val name: String, val kind: String, val kg: Double)

    /**
     * An equipment item. [ladder] is a guess when [unconfirmed]; a plate-loaded one with no pairs
     * of its own loads the gym's plates. [serves] fills in "Which machine?" for those exercises;
     * [cableStation] makes it the gym's cable station.
     */
    data class Item(
        val name: String,
        val kind: Kind,
        val ladder: LadderSpec?,
        val unconfirmed: Boolean,
        val serves: List<String>,
        val cableStation: Boolean = false,
    )

    enum class Kind(val key: String) { Stack("stack"), PlateLoaded("plate_loaded"), RackBench("rack_bench") }

    sealed interface Result {
        data class Valid(val gym: GymFile) : Result
        data class Invalid(val errors: List<String>) : Result
    }

    companion object {
        const val FORMAT = "hyperlog-gym"
        const val VERSION = 1

        /** The bar kinds a file may name; the first four are the app's barbell, EZ, trap and safety bars. */
        val BarKinds = listOf("barbell", "ez_bar", "trap_bar", "safety_bar", "swiss_bar", "axle_bar")

        /** Parses [json], with exercise ids checked against [library]. */
        fun parse(json: String, library: Map<String, Exercise>): Result {
            val root = try {
                Json.parseToJsonElement(json)
            } catch (e: SerializationException) {
                return Result.Invalid(listOf("not valid JSON: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}"))
            }
            val format = ((root as? JsonObject)?.get("format") as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (format != FORMAT) return Result.Invalid(listOf("not a Hyperlog gym file"))
            val reader = GymReader(library)
            val gym = reader.gym(root as JsonObject)
            return if (reader.errors.isEmpty() && gym != null) Result.Valid(gym) else Result.Invalid(reader.errors)
        }
    }
}

private class GymReader(private val library: Map<String, Exercise>) {
    val errors = mutableListOf<String>()

    private fun error(path: String, message: String) {
        errors += if (path.isEmpty()) message else "$path: $message"
    }

    fun gym(o: JsonObject): GymFile? {
        unknown(o, "", setOf("format", "version", "name", "usualDays", "pinPlates", "plates", "dumbbells", "bars", "equipment"))
        val version = (o["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (version != GymFile.VERSION) error("version", "must be ${GymFile.VERSION}")
        val name = text(o, "", "name", required = true)
        val days = array(o, "", "usualDays")?.mapIndexedNotNull { i, el ->
            val day = (el as? JsonPrimitive)?.takeIf { it.isString }?.content
            DayOfWeek.entries.firstOrNull { it.name.equals(day, ignoreCase = true) }
                ?: null.also { error("usualDays[$i]", if (day != null) "\"$day\" is not a weekday" else "must be a weekday") }
        }?.toSet().orEmpty()
        val pinPlates = array(o, "", "pinPlates")?.mapIndexedNotNull { i, el -> weight(el, "pinPlates[$i]") }
        val plates = array(o, "", "plates")?.let { sizes ->
            val seen = mutableSetOf<Double>()
            sizes.flatMapIndexed { i, el ->
                plateSize(el, "plates[$i]")?.let { (kg, pairs) ->
                    if (!seen.add(kg)) emptyList<Double>().also { error("plates[$i].kg", "${formatKgText(kg)} kg is already listed") } else List(pairs) { kg }
                }.orEmpty()
            }
        }
        val dumbbells = o["dumbbells"]?.let { ladder(it, "dumbbells", allowed = setOf("pin", "steps", "list"))?.let { spec -> (spec as? LadderSpec.PinStack)?.let { LadderSpec.Explicit(it.pins) } ?: spec } }
        val bars = array(o, "", "bars")?.mapIndexedNotNull { i, el -> bar(el, "bars[$i]") }.orEmpty()
        val served = mutableMapOf<String, Int>()
        var station: Int? = null
        val items = array(o, "", "equipment")?.mapIndexedNotNull { i, el ->
            val path = "equipment[$i]"
            val item = item(el, path) ?: return@mapIndexedNotNull null
            item.serves.forEachIndexed { j, id ->
                val before = served[id]
                if (before != null) error("$path.serves[$j]", "$id is already served by equipment[$before]") else served[id] = i
            }
            if (item.cableStation) {
                if (station != null) error("$path.cableStation", "equipment[$station] is already the cable station") else station = i
            }
            item
        }.orEmpty()
        if (name == null) return null
        return GymFile(name, days, pinPlates, dumbbells, bars, items, plates)
    }

    /** One size of the gym's plates: `{ "kg": 20, "pairs": 8 }`. */
    private fun plateSize(el: JsonElement, path: String): Pair<Double, Int>? {
        val o = el as? JsonObject ?: return null.also { error(path, "must be an object") }
        unknown(o, path, setOf("kg", "pairs"))
        val kg = o["kg"]?.let { weight(it, "$path.kg") } ?: null.also { if (o["kg"] == null) error("$path.kg", "is required") }
        val pairs = when (val p = o["pairs"]) {
            null -> null.also { error("$path.pairs", "is required") }
            else -> (p as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it in 0..50 }
                ?: null.also { error("$path.pairs", "must be a whole number from 0 to 50") }
        }
        return if (kg != null && pairs != null) kg to pairs else null
    }

    private fun formatKgText(kg: Double) = com.hyperlog.app.domain.formatKg(kg)

    private fun bar(el: JsonElement, path: String): GymFile.Bar? {
        val o = el as? JsonObject ?: return null.also { error(path, "must be an object") }
        unknown(o, path, setOf("name", "kind", "kg"))
        val name = text(o, path, "name", required = true)
        val kind = text(o, path, "kind", required = true)?.takeIf { it in GymFile.BarKinds }
            ?: null.also { if (o["kind"] != null) error("$path.kind", "must be one of ${GymFile.BarKinds.joinToString(", ")}") }
        val kg = o["kg"]?.let { weight(it, "$path.kg") } ?: null.also { if (o["kg"] == null) error("$path.kg", "is required") }
        return if (name != null && kind != null && kg != null) GymFile.Bar(name, kind, kg) else null
    }

    private fun item(el: JsonElement, path: String): GymFile.Item? {
        val o = el as? JsonObject ?: return null.also { error(path, "must be an object") }
        unknown(o, path, setOf("name", "kind", "ladder", "unconfirmed", "serves", "cableStation"))
        val name = text(o, path, "name", required = true)
        val kindText = text(o, path, "kind", required = true)
        val kind = kindText?.let { k -> GymFile.Kind.entries.firstOrNull { it.key == k } ?: null.also { error("$path.kind", "must be stack, plate_loaded or rack_bench") } }
        val ladder = when (kind) {
            GymFile.Kind.Stack -> o["ladder"]?.let { ladder(it, "$path.ladder", setOf("pin", "steps", "list")) } ?: LadderSpec.DefaultPinStack.takeIf { o["ladder"] == null }
            GymFile.Kind.PlateLoaded -> o["ladder"]?.let { ladder(it, "$path.ladder", setOf("plates")) }
                ?: LadderSpec.Plates(0.0, emptyList()).takeIf { o["ladder"] == null }
            GymFile.Kind.RackBench -> null.also { if (o["ladder"] != null) error("$path.ladder", "a rack or bench has no weights") }
            null -> null
        }
        val unconfirmed = bool(o, path, "unconfirmed") ?: (o["ladder"] == null && kind != GymFile.Kind.RackBench)
        val station = bool(o, path, "cableStation") ?: false
        if (station && kind != GymFile.Kind.Stack) error("$path.cableStation", "only a stack can be the cable station")
        val serves = array(o, path, "serves")?.mapIndexedNotNull { i, s -> served(s, "$path.serves[$i]", kind) }.orEmpty()
        if (name == null || kind == null || (kind != GymFile.Kind.RackBench && ladder == null)) return null
        return GymFile.Item(name, kind, ladder, unconfirmed && kind != GymFile.Kind.RackBench, serves, station)
    }

    private fun served(el: JsonElement, path: String, kind: GymFile.Kind?): String? {
        val id = (el as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null.also { error(path, "must be an exercise id") }
        val exercise = library[id] ?: return null.also { error(path, "unknown exercise \"$id\"") }
        if (kind != GymFile.Kind.RackBench && kind != null && !exercise.equipment.perMachine) {
            return null.also { error(path, "${exercise.name} is a free-weight exercise, not done on a machine") }
        }
        return id
    }

    /**
     * A ladder of one of the [allowed] kinds: `pin` (unit, plates as shorthand or a list, optional
     * labels, addOns and pinPlates), `steps` (unit, start, step, count), `list` (kg) or `plates`
     * (base and optional pairs). A shorthand string is a list of kg, exactly as typed.
     */
    fun ladder(el: JsonElement, path: String, allowed: Set<String>): LadderSpec? {
        if (el is JsonPrimitive && el.isString) {
            val plates = StackReading.exact(el.content) ?: return null.also { error(path, "\"${el.content}\" isn't a list of weights") }
            return if ("pin" in allowed) LadderSpec.PinStack(WeightUnit.Kg, plates, pinPlates = false) else LadderSpec.Explicit(plates)
        }
        val o = el as? JsonObject ?: return null.also { error(path, "must be a ladder object or a weights shorthand") }
        val kind = text(o, path, "kind", required = true) ?: return null
        if (kind !in allowed) return null.also { error("$path.kind", "must be ${allowed.joinToString(" or ")}") }
        fun unit(): WeightUnit? = when (val u = text(o, path, "unit", required = true)) {
            "kg" -> WeightUnit.Kg
            "lb" -> WeightUnit.Lb
            null -> null
            else -> null.also { error("$path.unit", "must be kg or lb, not \"$u\"") }
        }
        return when (kind) {
            "pin" -> {
                unknown(o, path, setOf("kind", "unit", "plates", "labels", "addOns", "pinPlates"))
                val unit = unit()
                val plates = when (val p = o["plates"]) {
                    null -> null.also { error("$path.plates", "is required") }
                    is JsonPrimitive -> StackReading.exact(p.content)
                        ?: null.also { error("$path.plates", "\"${p.content}\" isn't a stack: give two plates before …") }
                    is JsonArray -> p.mapIndexedNotNull { i, w -> weight(w, "$path.plates[$i]") }.sorted().distinct()
                    else -> null.also { error("$path.plates", "must be a shorthand or a list of weights") }
                }
                val labels = o["labels"]?.let { it as? JsonObject ?: null.also { _ -> error("$path.labels", "must be an object") } }
                labels?.let { unknown(it, "$path.labels", setOf("kg", "cut")) }
                val labelKg = labels?.get("kg")?.let { weight(it, "$path.labels.kg") }
                val cut = labels?.let { bool(it, "$path.labels", "cut") } ?: false
                val addOns = array(o, path, "addOns")?.mapIndexedNotNull { i, w -> weight(w, "$path.addOns[$i]") }.orEmpty()
                val pinPlates = bool(o, path, "pinPlates") ?: true
                if (unit == null || plates == null || plates.isEmpty()) null
                else LadderSpec.PinStack(unit, plates, labelKg ?: if (unit == WeightUnit.Lb) 0.1 else 0.01, cut, addOns, pinPlates)
            }
            "steps" -> {
                unknown(o, path, setOf("kind", "unit", "start", "step", "count"))
                val unit = unit()
                val start = o["start"]?.let { weight(it, "$path.start", zero = true) } ?: null.also { if (o["start"] == null) error("$path.start", "is required") }
                val step = o["step"]?.let { weight(it, "$path.step") } ?: null.also { if (o["step"] == null) error("$path.step", "is required") }
                val count = (o["count"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.takeIf { it in 1..200 }
                    ?: null.also { error("$path.count", "must be a whole number from 1 to 200") }
                if (unit != null && start != null && step != null && count != null) LadderSpec.Steps(start, step, unit, count) else null
            }
            "list" -> {
                unknown(o, path, setOf("kind", "kg"))
                array(o, path, "kg", required = true)?.mapIndexedNotNull { i, w -> weight(w, "$path.kg[$i]") }?.takeIf { it.isNotEmpty() }?.let { LadderSpec.Explicit(it) }
            }
            else -> {
                unknown(o, path, setOf("kind", "base", "pairs"))
                val base = o["base"]?.let { weight(it, "$path.base", zero = true) } ?: 0.0.takeIf { o["base"] == null }
                val pairs = array(o, path, "pairs")?.mapIndexedNotNull { i, w -> weight(w, "$path.pairs[$i]") }.orEmpty()
                base?.let { LadderSpec.Plates(it, pairs) }
            }
        }
    }

    private fun weight(el: JsonElement, path: String, zero: Boolean = false): Double? {
        val v = (el as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        if (v == null || v < 0 || (!zero && v == 0.0) || v > 1000) return null.also { error(path, if (zero) "must be a weight of 0 or more" else "must be a weight above 0") }
        return v
    }

    private fun text(o: JsonObject, path: String, key: String, required: Boolean = false): String? {
        val p = join(path, key)
        val el = o[key] ?: return if (required) null.also { error(p, "is required") } else null
        val s = (el as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null.also { error(p, "must be text") }
        if (s.isBlank()) return null.also { error(p, "must not be empty") }
        return s.trim()
    }

    private fun bool(o: JsonObject, path: String, key: String): Boolean? {
        val el = o[key] ?: return null
        return (el as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: null.also { error(join(path, key), "must be true or false") }
    }

    private fun array(o: JsonObject, path: String, key: String, required: Boolean = false): JsonArray? {
        val el = o[key] ?: return if (required) null.also { error(join(path, key), "is required") } else null
        return el as? JsonArray ?: null.also { error(join(path, key), "must be a list") }
    }

    private fun unknown(o: JsonObject, path: String, fields: Set<String>) {
        o.keys.filter { it !in fields }.forEach { error(path, "unknown field \"$it\"") }
    }

    private fun join(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"
}
