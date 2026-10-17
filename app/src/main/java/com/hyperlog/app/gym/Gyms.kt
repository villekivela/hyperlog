package com.hyperlog.app.gym

import com.hyperlog.app.data.EquipmentItemEntity
import com.hyperlog.app.data.GymEntity
import com.hyperlog.app.data.GymExerciseItemEntity
import com.hyperlog.app.data.GymMissingExerciseEntity
import com.hyperlog.app.data.Visit
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SharedLadderEntity
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.library.Equipment
import androidx.room.withTransaction
import com.hyperlog.app.library.toExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.hyperlog.app.week.WeekdayPlan
import com.hyperlog.app.week.WeekdayPlans
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** A gym and its equipment. */
data class Gym(
    val id: Long,
    val name: String,
    val items: List<EquipmentItem>,
    /** Exercises the gym has no equipment for: picking it proposes swaps for them. */
    val missing: Set<String> = emptySet(),
    /** The gym's small plates for hanging on a stack's pin, one entry per plate (SPEC, Stacks with add-ons). */
    val pinPlates: List<Double> = LadderSpec.DefaultPinPlates,
    /** The gym's dumbbell rack. */
    val dumbbells: LadderSpec = LadderSpec.DefaultDumbbells,
    /** Its racks, benches and bars, from an imported gym file: listed, but no exercise is done "on" them. */
    val kit: List<EquipmentItem> = emptyList(),
    /** The machine each exercise is done on here, remembered from "Which machine?" or the setup. */
    val machines: Map<String, Long> = emptyMap(),
    /** The stack chosen for setting the gym up from the program; null until chosen. */
    val defaultStack: LadderSpec? = null,
    /** The gym's plates as pairs, one entry per pair, which its bars and plate-loaded machines load. */
    val plates: List<Double> = LadderSpec.DefaultGymPlates,
)

/**
 * One piece of equipment at a gym, with its weight ladder. [id] 0 is a new one. An [archived] item
 * was deleted after sets were logged on it: only a session that used it still sees it.
 */
data class EquipmentItem(
    val id: Long = 0,
    val gymId: Long,
    val name: String,
    val ladder: LadderSpec,
    val archived: Boolean = false,
    /** How sure [ladder] is: only a confirmed one checks weights against it. */
    val state: LadderState = LadderState.Confirmed,
    /** The gym's shared cable station: every cable exercise there defaults to it. */
    val cableStation: Boolean = false,
    /** [MACHINE], [RACK], or a bar (`bar:ez_bar`, see [bar]). Only machines are in [Gym.items]. */
    val kind: String = MACHINE,
) {
    /** A bar's kind (`ez_bar`) and weight; null for anything else. */
    val bar: Pair<String, Double>? get() = kind.takeIf { it.startsWith(BAR) }?.let { it.removePrefix(BAR) to ladder.ladder().rungs.first() }

    companion object {
        const val MACHINE = "machine"
        const val RACK = "rack"
        const val BAR = "bar:"
    }
}

/** The sections a gym's screen lists its equipment in, in this order (SPEC, Gyms and equipment). */
enum class EquipmentKind { Stack, PlateLoaded, Bar, RackBench }

/** What kind of equipment this is: a bar or a rack by its [EquipmentItem.kind], a machine by its ladder. */
val EquipmentItem.equipmentKind: EquipmentKind
    get() = when {
        kind == EquipmentItem.RACK -> EquipmentKind.RackBench
        kind.startsWith(EquipmentItem.BAR) -> EquipmentKind.Bar
        ladder is LadderSpec.Plates -> EquipmentKind.PlateLoaded
        else -> EquipmentKind.Stack
    }

/**
 * The gym's equipment and kit by kind, in [EquipmentKind] order, each sorted by name the way a
 * person reads a list (case and accents aside). Kinds it has none of are left out.
 */
fun Gym.sections(): Map<EquipmentKind, List<EquipmentItem>> {
    val collator = java.text.Collator.getInstance(java.util.Locale.ROOT).apply { strength = java.text.Collator.SECONDARY }
    val byKind = (items + kit).filter { !it.archived }.groupBy { it.equipmentKind }
    return EquipmentKind.entries.mapNotNull { k -> byKind[k]?.let { k to it.sortedWith(compareBy(collator) { item -> item.name }) } }.toMap()
}

