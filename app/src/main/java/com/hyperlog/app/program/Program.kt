package com.hyperlog.app.program

/**
 * A periodized plan, as written in a program file (docs/program-format.md): blocks of weeks,
 * each with its days. It runs as a sequence: the next day is whatever comes after the last one
 * finished, whenever you train.
 */
data class Program(val title: String, val author: String?, val blocks: List<Block>, val description: String? = null) {
    /** Weeks run from 1 without gaps, so the last block's end is the program's length. */
    val weeks: Int get() = blocks.last().weekEnd

    fun blockAt(week: Int): Block? = blocks.firstOrNull { week in it.weekStart..it.weekEnd }

    /** The day after [position], or null when [position] is the program's last day. */
    fun next(position: Position): Position? {
        val block = blockAt(position.week) ?: return null
        return when {
            position.day + 1 < block.days.size -> position.copy(day = position.day + 1)
            position.week < weeks -> Position(position.week + 1, 0)
            else -> null
        }
    }

    /** The day at [position] with its week overrides applied, ready to train. */
    fun plannedDay(position: Position): PlannedDay? {
        val block = blockAt(position.week) ?: return null
        val day = block.days.getOrNull(position.day) ?: return null
        val overrides = listOfNotNull(block.weeks.forWeek(position.week), day.weeks.forWeek(position.week))
        val slots = day.groups.flatMapIndexed { g, group ->
            group.slots.mapIndexed { s, slot ->
                val label = if (group.superset) "${g + 1}${'a' + s}" else "${g + 1}"
                PlannedSlot(
                    id = slot.id,
                    label = label,
                    group = g,
                    exercise = slot.exercise,
                    alternatives = slot.alternatives.map { it.exercise },
                    note = slot.note,
                    sets = expand(slot.sets, position.week, overrides),
                    alternativeSets = slot.alternatives.filter { it.sets != null }
                        .associate { it.exercise to expand(it.sets!!, position.week, overrides) },
                )
            }
        }
        return PlannedDay(position, block, day, slots, deload = overrides.any { it.deload })
    }
}

/** Where you are: the week (from 1) and the index of the next day in that week's block. */
data class Position(val week: Int, val day: Int)

data class Block(
    val id: String,
    val name: String,
    val weekStart: Int,
    val weekEnd: Int,
    val days: List<Day>,
    val weeks: List<WeekOverride> = emptyList(),
)

data class Day(val id: String, val name: String, val groups: List<Group>, val weeks: List<WeekOverride> = emptyList())

/** One exercise on its own (straight sets), or a superset of two. */
data class Group(val superset: Boolean, val slots: List<Slot>)

/**
 * A place in a day for one exercise: the planned exercise, the alternatives the program allows,
 * the program's cue for it, and its set prescriptions. An alternative does the slot's sets unless
 * it has its own.
 */
data class Slot(
    val id: String,
    val exercise: String,
    val alternatives: List<Alternative>,
    val note: String?,
    val sets: List<SetSpec>,
)

data class Alternative(val exercise: String, val sets: List<SetSpec>? = null)

/** Reps as a range; a fixed number has min = max. */
data class RepRange(val min: Int, val max: Int) {
    /** `8`, `8–10`. */
    override fun toString() = if (min == max) "$min" else "$min–$max"
}

/**
 * One line of a slot's sets as written: [count] sets with the same prescription, and how given
 * weeks change it.
 */
data class SetSpec(
    val count: Int = 1,
    val reps: RepRange,
    val rir: Int? = null,
    val rest: Int = DEFAULT_REST_SECONDS,
    val technique: Technique? = null,
    val backoff: BackoffSpec? = null,
    val weeks: List<SetOverride> = emptyList(),
) {
    companion object {
        const val DEFAULT_REST_SECONDS = 120
    }
}

/** A back-off from the first set of an earlier line ([from], an index into the slot's sets). */
data class BackoffSpec(val from: Int = 0, val percent: Int = DEFAULT_PERCENT) {
    companion object {
        const val DEFAULT_PERCENT = 10
    }
}

/** How a work set is extended past its main reps (SPEC, Intensity techniques). */
sealed interface Technique {
    data class Dropset(val drops: Int = 2, val percent: Int = 25) : Technique
    data class MyoReps(val miniSetReps: Int = 3, val maxMiniSets: Int = 5) : Technique
    /** Partials to failure, or [partials] of them. */
    data class LengthenedPartials(val partials: Int? = null) : Technique
    data class IsoHold(val seconds: Int, val position: HoldPosition) : Technique
    data object Failure : Technique

    /** Which of the five techniques this is. */
    val kind: TechniqueKind get() = when (this) {
        is Dropset -> TechniqueKind.Dropset
        is MyoReps -> TechniqueKind.MyoReps
        is LengthenedPartials -> TechniqueKind.LengthenedPartials
        is IsoHold -> TechniqueKind.IsoHold
        Failure -> TechniqueKind.Failure
    }
}

/**
 * The five intensity techniques by kind. [key] is the name program files, Changes and stored
 * sets use (`dropset`, `myo_reps`…); the UI's one map gives each its label and symbol.
 */
