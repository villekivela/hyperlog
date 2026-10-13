package com.hyperlog.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
) {
    BackHandler(onBack = onBack)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // ListItem's 16 dp inset plus this lines its text up with the 24 dp margin's symbol column.
    val row = Modifier.padding(horizontal = 8.dp)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_gyms)) },
                supportingContent = {
                    Text(
                        if (gymCount == 0) stringResource(R.string.settings_gyms_none)
                        else pluralStringResource(R.plurals.settings_gyms_count, gymCount, gymCount),
                    )
                },
                leadingContent = { SymbolIcon(Symbol.FitnessCenter, null, tint = muted) },
                trailingContent = { SymbolIcon(Symbol.ChevronRight, null, tint = muted) },
                modifier = Modifier.clickable(onClick = onGyms).then(row),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_library)) },
                supportingContent = { Text(stringResource(R.string.settings_library_count, exerciseCount)) },
                leadingContent = { SymbolIcon(Symbol.List, null, tint = muted) },
                trailingContent = { SymbolIcon(Symbol.ChevronRight, null, tint = muted) },
                modifier = Modifier.clickable(onClick = onLibrary).then(row),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_starting_weights)) },
                supportingContent = {
                    when {
                        startingWeights.invalid -> Text(stringResource(R.string.settings_starting_weights_invalid), color = MaterialTheme.colorScheme.error)
                        startingWeights.count == 0 -> Text(stringResource(R.string.settings_starting_weights_none))
                        else -> Text(pluralStringResource(R.plurals.settings_starting_weights_count, startingWeights.count, startingWeights.count))
                    }
                },
                leadingContent = { SymbolIcon(Symbol.History, null, tint = muted) },
                modifier = Modifier.clickable(onClick = onImportStartingWeights).then(row),
            )
            // Backups are a group of their own, set apart by spacing rather than a divider.
            Spacer(Modifier.height(16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_export)) },
                supportingContent = {
                    when (backup) {
                        is BackupState.Exported -> Text(stringResource(R.string.backup_exported, backupTime(backup.at)))
                        BackupState.ExportFailed -> Text(stringResource(R.string.backup_export_failed), color = MaterialTheme.colorScheme.error)
                        else -> Text(stringResource(R.string.backup_export_about))
                    }
                },
                leadingContent = { SymbolIcon(Symbol.Download, null, tint = muted) },
                modifier = Modifier.clickable(onClick = onExport).then(row),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_restore)) },
                supportingContent = {
                    if (backup is BackupState.Refused) Text(refusal(backup.reason), color = MaterialTheme.colorScheme.error)
                    else Text(stringResource(R.string.backup_restore_about))
                },
                leadingContent = { SymbolIcon(Symbol.RestorePage, null, tint = muted) },
                modifier = Modifier.clickable(onClick = onRestore).then(row),
            )
            // About the two rows above, lined up with their text: 24 dp margin, 24 dp symbol, 16 dp gap.
            Text(
                stringResource(R.string.backup_auto),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(start = 64.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            )
            // Updates come last, after the footnote's 24 dp: a switch, read once a day when the app opens.
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_check_updates)) },
                supportingContent = { Text(stringResource(R.string.settings_check_updates_about)) },
                leadingContent = { SymbolIcon(Symbol.SystemUpdate, null, tint = muted) },
                trailingContent = { Switch(checked = checkForUpdates, onCheckedChange = null) },
                modifier = Modifier.toggleable(checkForUpdates, role = Role.Switch, onValueChange = onCheckForUpdates).then(row),
            )
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
