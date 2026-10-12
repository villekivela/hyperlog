package com.hyperlog.app.gym

import com.hyperlog.app.data.EquipmentItemEntity
import com.hyperlog.app.data.GymEntity
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.data.SharedLadderEntity
import com.hyperlog.app.domain.LadderSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class Gym(val id: Long, val name: String, val items: List<EquipmentItem>)

/** One piece of equipment at a gym, with its weight ladder. [id] 0 is a new one. */
data class EquipmentItem(val id: Long = 0, val gymId: Long, val name: String, val ladder: LadderSpec)

/**
 * The gyms you train at and their equipment, plus the dumbbell ladder and barbell plates that
 * every gym shares (SPEC, Gyms and equipment). Shared ladders read as the SPEC defaults until
 * edited.
 */
class Gyms(db: HyperlogDatabase) {
    private val dao = db.gyms()

    val gyms: Flow<List<Gym>> = combine(dao.observeGyms(), dao.observeItems()) { gyms, items ->
        val byGym = items.groupBy { it.gymId }
        gyms.map { g -> Gym(g.id, g.name, byGym[g.id].orEmpty().mapNotNull { it.toItem() }) }
    }

    val dumbbells: Flow<LadderSpec> = dao.observeShared().map { it.dumbbells() }
    val plates: Flow<LadderSpec.Plates> = dao.observeShared().map { it.plates() }

    suspend fun addGym(name: String): Long = dao.insertGym(GymEntity(name = name.trim()))
    suspend fun renameGym(id: Long, name: String) = dao.renameGym(id, name.trim())
    suspend fun deleteGym(id: Long) = dao.deleteGym(id)

    suspend fun saveItem(item: EquipmentItem) {
        dao.upsertItem(EquipmentItemEntity(item.id, item.gymId, item.name.trim(), item.ladder.encode()))
    }

    suspend fun deleteItem(id: Long) = dao.deleteItem(id)

    suspend fun setDumbbells(spec: LadderSpec) = dao.setShared(SharedLadderEntity(DUMBBELLS, spec.encode()))
    suspend fun setPlates(spec: LadderSpec.Plates) = dao.setShared(SharedLadderEntity(PLATES, spec.encode()))

    /** The shared ladders as they are now, for the session. */
    suspend fun shared(): SharedLadders = dao.shared().let { SharedLadders(it.dumbbells(), it.plates()) }

    private fun List<SharedLadderEntity>.dumbbells() =
        firstOrNull { it.key == DUMBBELLS }?.let { LadderSpec.decode(it.ladder) } ?: LadderSpec.DefaultDumbbells

    private fun List<SharedLadderEntity>.plates() =
        firstOrNull { it.key == PLATES }?.let { LadderSpec.decode(it.ladder) as? LadderSpec.Plates } ?: LadderSpec.DefaultPlates

    private fun EquipmentItemEntity.toItem() = LadderSpec.decode(ladder)?.let { EquipmentItem(id, gymId, name, it) }

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
