package com.hyperlog.app.ui.settings

import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.SegmentedList
import com.hyperlog.app.ui.theme.SegmentedRow
import com.hyperlog.app.ui.theme.GroupGap
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import com.hyperlog.app.ui.theme.Group
import com.hyperlog.app.ui.theme.GroupHead
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.hyperlog.app.coach.CoachCost
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.backup.Backups
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where the starting-weights import stands: how many exercises have one, and whether the last file was rejected. */
data class StartingWeightsStatus(val count: Int, val invalid: Boolean)

/** Where the Claude key's check stands: a key being entered is checked before it is saved. */
enum class KeyCheck { Idle, Checking, Refused, Unreachable, NotSaved }

/**
 * The Claude key as Settings shows it: its last 4 characters ([ending], null with no key), this
 * month's cost in millionths of a dollar, and the check of a key being entered.
 */
data class ClaudeKeyStatus(val ending: String? = null, val monthCost: Long = 0, val check: KeyCheck = KeyCheck.Idle)

/** Where a backup export or restore stands, as Settings shows it. */
sealed interface BackupState {
    data object Idle : BackupState
    /** The file was saved at [at] (epoch ms). */
    data class Exported(val at: Long) : BackupState
    data object ExportFailed : BackupState
    /** A readable backup made at [exportedAt] was picked: asking before it replaces anything. */
    data class Confirm(val exportedAt: Long) : BackupState
    /** The picked file can't be restored; nothing changed. */
    data class Refused(val reason: Backups.Reason) : BackupState
    /** Restored: the app starts again on the backup's data. */
    data object Restored : BackupState
}

