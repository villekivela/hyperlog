package com.hyperlog.app.ui.gym

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.Gyms
import com.hyperlog.app.gym.ProgramSetup
import com.hyperlog.app.gym.programSetup
import com.hyperlog.app.gym.SharedLadders
import com.hyperlog.app.library.Equipment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/** Gyms with their equipment and dumbbells, and the shared plates and bars, for the settings screens. */
class GymsViewModel(application: Application) : AndroidViewModel(application), GymActions {
    private val db = HyperlogDatabase.get(application)
    private val store = Gyms(db)
    private val programs = com.hyperlog.app.program.Programs(db)
    private val library = com.hyperlog.app.library.ExerciseLibrary(db)

    val gyms: StateFlow<List<Gym>> = store.gyms.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val plates: StateFlow<LadderSpec.Plates> = store.plates.stateIn(viewModelScope, SharingStarted.Eagerly, LadderSpec.DefaultPlates)
    val bars: StateFlow<Map<Equipment, Double>> = store.bars.stateIn(viewModelScope, SharingStarted.Eagerly, SharedLadders.DefaultBars)

    /** The active program's title and what it still needs at each gym, by gym id; null without a program. */
    val programSetups: StateFlow<Pair<String, Map<Long, ProgramSetup>>?> =
        kotlinx.coroutines.flow.combine(store.gyms, programs.active, library.exercises) { gyms, active, exercises ->
            active?.let { a ->
                val byId = exercises.associateBy { it.id }
                a.program.title to gyms.associate { it.id to programSetup(a.program, byId, it) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override fun addProgramEquipment(gymId: Long, stack: LadderSpec) = run {
        programSetups.value?.second?.get(gymId)?.let { store.addProgramEquipment(gymId, it, stack) }
    }

    override fun addGym(name: String, then: (Long) -> Unit) {
        viewModelScope.launch { then(store.addGym(name)) }
    }

    override fun renameGym(id: Long, name: String) = run { store.renameGym(id, name) }
    override fun deleteGym(id: Long) = run { store.deleteGym(id) }
    override fun setWeekdays(id: Long, days: Set<DayOfWeek>) = run { store.setWeekdays(id, days) }
    override fun setPinPlates(id: Long, plates: List<Double>) = run { store.setPinPlates(id, plates) }
    override fun unmarkMissing(gymId: Long, exerciseId: String) = run { store.unmarkMissing(gymId, exerciseId) }
    override fun saveItem(item: EquipmentItem) = run { store.saveItem(item) }
    override fun deleteItem(id: Long) = run { store.deleteItem(id) }
    override fun setDumbbells(gymId: Long, spec: LadderSpec) = run { store.setDumbbells(gymId, spec) }
    override fun setPlates(spec: LadderSpec.Plates) = run { store.setPlates(spec) }
    override fun setBar(bar: Equipment, kg: Double) = run { store.setBar(bar, kg) }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

interface GymActions {
    fun addGym(name: String, then: (Long) -> Unit)
    fun renameGym(id: Long, name: String)
    fun deleteGym(id: Long)
    fun setWeekdays(id: Long, days: Set<DayOfWeek>)
    fun setPinPlates(id: Long, plates: List<Double>)
    fun addProgramEquipment(gymId: Long, stack: LadderSpec)
    fun unmarkMissing(gymId: Long, exerciseId: String)
    fun saveItem(item: EquipmentItem)
    fun deleteItem(id: Long)
    fun setDumbbells(gymId: Long, spec: LadderSpec)
    fun setPlates(spec: LadderSpec.Plates)
    fun setBar(bar: Equipment, kg: Double)
}