enum class TechniqueKind(val key: String) {
    Dropset("dropset"),
    MyoReps("myo_reps"),
    LengthenedPartials("lengthened_partials"),
    IsoHold("iso_hold"),
    Failure("failure"),
    ;

    companion object {
        /** The kind a file names [key]; null for anything else. */
        fun ofKey(key: String?): TechniqueKind? = entries.firstOrNull { it.key == key }

        /** Every kind's key, `dropset, myo_reps, …`, for messages and the Change format. */
        val keys: List<String> get() = entries.map { it.key }
    }
}

enum class HoldPosition(val key: String) { Stretch("stretch"), Contraction("contraction") }

/** Weeks [from]..[to] of the program. */
data class WeekRange(val from: Int, val to: Int) {
    operator fun contains(week: Int) = week in from..to
}

/**
 * A set line's override for some weeks. Null fields keep the line's value; [technique] is only
 * changed when [setsTechnique] (null then removes it).
 */
data class SetOverride(
    val weeks: WeekRange,
    val count: Int? = null,
    val reps: RepRange? = null,
    val rir: Int? = null,
    val rest: Int? = null,
    val setsTechnique: Boolean = false,
    val technique: Technique? = null,
)

/**
 * A block's or day's override for some weeks, applied to every set of it after the sets' own
 * overrides: a target RIR or a change to it, no techniques, fewer sets per slot, and the deload mark.
 */
data class WeekOverride(
    val weeks: WeekRange,
    val rir: Int? = null,
    val rirAdd: Int = 0,
    val noTechniques: Boolean = false,
    val fewerSets: Int = 0,
    /** These weeks are a deload: their sets never lower the estimate. */
    val deload: Boolean = false,
)

private fun List<WeekOverride>.forWeek(week: Int) = firstOrNull { week in it.weeks }

/** A day as it is trained in one week: its slots in order with their resolved sets. */
data class PlannedDay(
    val position: Position,
    val block: Block,
    val day: Day,
    val slots: List<PlannedSlot>,
    /** A deload week for this day, as the program marks it; its sets never lower the estimate. */
    val deload: Boolean = false,
) {
    fun slot(id: String?) = slots.firstOrNull { it.id == id }
}

data class PlannedSlot(
    val id: String,
    /** `1`, or `5a` / `5b` in a superset. */
    val label: String,
    /** Index of the slot's group in the day; superset partners share it. */
    val group: Int,
    val exercise: String,
    val alternatives: List<String>,
    val note: String?,
    val sets: List<SetPrescription>,
    val alternativeSets: Map<String, List<SetPrescription>> = emptyMap(),
    /** How far accepted Changes move this slot's suggested weights, in percent (−10 = 10% lighter). */
    val loadPercent: Int = 0,
    /** The program's exercise for the slot, when a Change swapped it for [exercise]. */
    val inPlaceOf: String? = null,
) {
    /** The sets for [exerciseId]: an alternative's own, else the slot's. */
    fun setsFor(exerciseId: String): List<SetPrescription> = alternativeSets[exerciseId] ?: sets
}

/** One set to do, with this week's values. */
data class SetPrescription(
    val reps: RepRange,
    val rir: Int?,
    val rest: Int,
    val technique: Technique? = null,
    val backoff: Backoff? = null,
)

/** This set's weight is set [from]'s (an index into the planned sets) minus [percent], rounded down to a rung. */
data class Backoff(val from: Int, val percent: Int)

private fun expand(specs: List<SetSpec>, week: Int, overrides: List<WeekOverride>): List<SetPrescription> {
    val firstOf = IntArray(specs.size) { -1 }
    var sets = buildList {
        specs.forEachIndexed { i, spec ->
            val o = spec.weeks.firstOrNull { week in it.weeks }
            val count = o?.count ?: spec.count
            if (count > 0) firstOf[i] = size
            val backoff = spec.backoff?.let { b -> firstOf[b.from].takeIf { it >= 0 }?.let { Backoff(it, b.percent) } }
            repeat(count) {
                add(
                    SetPrescription(
                        reps = o?.reps ?: spec.reps,
                        rir = o?.rir ?: spec.rir,
                        rest = o?.rest ?: spec.rest,
                        technique = if (o?.setsTechnique == true) o.technique else spec.technique,
                        backoff = backoff,
                    ),
                )
            }
        }
    }
    overrides.forEach { a ->
        sets = sets.map { set ->
            set.copy(
                rir = (a.rir ?: set.rir)?.let { (it + a.rirAdd).coerceIn(0, 10) },
                technique = if (a.noTechniques) null else set.technique,
            )
        }
        if (a.fewerSets > 0) sets = sets.take((sets.size - a.fewerSets).coerceAtLeast(1))
    }
    return sets
}

/** The technique with one more drop or mini-set; null for one with neither. */
fun Technique.oneMore(): Technique? = when (this) {
    is Technique.Dropset -> copy(drops = drops + 1)
    is Technique.MyoReps -> copy(maxMiniSets = maxMiniSets + 1)
    else -> null
}