/**
 * Settings: gyms and their equipment, the exercise library, the one-off import of starting
 * weights from the old app, the Claude key with this month's cost, backups, "Check for updates",
 * and the installed version at the foot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    gymCount: Int,
    exerciseCount: Int,
    onGyms: () -> Unit,
    onLibrary: () -> Unit,
    onBack: () -> Unit,
    startingWeights: StartingWeightsStatus,
    onImportStartingWeights: () -> Unit,
    modifier: Modifier = Modifier,
    backup: BackupState = BackupState.Idle,
    lastExport: Long? = null,
    onExport: () -> Unit = {},
    onRestore: () -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onDismissBackup: () -> Unit = {},
    checkForUpdates: Boolean = true,
    onCheckForUpdates: (Boolean) -> Unit = {},
    /** "Check for updates now", and what it found; null before it is tapped. */
    onCheckNow: () -> Unit = {},
    checkedNow: com.hyperlog.app.update.UpdateViewModel.CheckedNow? = null,
    /** The installed versionName ("0.2.0"), shown at the foot; null shows none. */
    version: String? = null,
    claudeKey: ClaudeKeyStatus = ClaudeKeyStatus(),
    /** Checks a key typed in, then saves it. */
    onSaveKey: (String) -> Unit = {},
    onRemoveKey: () -> Unit = {},
    /** The key's field was closed: forget its check. */
    onDismissKey: () -> Unit = {},
    /** The scripted coach's switch, offered in a debug build only; null hides it. */
    scriptedCoach: Boolean? = null,
    onScriptedCoach: (Boolean) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var enteringKey by rememberSaveable { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Rows sit in card groups; ListItem's own container steps aside for the card's.
    val row = Modifier
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    Scaffold(
        containerColor = Tones.screen,
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                // A destination of the navigation bar: no back arrow (back goes to Today). The bar sets
                // titles 16 dp in; the screen's text edge is 24 dp.
                title = { Text(stringResource(R.string.settings), Modifier.padding(start = 8.dp)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            val error = MaterialTheme.colorScheme.error
            val chevron: @Composable () -> Unit = { SymbolIcon(Symbol.ChevronRight, null, tint = muted) }
            val gyms = if (gymCount == 0) stringResource(R.string.settings_gyms_none) else pluralStringResource(R.plurals.settings_gyms_count, gymCount, gymCount)
            val library = stringResource(R.string.settings_library_count, exerciseCount)
            val weights = when {
                startingWeights.invalid -> stringResource(R.string.settings_starting_weights_invalid)
                startingWeights.count == 0 -> stringResource(R.string.settings_starting_weights_none)
                else -> pluralStringResource(R.plurals.settings_starting_weights_count, startingWeights.count, startingWeights.count)
            }
            val gymsTitle = stringResource(R.string.settings_gyms)
            val libraryTitle = stringResource(R.string.settings_library)
            val weightsTitle = stringResource(R.string.settings_starting_weights)
            SegmentedList {
                row(gymsTitle, gyms, Symbol.FitnessCenter, onClick = onGyms, trailing = chevron)
                row(libraryTitle, library, Symbol.List, onClick = onLibrary, trailing = chevron)
                row(
                    weightsTitle, weights, Symbol.History, onClick = onImportStartingWeights,
                    supportingColor = if (startingWeights.invalid) error else null,
                )
            }
            // The Claude key, with this month's cost under it once there is one.
            GroupHead(stringResource(R.string.settings_coach))
            val keyTitle = stringResource(R.string.claude_key)
            val keyLine = claudeKey.ending?.let { stringResource(R.string.claude_key_ending, it) } ?: stringResource(R.string.claude_key_about)
            val removeLabel = stringResource(R.string.claude_key_remove)
            val keyText = stringResource(R.string.claude_key_text)
            SegmentedList {
                if (claudeKey.ending == null && enteringKey) {
                    item {
                        SegmentedRow(keyTitle, keyText, Symbol.Key, supportingLines = 3)
                        KeyEntry(
                            check = claudeKey.check,
                            onSave = onSaveKey,
                            onCancel = {
                                enteringKey = false
                                onDismissKey()
                            },
                        )
                    }
                } else if (claudeKey.ending == null) {
                    row(keyTitle, keyLine, Symbol.Key, onClick = { enteringKey = true })
                } else {
                    row(keyTitle, keyLine, Symbol.Key, trailing = { TextButton(onClick = onRemoveKey) { Text(removeLabel) } })
                }
                scriptedCoach?.let { on ->
                    item {
                        Box(Modifier.toggleable(on, role = Role.Switch, onValueChange = onScriptedCoach)) {
                            SegmentedRow(
                                stringResource(R.string.settings_scripted_coach), stringResource(R.string.settings_scripted_coach_body), Symbol.Sports,
                                supportingLines = 2,
                                trailing = { Switch(checked = on, onCheckedChange = null) },
                            )
                        }
                    }
                }
            }
            if (claudeKey.ending != null) {
                Text(
                    stringResource(R.string.claude_key_month, CoachCost.dollars(claudeKey.monthCost)),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.padding(start = 84.dp, end = 24.dp, top = 8.dp),
                )
            }
            // Backups are a group of their own, a card apart from the setup rows.
            GroupHead(stringResource(R.string.settings_backups))
            val export = when (backup) {
                is BackupState.Exported -> stringResource(R.string.backup_exported, backupTime(backup.at))
                BackupState.ExportFailed -> stringResource(R.string.backup_export_failed)
                // The last export stays on the row, so a stale backup shows.
                else -> lastExport?.let { stringResource(R.string.backup_exported, backupTime(it)) } ?: stringResource(R.string.backup_export_about)
            }
            val restore = if (backup is BackupState.Refused) refusal(backup.reason) else stringResource(R.string.backup_restore_about)
            val exportTitle = stringResource(R.string.backup_export)
            val restoreTitle = stringResource(R.string.backup_restore)
            SegmentedList {
                row(exportTitle, export, Symbol.Download, onClick = onExport, supportingColor = if (backup == BackupState.ExportFailed) error else null)
                row(restoreTitle, restore, Symbol.RestorePage, onClick = onRestore, supportingColor = if (backup is BackupState.Refused) error else null)
            }
            // About the two rows above, lined up with their text: 12 dp tile, 16 dp inset, 40 dp circle, 16 dp gap.
            Text(
                stringResource(R.string.backup_auto),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(start = 84.dp, end = 24.dp, top = 8.dp, bottom = GroupGap),
            )
            // Updates come last: a switch, read when the app comes to the foreground, and a check now.
            val checkNowTitle = stringResource(R.string.settings_check_now)
            val checkNowLine = when (checkedNow) {
                com.hyperlog.app.update.UpdateViewModel.CheckedNow.Checking -> stringResource(R.string.settings_check_now_checking)
                com.hyperlog.app.update.UpdateViewModel.CheckedNow.Found -> stringResource(R.string.settings_check_now_found)
                com.hyperlog.app.update.UpdateViewModel.CheckedNow.NoneNewer -> stringResource(R.string.settings_check_now_none)
                null -> stringResource(R.string.settings_check_now_about)
            }
            SegmentedList {
                item {
                    Box(Modifier.toggleable(checkForUpdates, role = Role.Switch, onValueChange = onCheckForUpdates)) {
                        SegmentedRow(
                            stringResource(R.string.settings_check_updates), stringResource(R.string.settings_check_updates_about), Symbol.SystemUpdate,
                            trailing = { Switch(checked = checkForUpdates, onCheckedChange = null) },
                        )
                    }
                }
                if (checkForUpdates) {
                    val checking = checkedNow == com.hyperlog.app.update.UpdateViewModel.CheckedNow.Checking
                    row(
                        checkNowTitle,
                        checkNowLine,
                        Symbol.Replay,
                        onClick = if (checking) null else onCheckNow,
                    )
                }
            }
            if (version != null) {
                Text(
                    stringResource(R.string.settings_version, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = GroupGap),
                )
            }
            Spacer(Modifier.height(GroupGap))
        }
    }
    // The field closes once the key is checked and saved.
    LaunchedEffect(claudeKey.ending) { if (claudeKey.ending != null) enteringKey = false }
    if (backup is BackupState.Confirm) {
        AlertDialog(
            onDismissRequest = onDismissBackup,
            icon = { SymbolIcon(Symbol.RestorePage, null) },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_text, backupTime(backup.exportedAt))) },
            confirmButton = { TextButton(onClick = onConfirmRestore) { Text(stringResource(R.string.backup_restore_confirm)) } },
            dismissButton = { TextButton(onClick = onDismissBackup) { Text(stringResource(R.string.backup_cancel)) } },
        )
    }
}

