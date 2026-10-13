package com.hyperlog.app.ui.gym

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Every gym, then the dumbbells, plates and specialty bars all gyms share. A gym opens its
 * equipment; the shared ladders open the ladder editor, a bar a field for its weight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymsScreen(
    gyms: List<Gym>,
    dumbbells: LadderSpec,
    plates: LadderSpec.Plates,
    bars: Map<Equipment, Double>,
    onAddGym: (String) -> Unit,
    onOpenGym: (Long) -> Unit,
    onDumbbells: () -> Unit,
    onPlates: () -> Unit,
    onBar: (Equipment, Double) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var naming by rememberSaveable { mutableStateOf(false) }
    var weighing by rememberSaveable { mutableStateOf<Equipment?>(null) }
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
                    symbol = null,
                    onClick = { onOpenGym(gym.id) },
                )
            }
            AddRow(stringResource(R.string.gym_add)) { naming = true }

            SectionLabel(stringResource(R.string.gym_shared))
            Row(stringResource(R.string.gym_dumbbells), dumbbells.ladder().preview(), symbol = null, onClick = onDumbbells)
            Row(stringResource(R.string.gym_plates), plates.summary(), symbol = null, onClick = onPlates)
            bars.forEach { (bar, kg) ->
                Row(bar.label, stringResource(R.string.gym_bar_summary, formatKg(kg)), symbol = null, onClick = { weighing = bar })
            }
        }
    }
    weighing?.let { bar ->
        BarDialog(bar.label, bars[bar] ?: 0.0, onDone = { kg -> weighing = null; onBar(bar, kg) }, onDismiss = { weighing = null })
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

/**
 * One gym's equipment, each with its ladder, then its usual days. Rename and delete sit in the
 * top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymScreen(
    gym: Gym,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onWeekdays: (Set<DayOfWeek>) -> Unit,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onBack: () -> Unit,
    /** Exercise names, for the exercises the gym has no equipment for. */
    exerciseNames: Map<String, String> = emptyMap(),
    /** The gym has the equipment for this exercise after all. */
    onRestore: (String) -> Unit = {},
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

            SectionLabel(stringResource(R.string.gym_days))
            WeekdayPicker(gym.weekdays, onWeekdays)
            Text(
                stringResource(R.string.gym_days_body, gym.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 64.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            )

            if (gym.missing.isNotEmpty()) {
                SectionLabel(stringResource(R.string.gym_missing))
                gym.missing.map { it to (exerciseNames[it] ?: it) }.sortedBy { it.second }.forEach { (id, name) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = { SymbolIcon(Symbol.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = {
                            IconButton(onClick = { onRestore(id) }) {
                                SymbolIcon(Symbol.Close, stringResource(R.string.gym_missing_restore, name))
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    stringResource(R.string.gym_missing_body, gym.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 64.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                )
            }
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

/**
 * The weekday plan: seven round toggles, Monday first, filled when the gym is planned for that
 * day. The first lines up with the text column; the rest spread to the edge (a 360 dp phone still
 * fits all seven).
 */
@Composable
private fun WeekdayPicker(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(start = 64.dp, end = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        DayOfWeek.entries.forEach { day ->
            val on = day in days
            run {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (on) cs.primary else Color.Transparent)
                        .border(1.dp, if (on) cs.primary else cs.outlineVariant, CircleShape)
                        .toggleable(on, role = Role.Checkbox) { onChange(if (it) days + day else days - day) }
                        .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                    )
                }
            }
        }
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
            // In the rows' text column: 8 + 16 inset, 24 symbol, 16 gap.
            modifier = Modifier.padding(start = 64.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

/** A specialty bar's own weight, typed on the system keyboard like the rest of setup. */
@Composable
private fun BarDialog(title: String, kg: Double, onDone: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(formatKg(kg)) }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 100 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.gym_bar_weight)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onDone) }, enabled = value != null) { Text(stringResource(R.string.gym_bar_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.gym_cancel)) } },
    )
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
