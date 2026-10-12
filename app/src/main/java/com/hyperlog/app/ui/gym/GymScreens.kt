package com.hyperlog.app.ui.gym

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.ui.session.Margin
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

/**
 * Every gym, then the dumbbells and plates all gyms share. A gym opens its equipment; the shared
 * ladders open the ladder editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymsScreen(
    gyms: List<Gym>,
    dumbbells: LadderSpec,
    plates: LadderSpec.Plates,
    onAddGym: (String) -> Unit,
    onOpenGym: (Long) -> Unit,
    onDumbbells: () -> Unit,
    onPlates: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var naming by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gyms)) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            gyms.forEach { gym ->
                Row(
                    headline = gym.name,
                    supporting = pluralStringResource(R.plurals.gym_item_count, gym.items.size, gym.items.size),
                    symbol = Symbol.FitnessCenter,
                    onClick = { onOpenGym(gym.id) },
                )
            }
            AddRow(stringResource(R.string.gym_add)) { naming = true }

            SectionLabel(stringResource(R.string.gym_shared))
            Row(stringResource(R.string.gym_dumbbells), dumbbells.ladder().preview(), Symbol.FitnessCenter, onClick = onDumbbells)
            Row(stringResource(R.string.gym_plates), plates.summary(), Symbol.FitnessCenter, onClick = onPlates)
        }
    }
    if (naming) {
        NameDialog(
            title = stringResource(R.string.gym_add),
            initial = "",
            confirm = stringResource(R.string.gym_add_confirm),
            onDone = { naming = false; onAddGym(it) },
            onDismiss = { naming = false },
        )
    }
}

/** One gym's equipment, each with its ladder. Rename and delete sit in the top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymScreen(
    gym: Gym,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gym.name) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { renaming = true }) { SymbolIcon(Symbol.Edit, stringResource(R.string.gym_rename)) }
                    IconButton(onClick = { deleting = true }) { SymbolIcon(Symbol.Delete, stringResource(R.string.gym_delete)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            gym.items.forEach { item ->
                Row(item.name, item.ladder.ladder().preview(), symbol = null, onClick = { onOpenItem(item.id) })
            }
            AddRow(stringResource(R.string.gym_add_item), onClick = onAddItem)
        }
    }
    if (renaming) {
        NameDialog(
            title = stringResource(R.string.gym_rename),
            initial = gym.name,
            confirm = stringResource(R.string.gym_rename_confirm),
            onDone = { renaming = false; onRename(it) },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.gym_delete_title, gym.name)) },
            text = { Text(stringResource(R.string.gym_delete_text)) },
            confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.gym_delete_confirm)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.gym_keep)) } },
        )
    }
}

/** `20 kg bar · 20 20 20 15 10 5 2.5 1.25`: the base, then one number per pair. */
internal fun LadderSpec.Plates.summary(): String =
    "${formatKg(baseKg)} kg bar · " + pairs.sortedDescending().joinToString(" ") { formatKg(it) }

@Composable
internal fun Row(headline: String, supporting: String, symbol: Symbol?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting, style = MaterialTheme.typography.bodyMedium.merge(com.hyperlog.app.ui.session.Tnum)) },
        // Rows without a symbol keep its column, so every name lines up with the add row's label.
        leadingContent = {
            if (symbol != null) SymbolIcon(symbol, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) else Spacer(Modifier.width(24.dp))
        },
        trailingContent = { SymbolIcon(Symbol.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, color = MaterialTheme.colorScheme.primary) },
        leadingContent = { SymbolIcon(Symbol.Add, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Margin, top = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
internal fun NameDialog(title: String, initial: String, confirm: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.gym_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.gym_cancel)) } },
    )
}
