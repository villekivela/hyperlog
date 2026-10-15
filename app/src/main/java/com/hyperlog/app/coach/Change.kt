package com.hyperlog.app.coach

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * A proposed edit to the plan (SPEC, Coaching): a summary, the period it covers and its
 * operations. It is shown as a diff and applied only once accepted. The JSON is SPEC's Change
 * format; [ChangeJson] reads and writes it, and [ChangeValidator] checks it against a program.
 */
@Serializable
data class Change(
    val summary: String,
    val scope: Scope,
    val operations: List<Operation>,
    /**
     * The ids of Changes in force this one ends: accepting reverts them first, as Revert does.
     * With any, [operations] may be empty, for a reply that only ends them.
     */
    val replaces: List<Long> = emptyList(),
)

/** The period a Change's plan edits cover. Position operations ignore it: they run once, on accept. */
@Serializable(with = ScopeSerializer::class)
sealed interface Scope {
    /** Temporary: sessions dated [from]..[to], both included. */
    data class Dates(val from: LocalDate, val to: LocalDate) : Scope

    /** Every session from the day it is accepted. */
    data object RestOfProgram : Scope

    /** Sessions from the day it is accepted, while they are in the block it was accepted in. */
    data object RestOfBlock : Scope
}

/**
 * One edit. Slot operations name a slot by its id (unique within a block; a block reusing the
 * id means the same place); [slot] null on the load and RIR operations means every slot.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("op")
sealed interface Operation {
    val reason: String?

    /** The slot does [exercise] instead. */
    @Serializable @SerialName("swap")
    data class Swap(val slot: String, val exercise: String, override val reason: String? = null) : Operation

    /** The slot is left out. */
    @Serializable @SerialName("remove")
    data class Remove(val slot: String, override val reason: String? = null) : Operation

    /** [exercise] is added to the day with id [day], after slot [after] (else at the end). */
    @Serializable @SerialName("add")
    data class Add(
        val day: String,
        val exercise: String,
        val sets: List<ChangeSet>,
        val after: String? = null,
        override val reason: String? = null,
    ) : Operation

    /** The slot does [sets] sets: the first ones, or its last set repeated (without its technique). */
    @Serializable @SerialName("adjust_sets")
    data class AdjustSets(val slot: String, val sets: Int, override val reason: String? = null) : Operation

    /** Suggested weights move by [percent] (−10 = 10% lighter), to a rung. */
    @Serializable @SerialName("adjust_load")
    data class AdjustLoad(val slot: String? = null, val percent: Int, override val reason: String? = null) : Operation

    /** Target RIR moves by [by] (+1 = one more rep in reserve). */
    @Serializable @SerialName("adjust_rir")
    data class AdjustRir(val slot: String? = null, val by: Int, override val reason: String? = null) : Operation

    /**
     * The slot's sets (every slot's when [slot] is null) lose their intensity technique of kind
     * [technique] (`dropset`, `myo_reps`, `lengthened_partials`, `iso_hold`, `failure`), or any
     * technique when it is null.
     */
    @Serializable @SerialName("remove_technique")
    data class RemoveTechnique(val slot: String? = null, val technique: String? = null, override val reason: String? = null) : Operation {
        /** The kind [technique] names; null for any (or a name the validator turns down). */
        val kind: com.hyperlog.app.program.TechniqueKind? get() = com.hyperlog.app.program.TechniqueKind.ofKey(technique)
    }

    /** The next day is passed over. */
    @Serializable @SerialName("skip_day")
    data class SkipDay(override val reason: String? = null) : Operation

    /**
     * Program week [week] (the week you are in when null) is a deload: half its sets (rounded
     * up), RIR + 2, no techniques, and it never lowers the estimate.
     */
    @Serializable @SerialName("deload_week")
    data class DeloadWeek(val week: Int? = null, override val reason: String? = null) : Operation

    /**
     * The rest of the plan moves [weeks] weeks later: the position goes back that many weeks, so
     * they are trained again. Negative moves it earlier, skipping weeks.
     */
    @Serializable @SerialName("shift_program")
    data class ShiftProgram(val weeks: Int, override val reason: String? = null) : Operation

    /** The week of the last day trained runs again from its first day. */
    @Serializable @SerialName("repeat_week")
    data class RepeatWeek(override val reason: String? = null) : Operation

    /** Changes the position, once, when accepted. */
    val movesPosition: Boolean get() = this is SkipDay || this is ShiftProgram || this is RepeatWeek
}