/**
 * How sure a machine's ladder is (SPEC, Learning a stack). Set up or edited by hand, it is
 * [Confirmed]. A guess (from the program's setup or an import) is [Unconfirmed] until its first use
 * asks for the stack's labels; skipped, it stays a [Guess] and typed weights teach it; a declined
 * offer leaves it [Offered], kept as typed and never offered again.
 */
enum class LadderState(val key: String) {
    Confirmed("confirmed"), Unconfirmed("unconfirmed"), Guess("guess"), Offered("offered");

    companion object {
        fun ofKey(key: String) = entries.firstOrNull { it.key == key } ?: Confirmed
    }
}

/**
 * The gyms you train at and their equipment, plus the dumbbell ladder and barbell plates that
 * every gym shares (SPEC, Gyms and equipment). Shared ladders read as the SPEC defaults until
 * edited.
 */
class Gyms(private val db: HyperlogDatabase) {
    private val dao = db.gyms()
    private val weekdayPlans = WeekdayPlans(db)

    val gyms: Flow<List<Gym>> = combine(dao.observeGyms(), dao.observeItems(), dao.observeMissing(), dao.observeRemembered()) { gyms, items, missing, remembered ->
        val byGym = items.groupBy { it.gymId }
        val missingBy = missing.groupBy({ it.gymId }, { it.exerciseId })
        val rememberedBy = remembered.groupBy { it.gymId }
        gyms.map { g -> g.toGym(byGym[g.id].orEmpty(), missingBy[g.id].orEmpty(), rememberedBy[g.id].orEmpty()) }
    }

    /** What the barbell weighs, at every gym that doesn't name its own; the gym's plates go on it. */
    val barbell: Flow<Double> = dao.observeShared().map { it.plates().baseKg }

    /** The EZ, trap and safety bars' own weights; the gym's plates go on them. */
    val bars: Flow<Map<Equipment, Double>> = dao.observeShared().map { it.bars() }

    suspend fun addGym(name: String): Long = dao.insertGym(GymEntity(name = name.trim()))
    suspend fun renameGym(id: Long, name: String) = dao.renameGym(id, name.trim())

    /**
     * Deletes a gym. One trained at, or with a machine sets were logged on, is archived instead,
     * so its sessions and each machine's history stay apart (ADR 0002); its unused equipment goes.
     */
    suspend fun deleteGym(id: Long) {
        val items = dao.items(id)
        items.forEach { deleteItem(it.id) }
        weekdayPlans.forgetGym(id)
        if (dao.gymUsed(id) || items.any { dao.itemUsed(it.id) }) dao.archiveGym(id) else dao.deleteGym(id)
    }

    /** Sets the small plates [id] has for the pin of its stacks, one entry per plate. */
    suspend fun setPinPlates(id: Long, plates: List<Double>) =
        dao.setPinPlates(id, plates.filter { it > 0 }.joinToString(",") { it.toString() })

    /** Sets [id]'s plates, as pairs: one entry per pair, so 8 pairs of 20 are eight 20s. */
    suspend fun setPlates(id: Long, pairs: List<Double>) =
        dao.setPlates(id, pairs.filter { it > 0 }.sortedDescending().joinToString(",") { it.toString() })

    /** One gym with its equipment, as it is now; archived items come too, for the sessions that used them. */
    suspend fun gym(id: Long): Gym? = dao.gym(id)?.let { it.toGym(dao.items(id), dao.missing(id), dao.remembered(id)) }

    /** What importing a gym file did: added the gym, or nothing, with the file's problems. */
    sealed interface Import {
        data class Added(val gymId: Long) : Import
        data class Invalid(val errors: List<String>) : Import
    }

