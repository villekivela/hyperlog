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

/** Gyms with their equipment, dumbbells and plates, and the shared bars' weights, for the settings screens. */
class GymsViewModel(application: Application) : AndroidViewModel(application), GymActions {
    private val db = HyperlogDatabase.get(application)
    private val store = Gyms(db)
    private val programs = com.hyperlog.app.program.Programs(db)
    private val library = com.hyperlog.app.library.ExerciseLibrary(db)

    val gyms: StateFlow<List<Gym>> = store.gyms.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** The barbell's weight, then the EZ, trap and safety bars'. */
    val bars: StateFlow<Map<Equipment, Double>> = kotlinx.coroutines.flow.combine(store.barbell, store.bars) { barbell, bars -> mapOf(Equipment.Barbell to barbell) + bars }
        .stateIn(viewModelScope, SharingStarted.Eagerly, mapOf(Equipment.Barbell to LadderSpec.DefaultPlates.baseKg) + SharedLadders.DefaultBars)

    /** The active program's title and what it still needs at each gym, by gym id; null without a program. */
    val programSetups: StateFlow<Pair<String, Map<Long, ProgramSetup>>?> =
        kotlinx.coroutines.flow.combine(store.gyms, programs.active, library.exercises) { gyms, active, exercises ->
            active?.let { a ->
                val byId = exercises.associateBy { it.id }
                a.program.title to gyms.associate { it.id to programSetup(a.program, byId, it) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _importErrors = kotlinx.coroutines.flow.MutableStateFlow<List<String>?>(null)

    /** The problems of the last gym file rejected, until closed. */
    val importErrors: StateFlow<List<String>?> = _importErrors

    /** Reads the gym file at [uri] and adds its gym if the file is valid; [then] gets the new gym's id. */
    fun importGym(uri: android.net.Uri, then: (Long) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
            }
            if (text == null) {
                _importErrors.value = listOf(app.getString(com.hyperlog.app.R.string.program_unreadable))
                return@launch
            }
            when (val result = store.import(text)) {
                is Gyms.Import.Added -> { _importErrors.value = null; then(result.gymId) }
                is Gyms.Import.Invalid -> _importErrors.value = result.errors
            }
        }
    }

    fun closeImportErrors() {
        _importErrors.value = null
    }

    override fun addProgramEquipment(gymId: Long, stack: LadderSpec) = run {
        programSetups.value?.second?.get(gymId)?.let { store.addProgramEquipment(gymId, it, stack) }
    }

    override fun addGym(name: String, then: (Long) -> Unit) {
        viewModelScope.launch { then(store.addGym(name)) }
    }

    override fun renameGym(id: Long, name: String) = run { store.renameGym(id, name) }
    override fun deleteGym(id: Long) = run { store.deleteGym(id) }
    override fun setPinPlates(id: Long, plates: List<Double>) = run { store.setPinPlates(id, plates) }
    override fun unmarkMissing(gymId: Long, exerciseId: String) = run { store.unmarkMissing(gymId, exerciseId) }
    override fun saveItem(item: EquipmentItem) = run { store.saveItem(item) }
    override fun deleteItem(id: Long) = run { store.deleteItem(id) }
    override fun renameItem(id: Long, name: String) = run { store.renameItem(id, name) }
    override fun setDumbbells(gymId: Long, spec: LadderSpec) = run { store.setDumbbells(gymId, spec) }
    override fun setPlates(gymId: Long, pairs: List<Double>) = run { store.setPlates(gymId, pairs) }
    override fun setBar(bar: Equipment, kg: Double) = run { if (bar == Equipment.Barbell) store.setBarbell(kg) else store.setBar(bar, kg) }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

interface GymActions {
    fun addGym(name: String, then: (Long) -> Unit)
    fun renameGym(id: Long, name: String)
    fun deleteGym(id: Long)
    fun setPinPlates(id: Long, plates: List<Double>)
    fun addProgramEquipment(gymId: Long, stack: LadderSpec)
    fun unmarkMissing(gymId: Long, exerciseId: String)
    fun saveItem(item: EquipmentItem)
    fun deleteItem(id: Long)
    fun renameItem(id: Long, name: String)
    fun setDumbbells(gymId: Long, spec: LadderSpec)
    fun setPlates(gymId: Long, pairs: List<Double>)
    fun setBar(bar: Equipment, kg: Double)
}
