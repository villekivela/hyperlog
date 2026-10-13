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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
 * Settings: gyms and their equipment, the exercise library, backups, the one-off import of
 * starting weights from the old app, and "Check for updates".
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
    onExport: () -> Unit = {},
    onRestore: () -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onDismissBackup: () -> Unit = {},
    checkForUpdates: Boolean = true,
    onCheckForUpdates: (Boolean) -> Unit = {},
    /** "Check for updates now", and what it found; null before it is tapped. */
    onCheckNow: () -> Unit = {},
    checkedNow: com.hyperlog.app.update.UpdateViewModel.CheckedNow? = null,
) {
    BackHandler(onBack = onBack)
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
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) }
                },
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
            // Backups are a group of their own, a card apart from the setup rows.
            GroupHead(stringResource(R.string.settings_backups))
            val export = when (backup) {
                is BackupState.Exported -> stringResource(R.string.backup_exported, backupTime(backup.at))
                BackupState.ExportFailed -> stringResource(R.string.backup_export_failed)
                else -> stringResource(R.string.backup_export_about)
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
            Spacer(Modifier.height(GroupGap))
        }
    }
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