    /**
     * Imports the gym in [text] (docs/gym-format.md) as a new gym, all in one transaction: its
     * usual days (weekday plan days at this gym, taken from any gym that had them), pin plates, dumbbell rack, bars, racks and machines, each machine remembered for
     * the exercises it serves. Gyms already here are never changed.
     */
    suspend fun import(text: String): Import {
        val library = db.exercises().all().associate { it.id to it.toExercise() }
        val file = when (val result = GymFile.parse(text, library)) {
            is GymFile.Result.Invalid -> return Import.Invalid(result.errors)
            is GymFile.Result.Valid -> result.gym
        }
        val id = db.withTransaction {
            val id = addGym(file.name)
            if (file.usualDays.isNotEmpty()) {
                weekdayPlans.set(WeekdayPlan(weekdayPlans.current().gyms + file.usualDays.associateWith { id }))
            }
            file.pinPlates?.let { setPinPlates(id, it) }
            file.plates?.let { setPlates(id, it) }
            file.dumbbells?.let { setDumbbells(id, it) }
            file.bars.forEach { addItem(EquipmentItem(gymId = id, name = it.name, ladder = LadderSpec.Explicit(listOf(it.kg)), kind = EquipmentItem.BAR + it.kind)) }
            file.equipment.forEach { item ->
                if (item.kind == GymFile.Kind.RackBench) {
                    addItem(EquipmentItem(gymId = id, name = item.name, ladder = LadderSpec.Explicit(emptyList()), kind = EquipmentItem.RACK))
                    return@forEach
                }
                // A plate-loaded machine without pairs of its own loads the gym's plates.
                val ladder = item.ladder!!
                val machine = addItem(
                    EquipmentItem(
                        gymId = id, name = item.name, ladder = ladder, cableStation = item.cableStation,
                        state = if (item.unconfirmed) LadderState.Unconfirmed else LadderState.Confirmed,
                    ),
                )
                item.serves.forEach { remember(id, it, machine) }
            }
            id
        }
        return Import.Added(id)
    }

    /**
     * Adds what [setup] needs at [gymId] in one go: a machine for each machine exercise (one
     * already there by name is used; a plate-loaded one loads the gym's plates from a zero start,
     * the rest [stack]), and the gym's cable station for every cable exercise, made on [stack] if
     * there is none. The new ones are guesses until their first use. [stack] becomes the gym's
     * default stack.
     */
    suspend fun addProgramEquipment(gymId: Long, setup: ProgramSetup, stack: LadderSpec) {
        val gym = gym(gymId) ?: return
        setup.machines.forEach { exercise ->
            val id = gym.machineFor(exercise)?.id ?: addItem(
                EquipmentItem(
                    gymId = gymId, name = exercise.name,
                    ladder = if (exercise.plateLoaded) LadderSpec.Plates(0.0, emptyList()) else stack,
                    state = LadderState.Unconfirmed,
                ),
            )
            remember(gymId, exercise.id, id)
        }
        if (setup.cables.isNotEmpty()) {
            val station = setup.station?.id ?: gym.items.firstOrNull { it.cableStation && !it.archived }?.id ?: addItem(
                EquipmentItem(gymId = gymId, name = CABLE_STATION, ladder = stack, state = LadderState.Unconfirmed, cableStation = true),
            )
            setup.cables.forEach { remember(gymId, it.id, station) }
        }
        dao.setDefaultStack(gymId, stack.encode())
    }

    /** Marks [exerciseId] as one [gymId] has no equipment for. */
    suspend fun markMissing(gymId: Long, exerciseId: String) = dao.markMissing(GymMissingExerciseEntity(gymId, exerciseId))

    /** [gymId] has the equipment for [exerciseId] after all. */
    suspend fun unmarkMissing(gymId: Long, exerciseId: String) = dao.unmarkMissing(gymId, exerciseId)

    /** The gym to preselect when a session starts on [day]; see [usualGym]. */
    suspend fun usualGym(day: DayOfWeek, zone: ZoneId = ZoneId.systemDefault()): UsualGym? {
        val gyms = dao.observeGyms().first().map { it.toGym(emptyList()) }
        return usualGym(gyms, weekdayPlans.current(), dao.visits(), day, zone)
    }

    /** The usual gym for a session started today, kept up to date as gyms and sessions change. */
    fun usualGymToday(zone: ZoneId = ZoneId.systemDefault()): Flow<UsualGym?> =
        combine(gyms, weekdayPlans.plan, dao.observeVisits()) { gyms, plan, visits ->
            usualGym(gyms, plan, visits, java.time.LocalDate.now(zone).dayOfWeek, zone)
        }

