package com.hyperlog.app.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperlog.app.coach.ClaudeClient
import com.hyperlog.app.coach.ClaudeKeys
import com.hyperlog.app.coach.CoachCost
import com.hyperlog.app.coach.CoachError
import com.hyperlog.app.data.HyperlogDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Claude key (SPEC, Coaching → With a Claude key): a key typed in is [check]ed, then
 * saved; a refused one isn't. The status carries the key's ending, never the key.
 */
class ClaudeKeySetup(private val keys: ClaudeKeys, cost: CoachCost, private val check: suspend (String) -> Unit) {
    private val checking = MutableStateFlow(KeyCheck.Idle)

    val status: Flow<ClaudeKeyStatus> = combine(keys.ending, cost.thisMonth, checking) { ending, month, check ->
        ClaudeKeyStatus(ending, month, check)
    }

    suspend fun save(key: String) {
        checking.value = KeyCheck.Checking
        Log.w(TAG, "Checking a key of ${key.length} characters: ${shape(key)}")
        try {
            check(key)
        } catch (_: CoachError.SpendLimit) {
            // The key is good; the Console's limit only stops check-ins until it resets.
        } catch (_: CoachError.KeyRefused) {
            checking.value = KeyCheck.Refused
            return
        } catch (_: CoachError) {
            checking.value = KeyCheck.Unreachable
            return
        }
        checking.value = try {
            withContext(Dispatchers.IO) { keys.save(key) }
            KeyCheck.Idle
        } catch (_: Exception) {
            // Keystore or the disk failed; nothing was saved.
            KeyCheck.NotSaved
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) { keys.remove() }

    /**
     * Whether [key] holds only the characters a key is made of, so a paste with a hidden character
     * shows in Logcat. Never the key itself.
     */
    private fun shape(key: String): String {
        val others = key.count { it !in KEY_CHARACTERS }
        return if (others == 0) "only key characters" else "not only key characters ($others other)"
    }

    companion object {
        const val TAG = "ClaudeKeySetup"
        private val KEY_CHARACTERS = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '-' + '_'
    }

    /** The field was closed: forget its check. */
    fun dismiss() {
        checking.value = KeyCheck.Idle
    }
}

class ClaudeKeyViewModel(application: Application) : AndroidViewModel(application) {
    private val setup = ClaudeKeySetup(ClaudeKeys.get(application), CoachCost(HyperlogDatabase.get(application))) { key ->
        ClaudeClient(key).checkKey()
    }

    // While Settings shows it: coming back reads the month afresh, so a new month starts at nothing.
    val status: StateFlow<ClaudeKeyStatus> = setup.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClaudeKeyStatus())

    fun save(key: String) {
        viewModelScope.launch { setup.save(key) }
    }

    fun remove() {
        viewModelScope.launch { setup.remove() }
    }

    fun dismiss() = setup.dismiss()
}
