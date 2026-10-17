package com.hyperlog.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.backup.Backups
import com.hyperlog.app.data.HyperlogDatabase
import com.hyperlog.app.library.ExerciseLibrary
import com.hyperlog.app.library.LibrarySeed
import com.hyperlog.app.session.SessionRuntime
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Export to a file the user picks, and restore from one after asking (SPEC, Backups). */
class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val db = HyperlogDatabase.get(application)
    private val backups = Backups(db)
    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    /** When the last export was saved, for the Export row (null before the first). */
    val lastExport: StateFlow<Long?> = backups.lastExport.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The picked file, held while the user is asked. */
    private var picked: String? = null

    /** The name the system's save dialog suggests. */
    fun fileName(): String = Backups.fileName(LocalDate.now())

    fun export(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val file = backups.export()
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(file.toByteArray()) }
                }.isSuccess
            }
            val at = System.currentTimeMillis()
            if (saved) backups.recordExport(at)
            _state.value = if (saved) BackupState.Exported(at) else BackupState.ExportFailed
        }
    }

    /** Reads the picked file; a readable backup asks before anything is replaced. */
    fun pick(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val text = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
            }
            picked = text
            _state.value = when (val contents = text?.let { backups.read(it) }) {
                is Backups.Contents.Readable -> BackupState.Confirm(contents.exportedAt)
                is Backups.Contents.Unreadable -> BackupState.Refused(contents.reason)
                null -> BackupState.Refused(Backups.Reason.NotABackup)
            }
        }
    }

    fun dismiss() {
        picked = null
        _state.value = BackupState.Idle
    }

    /** Replaces everything with the picked backup, then has the running session read it again. */
    fun restore() {
        val file = picked ?: return
        picked = null
        viewModelScope.launch {
            val app = getApplication<Application>()
            when (val result = backups.restore(file)) {
                Backups.Result.Restored -> {
                    // A backup from an older app carries its library; the shipped one updates it as on any start.
                    ExerciseLibrary(db).sync(withContext(Dispatchers.IO) { LibrarySeed.fromAssets(app) })
                    SessionRuntime.get(app).logger.restored()
                    _state.value = BackupState.Restored
                }
                is Backups.Result.Invalid -> _state.value = BackupState.Refused(result.reason)
            }
        }
    }
}
