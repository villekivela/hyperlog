package com.hyperlog.app.ui.gym

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.Gyms
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Gyms, their equipment, and the shared dumbbells and plates, for the settings screens. */
class GymsViewModel(application: Application) : AndroidViewModel(application), GymActions {
    private val store = Gyms(HyperlogDatabase.get(application))

    val gyms: StateFlow<List<Gym>> = store.gyms.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dumbbells: StateFlow<LadderSpec> = store.dumbbells.stateIn(viewModelScope, SharingStarted.Eagerly, LadderSpec.DefaultDumbbells)
    val plates: StateFlow<LadderSpec.Plates> = store.plates.stateIn(viewModelScope, SharingStarted.Eagerly, LadderSpec.DefaultPlates)

    override fun addGym(name: String, then: (Long) -> Unit) {
        viewModelScope.launch { then(store.addGym(name)) }
    }

    override fun renameGym(id: Long, name: String) = run { store.renameGym(id, name) }
    override fun deleteGym(id: Long) = run { store.deleteGym(id) }
    override fun saveItem(item: EquipmentItem) = run { store.saveItem(item) }
    override fun deleteItem(id: Long) = run { store.deleteItem(id) }
    override fun setDumbbells(spec: LadderSpec) = run { store.setDumbbells(spec) }
    override fun setPlates(spec: LadderSpec.Plates) = run { store.setPlates(spec) }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

interface GymActions {
    fun addGym(name: String, then: (Long) -> Unit)
    fun renameGym(id: Long, name: String)
    fun deleteGym(id: Long)
    fun saveItem(item: EquipmentItem)
    fun deleteItem(id: Long)
    fun setDumbbells(spec: LadderSpec)
    fun setPlates(spec: LadderSpec.Plates)
}
