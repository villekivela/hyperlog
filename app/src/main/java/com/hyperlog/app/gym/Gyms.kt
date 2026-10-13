package com.hyperlog.app.gym

import com.hyperlog.app.data.EquipmentItemEntity
import com.hyperlog.app.data.GymEntity
import com.hyperlog.app.data.GymExerciseItemEntity
import com.hyperlog.app.data.GymMissingExerciseEntity
import com.hyperlog.app.data.Visit
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SharedLadderEntity
import com.hyperlog.app.domain.LadderSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** A gym, its equipment, and the weekdays it is planned for (empty: no plan). */
data class Gym(
    val id: Long,
    val name: String,
    val items: List<EquipmentItem>,
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** Exercises the gym has no equipment for: picking it proposes swaps for them. */
    val missing: Set<String> = emptySet(),
)

/**
 * One piece of equipment at a gym, with its weight ladder. [id] 0 is a new one. An [archived] item
 * was deleted after sets were logged on it: only a session that used it still sees it.
 */
data class EquipmentItem(val id: Long = 0, val gymId: Long, val name: String, val ladder: LadderSpec, val archived: Boolean = false)

/**
 * The gyms you train at and their equipment, plus the dumbbell ladder and barbell plates that
 * every gym shares (SPEC, Gyms and equipment). Shared ladders read as the SPEC defaults until
 * edited.
 */
class Gyms(db: HyperlogDatabase) {
    private val dao = db.gyms()

    val gyms: Flow<List<Gym>> = combine(dao.observeGyms(), dao.observeItems(), dao.observeMissing()) { gyms, items, missing ->
        val byGym = items.groupBy { it.gymId }
        val missingBy = missing.groupBy({ it.gymId }, { it.exerciseId })
        gyms.map { g -> g.toGym(byGym[g.id].orEmpty(), missingBy[g.id].orEmpty()) }
    }

    val dumbbells: Flow<LadderSpec> = dao.observeShared().map { it.dumbbells() }
    val plates: Flow<LadderSpec.Plates> = dao.observeShared().map { it.plates() }

    suspend fun addGym(name: String): Long = dao.insertGym(GymEntity(name = name.trim()))
    suspend fun renameGym(id: Long, name: String) = dao.renameGym(id, name.trim())

    /**
     * Deletes a gym. One trained at, or with a machine sets were logged on, is archived instead,
     * so its sessions and each machine's history stay apart (ADR 0002); its unused equipment goes.
     */
    suspend fun deleteGym(id: Long) {
        val items = dao.items(id)
        items.forEach { deleteItem(it.id) }
        if (dao.gymUsed(id) || items.any { dao.itemUsed(it.id) }) dao.archiveGym(id) else dao.deleteGym(id)
    }
    suspend fun setWeekdays(id: Long, days: Set<DayOfWeek>) = dao.setWeekdays(id, days.toBits())

    /** One gym with its equipment, as it is now; archived items come too, for the sessions that used them. */
    suspend fun gym(id: Long): Gym? = dao.gym(id)?.let { it.toGym(dao.items(id), dao.missing(id)) }

    /** Marks [exerciseId] as one [gymId] has no equipment for. */
    suspend fun markMissing(gymId: Long, exerciseId: String) = dao.markMissing(GymMissingExerciseEntity(gymId, exerciseId))

    /** [gymId] has the equipment for [exerciseId] after all. */
    suspend fun unmarkMissing(gymId: Long, exerciseId: String) = dao.unmarkMissing(gymId, exerciseId)

    /** The gym to preselect when a session starts on [day]; see [usualGym]. */
    suspend fun usualGym(day: DayOfWeek, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val gyms = dao.observeGyms().first().map { it.toGym(emptyList()) }
        return usualGym(gyms, dao.visits(), day, zone)
    }

    /** The usual gym for a session started today, kept up to date as gyms and sessions change. */
    fun usualGymToday(zone: ZoneId = ZoneId.systemDefault()): Flow<Long?> =
        combine(gyms, dao.observeVisits()) { gyms, visits ->
            usualGym(gyms, visits, java.time.LocalDate.now(zone).dayOfWeek, zone)
        }