/** A set line of an added exercise, as in a program file: `{"count": 3, "reps": "8-12", "rir": 2}`. */
@Serializable
data class ChangeSet(
    val count: Int = 1,
    @Serializable(with = RepsSerializer::class) val reps: com.hyperlog.app.program.RepRange,
    val rir: Int? = null,
    val rest: Int = com.hyperlog.app.program.SetSpec.DEFAULT_REST_SECONDS,
)

/** Reads and writes SPEC's Change JSON. */
object ChangeJson {
    val format = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = false
    }

    fun encode(change: Change): String = format.encodeToString(Change.serializer(), change)

    /** Throws [SerializationException] or [IllegalArgumentException] on JSON that isn't a Change. */
    fun decode(text: String): Change = format.decodeFromString(Change.serializer(), text)
}

/** `{"from": "2026-10-13", "to": "2026-10-19"}`, `"rest_of_program"` or `"rest_of_block"`. */
internal object ScopeSerializer : KSerializer<Scope> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Scope", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Scope {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element is JsonPrimitive && element.isString) {
            return when (element.content) {
                "rest_of_program" -> Scope.RestOfProgram
                "rest_of_block" -> Scope.RestOfBlock
                else -> throw SerializationException("scope: \"${element.content}\" isn't a scope: use {\"from\", \"to\"}, \"rest_of_program\" or \"rest_of_block\"")
            }
        }
        val o = element as? JsonObject ?: throw SerializationException("scope: expected {\"from\", \"to\"} or \"rest_of_program\"")
        val extra = o.keys - setOf("from", "to")
        if (extra.isNotEmpty()) throw SerializationException("scope: unknown field \"${extra.first()}\"")
        fun date(key: String): LocalDate {
            val text = (o[key] as? JsonPrimitive)?.contentOrNull ?: throw SerializationException("scope.$key: a date is required (YYYY-MM-DD)")
            return runCatching { LocalDate.parse(text) }.getOrElse { throw SerializationException("scope.$key: \"$text\" isn't a date (YYYY-MM-DD)") }
        }
        return Scope.Dates(date("from"), date("to"))
    }

    override fun serialize(encoder: Encoder, value: Scope) {
        val json = encoder as JsonEncoder
        val element: JsonElement = when (value) {
            is Scope.Dates -> buildJsonObject { put("from", value.from.toString()); put("to", value.to.toString()) }
            Scope.RestOfProgram -> JsonPrimitive("rest_of_program")
            Scope.RestOfBlock -> JsonPrimitive("rest_of_block")
        }
        json.encodeJsonElement(element)
    }
}

/** Reps as `8` or `"8-10"`, as in a program file. */
internal object RepsSerializer : KSerializer<com.hyperlog.app.program.RepRange> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Reps", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): com.hyperlog.app.program.RepRange {
        val p = (decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive ?: throw SerializationException("reps: expected 8 or \"8-10\"")
        p.intOrNull?.takeIf { !p.isString }?.let { return com.hyperlog.app.program.RepRange(it, it) }
        val parts = p.content.split('-', '–').map { it.trim().toIntOrNull() }
        val min = parts.firstOrNull()
        val max = parts.lastOrNull()
        if (parts.size !in 1..2 || min == null || max == null || min < 1 || max < min) throw SerializationException("reps: \"${p.content}\" isn't 8 or \"8-10\"")
        return com.hyperlog.app.program.RepRange(min, max)
    }

    override fun serialize(encoder: Encoder, value: com.hyperlog.app.program.RepRange) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(if (value.min == value.max) JsonPrimitive(value.min) else JsonPrimitive("${value.min}-${value.max}"))
    }
}