    /** Remembers [itemId] as the machine [exerciseId] is done on at [gymId]. */
    suspend fun remember(gymId: Long, exerciseId: String, itemId: Long) =
        dao.remember(GymExerciseItemEntity(gymId, exerciseId, itemId))

    /** The machine [exerciseId] was last done on at [gymId], if it has been. */
    suspend fun remembered(gymId: Long, exerciseId: String): Long? = dao.rememberedItem(gymId, exerciseId)

    /** Adds an equipment item and returns its id. */
    suspend fun addItem(item: EquipmentItem): Long =
        dao.upsertItem(EquipmentItemEntity(0, item.gymId, item.name.trim(), item.ladder.encode(), ladderState = item.state.key, cableStation = item.cableStation, kind = item.kind))

    suspend fun saveItem(item: EquipmentItem) {
        dao.upsertItem(EquipmentItemEntity(item.id, item.gymId, item.name.trim(), item.ladder.encode(), item.archived, item.state.key, item.cableStation, item.kind))
    }

    /** Renames equipment item [id], whatever it is; its ladder, kind and history stay. */
    suspend fun renameItem(id: Long, name: String) = dao.renameItem(id, name.trim())

    /** Machine [id]'s ladder as set up or taken from an offer: confirmed from now on. */
    suspend fun setLadder(id: Long, spec: LadderSpec) = dao.setLadder(id, spec.encode(), LadderState.Confirmed.key)

    suspend fun setLadderState(id: Long, state: LadderState) = dao.setLadderState(id, state.key)

    /** Every weight logged on machine [itemId], any exercise. */
    suspend fun weightsOn(itemId: Long): List<Double> = dao.weightsOn(itemId)

    /**
     * Deletes an equipment item. One with sets logged on it is archived instead: hidden and
     * forgotten as any exercise's machine, but its sessions keep pointing at it, so its history
     * never merges into another machine's or the no-gym pool (ADR 0002).
     */
    suspend fun deleteItem(id: Long) {
        if (dao.itemUsed(id)) {
            dao.forgetItem(id)
            dao.archiveItem(id)
        } else dao.deleteItem(id)
    }

    /** Each machine's history heading, `Cable left · Harbour`, by id, deleted ones included. */
    suspend fun machineNames(): Map<Long, String> = dao.machineNames().associate { it.id to "${it.name} · ${it.gym}" }

    /** Sets [gymId]'s dumbbell rack. */
    suspend fun setDumbbells(gymId: Long, spec: LadderSpec) = dao.setDumbbells(gymId, spec.encode())

    /** Sets what the barbell weighs where a gym doesn't name its own. */
    suspend fun setBarbell(kg: Double) = dao.setShared(SharedLadderEntity(PLATES, LadderSpec.Plates(kg, emptyList()).encode()))

    /** Sets what the [bar] (an EZ, trap or safety bar) weighs. */
    suspend fun setBar(bar: Equipment, kg: Double) {
        require(bar in SharedLadders.DefaultBars) { "$bar is not a specialty bar" }
        dao.setShared(SharedLadderEntity(barKey(bar), LadderSpec.Explicit(listOf(kg)).encode()))
    }

    /** The shared ladders as they are now, for a session: the barbell with the typical plates, until a gym's replace them. */
    suspend fun shared(): SharedLadders = dao.shared().let { SharedLadders(LadderSpec.DefaultDumbbells, LadderSpec.Plates(it.plates().baseKg, LadderSpec.DefaultGymPlates), it.bars()) }

    /** The barbell, stored as plates with no pairs (the gym's go on it); a 20 kg bar until set. */
    private fun List<SharedLadderEntity>.plates() =
        firstOrNull { it.key == PLATES }?.let { LadderSpec.decode(it.ladder) as? LadderSpec.Plates } ?: LadderSpec.DefaultPlates

    /** Each bar's weight, stored as a one-weight list; the default until set. */
    private fun List<SharedLadderEntity>.bars() = SharedLadders.DefaultBars.mapValues { (bar, default) ->
        firstOrNull { it.key == barKey(bar) }?.let { (LadderSpec.decode(it.ladder) as? LadderSpec.Explicit)?.kgs?.firstOrNull() } ?: default
    }

    private fun barKey(bar: Equipment) = "bar_${bar.key}"