    /** Remembers [itemId] as the machine [exerciseId] is done on at [gymId]. */
    suspend fun remember(gymId: Long, exerciseId: String, itemId: Long) =
        dao.remember(GymExerciseItemEntity(gymId, exerciseId, itemId))

    /** The machine [exerciseId] was last done on at [gymId], if it has been. */
    suspend fun remembered(gymId: Long, exerciseId: String): Long? = dao.rememberedItem(gymId, exerciseId)

    /** Adds an equipment item and returns its id. */
    suspend fun addItem(item: EquipmentItem): Long =
        dao.upsertItem(EquipmentItemEntity(0, item.gymId, item.name.trim(), item.ladder.encode()))

    suspend fun saveItem(item: EquipmentItem) {
        dao.upsertItem(EquipmentItemEntity(item.id, item.gymId, item.name.trim(), item.ladder.encode(), item.archived))
    }

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

    /** Each machine's history heading, `Cable left · Kamppi`, by id, deleted ones included. */
    suspend fun machineNames(): Map<Long, String> = dao.machineNames().associate { it.id to "${it.name} · ${it.gym}" }

    suspend fun setDumbbells(spec: LadderSpec) = dao.setShared(SharedLadderEntity(DUMBBELLS, spec.encode()))
    suspend fun setPlates(spec: LadderSpec.Plates) = dao.setShared(SharedLadderEntity(PLATES, spec.encode()))

    /** The shared ladders as they are now, for the session. */
    suspend fun shared(): SharedLadders = dao.shared().let { SharedLadders(it.dumbbells(), it.plates()) }

    private fun List<SharedLadderEntity>.dumbbells() =
        firstOrNull { it.key == DUMBBELLS }?.let { LadderSpec.decode(it.ladder) } ?: LadderSpec.DefaultDumbbells

    private fun List<SharedLadderEntity>.plates() =
        firstOrNull { it.key == PLATES }?.let { LadderSpec.decode(it.ladder) as? LadderSpec.Plates } ?: LadderSpec.DefaultPlates

    private fun GymEntity.toGym(items: List<EquipmentItemEntity>, missing: List<String> = emptyList()) =
        Gym(id, name, items.mapNotNull { it.toItem() }, weekdays.toDays(), missing.toSet())

    private fun EquipmentItemEntity.toItem() = LadderSpec.decode(ladder)?.let { EquipmentItem(id, gymId, name, it, archived) }

    private companion object {
        const val DUMBBELLS = "dumbbells"
        const val PLATES = "plates"
    }
}

data class SharedLadders(val dumbbells: LadderSpec, val plates: LadderSpec.Plates) {
    companion object {
        val Defaults = SharedLadders(LadderSpec.DefaultDumbbells, LadderSpec.DefaultPlates)
    }
}

/**
 * The gym a session starting on [day] defaults to. A gym whose weekday plan has [day] comes first;
 * otherwise the gym trained at most often on that weekday, the latest visit breaking a tie; then
 * the gym trained at last; then the first gym. Null with no gyms.
 */
fun usualGym(gyms: List<Gym>, visits: List<Visit>, day: DayOfWeek, zone: ZoneId): Long? {
    if (gyms.isEmpty()) return null
    val ids = gyms.map { it.id }.toSet()
    gyms.firstOrNull { day in it.weekdays }?.let { return it.id }
    val known = visits.filter { it.gymId in ids }
    val onDay = known.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).dayOfWeek == day }
    onDay.groupBy { it.gymId }
        .maxWithOrNull(compareBy<Map.Entry<Long, List<Visit>>> { it.value.size }.thenBy { e -> e.value.maxOf { it.startedAt } })
        ?.let { return it.key }
    return known.maxByOrNull { it.startedAt }?.gymId ?: gyms.first().id
}

private fun Set<DayOfWeek>.toBits() = fold(0) { bits, d -> bits or (1 shl (d.value - 1)) }

private fun Int.toDays() = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()