/**
 * The key's field, in its card under the row: the key is checked when saved, and a refused or
 * unreachable check says so under the field. Text fields stay out of dialogs here, where
 * Robolectric's tests can't type into them.
 */
@Composable
private fun KeyEntry(check: KeyCheck, onSave: (String) -> Unit, onCancel: () -> Unit) {
    // Held only while the field is open, never saved with the screen's state.
    var typed by remember { mutableStateOf("") }
    val checking = check == KeyCheck.Checking
    Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 8.dp)) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            label = { Text(stringResource(R.string.claude_key_field)) },
            singleLine = true,
            enabled = !checking,
            isError = check == KeyCheck.Refused || check == KeyCheck.Unreachable || check == KeyCheck.NotSaved,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            supportingText = when (check) {
                KeyCheck.Idle -> null
                KeyCheck.Checking -> { { Text(stringResource(R.string.claude_key_checking)) } }
                KeyCheck.Refused -> { { Text(stringResource(R.string.claude_key_refused)) } }
                KeyCheck.Unreachable -> { { Text(stringResource(R.string.claude_key_unreachable)) } }
                KeyCheck.NotSaved -> { { Text(stringResource(R.string.claude_key_not_saved)) } }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.claude_key_cancel)) }
            TextButton(onClick = { onSave(typed.trim()) }, enabled = typed.isNotBlank() && !checking) {
                Text(stringResource(R.string.claude_key_save))
            }
        }
    }
}

@Composable
private fun refusal(reason: Backups.Reason) = stringResource(
    when (reason) {
        Backups.Reason.NotABackup -> R.string.backup_refused_not_a_backup
        Backups.Reason.Newer -> R.string.backup_refused_newer
        Backups.Reason.Older -> R.string.backup_refused_older
        Backups.Reason.Damaged -> R.string.backup_refused_damaged
    },
)

/** `Tue 13 Oct, 14:05`. */
internal fun backupTime(at: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