    private fun GymEntity.toGym(items: List<EquipmentItemEntity>, missing: List<String> = emptyList(), remembered: List<GymExerciseItemEntity> = emptyList()) =
        Gym(
            id, name, items.filter { it.kind == EquipmentItem.MACHINE }.mapNotNull { it.toItem() }, missing.toSet(), pinPlates.toPinPlates(),
            dumbbells?.let { LadderSpec.decode(it) } ?: LadderSpec.DefaultDumbbells,
            items.filter { it.kind != EquipmentItem.MACHINE && !it.archived }.mapNotNull { it.toItem() },
            remembered.associate { it.exerciseId to it.equipmentItemId },
            defaultStack?.let { LadderSpec.decode(it) },
            plates?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.filter { it > 0 } ?: LadderSpec.DefaultGymPlates,
        )

    private fun EquipmentItemEntity.toItem() = LadderSpec.decode(ladder)?.let { EquipmentItem(id, gymId, name, it, archived, LadderState.ofKey(ladderState), cableStation, kind) }

    companion object {
        /** The name of a cable station the app makes. */
        const val CABLE_STATION = "Cable station"

        const val PLATES = "plates"
    }
}

/**
 * The ladders a session uses besides its machines': the [dumbbells] (the session gym's rack, else
 * the SPEC default), the barbell with the gym's [plates] (the typical ones with no gym), and the
 * weights of the EZ, trap and safety [bars], which load the same plates. A plate-loaded machine
 * without pairs of its own loads them too.
 */
data class SharedLadders(
    val dumbbells: LadderSpec,
    val plates: LadderSpec.Plates,
    val bars: Map<Equipment, Double> = DefaultBars,
) {
    /** The plates on a specialty [bar]: its own weight, then the gym's pairs. Null for anything else. */
    fun barPlates(bar: Equipment): LadderSpec.Plates? = bars[bar]?.let { LadderSpec.Plates(it, plates.pairs) }

    companion object {
        /** What the specialty bars weigh until set: a 10 kg EZ bar, a 25 kg trap bar, a 30 kg safety bar. */
        val DefaultBars = mapOf(Equipment.EzBar to 10.0, Equipment.TrapBar to 25.0, Equipment.SafetyBar to 30.0)

        val Defaults = SharedLadders(LadderSpec.DefaultDumbbells, LadderSpec.DefaultPlates)
    }
}

/** The gym a session preselects, and [why]: only a weekday plan or habit makes it "Usual on Mondays". */
data class UsualGym(val id: Long, val why: Why) {
    enum class Why {
        /** The weekday plan names it for the day, or most sessions on that weekday were there. */
        Weekday,
        /** Trained at last, with nothing known about the weekday. */
        Last,
        /** The first gym, with no plan or history at all. */
        First,
    }
}

/**
 * The gym a session starting on [day] defaults to. The gym the weekday [plan] names for [day] comes
 * first, or the only gym when [day] is a training day naming none; otherwise the gym trained at
 * most often on that weekday, the latest visit breaking a tie; then the gym trained at last; then
 * the first gym. Null with no gyms.
 */
fun usualGym(gyms: List<Gym>, plan: WeekdayPlan, visits: List<Visit>, day: DayOfWeek, zone: ZoneId): UsualGym? {
    if (gyms.isEmpty()) return null
    val ids = gyms.map { it.id }.toSet()
    if (day in plan.days) {
        val planned = plan.gyms[day]?.takeIf { it in ids } ?: gyms.singleOrNull()?.id
        planned?.let { return UsualGym(it, UsualGym.Why.Weekday) }
    }
    val known = visits.filter { it.gymId in ids }
    val onDay = known.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).dayOfWeek == day }
    onDay.groupBy { it.gymId }
        .maxWithOrNull(compareBy<Map.Entry<Long, List<Visit>>> { it.value.size }.thenBy { e -> e.value.maxOf { it.startedAt } })
        ?.let { return UsualGym(it.key, UsualGym.Why.Weekday) }
    known.maxByOrNull { it.startedAt }?.let { return UsualGym(it.gymId, UsualGym.Why.Last) }
    return UsualGym(gyms.first().id, UsualGym.Why.First)
}

private fun String?.toPinPlates(): List<Double> =
    this?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() } ?: LadderSpec.DefaultPinPlates

