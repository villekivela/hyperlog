package com.hyperlog.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

/** Where the starting-weights import stands: how many exercises have one, and whether the last file was rejected. */
data class StartingWeightsStatus(val count: Int, val invalid: Boolean)

/**
 * Settings: gyms and their equipment, the exercise library, and the one-off import of starting
 * weights from the old app. Backups join in a later ticket.
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
) {
    BackHandler(onBack = onBack)
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_gyms)) },
                supportingContent = {
                    Text(
                        if (gymCount == 0) stringResource(R.string.settings_gyms_none)
                        else pluralStringResource(R.plurals.settings_gyms_count, gymCount, gymCount),
                    )
                },
                leadingContent = { SymbolIcon(Symbol.FitnessCenter, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingContent = { SymbolIcon(Symbol.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable(onClick = onGyms).padding(horizontal = 8.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_library)) },
                supportingContent = { Text(stringResource(R.string.settings_library_count, exerciseCount)) },
                leadingContent = { SymbolIcon(Symbol.List, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingContent = { SymbolIcon(Symbol.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                // ListItem's 16 dp inset plus this lines its text up with the 24 dp margin's symbol column.
                modifier = Modifier.clickable(onClick = onLibrary).padding(horizontal = 8.dp),
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
                leadingContent = { SymbolIcon(Symbol.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable(onClick = onImportStartingWeights).padding(horizontal = 8.dp),
            )
        }
    }
}
