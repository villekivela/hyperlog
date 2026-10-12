package com.hyperlog.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.legacy.LegacyBackup
import com.hyperlog.app.legacy.StartingWeights
import com.hyperlog.app.library.LibrarySeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The one-off import of starting weights from a legacy backup the user picks. */
class StartingWeightsViewModel(application: Application) : AndroidViewModel(application) {
    private val weights = StartingWeights(HyperlogDatabase.get(application))
    private val invalid = MutableStateFlow(false)

    val status: StateFlow<StartingWeightsStatus> = combine(weights.count, invalid) { count, bad -> StartingWeightsStatus(count, bad) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StartingWeightsStatus(0, false))

    /** Reads the backup at [uri]; a file that isn't one leaves the earlier import in place. */
    fun import(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                val text = runCatching { app.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
                text?.let { weights.import(it, LibrarySeed.fromAssets(app)) } ?: LegacyBackup.Result.Invalid
            }
            invalid.value = result is LegacyBackup.Result.Invalid
        }
    }
}
