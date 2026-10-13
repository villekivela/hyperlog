package com.hyperlog.app.ui.gym

import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import androidx.compose.material3.ListItemDefaults
import com.hyperlog.app.ui.theme.Group
import com.hyperlog.app.ui.theme.SegmentedList
import com.hyperlog.app.ui.theme.GroupHead
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.testTag
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
import com.hyperlog.app.domain.summary
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.equipmentKind
import com.hyperlog.app.gym.sections
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Every gym, then the plates and specialty bars all gyms share. A gym opens its
 * equipment; the shared ladders open the ladder editor, a bar a field for its weight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymsScreen(
    gyms: List<Gym>,
    plates: LadderSpec.Plates,
    bars: Map<Equipment, Double>,
    onAddGym: (String) -> Unit,
    onOpenGym: (Long) -> Unit,
    onPlates: () -> Unit,
    onBar: (Equipment, Double) -> Unit,
    onBack: () -> Unit,
    /** Picks a gym file to import (docs/gym-format.md). */
    onImport: () -> Unit = {},
    /** The last gym file's problems, shown in place of the list until closed. */
    importErrors: List<String>? = null,
    onCloseImportErrors: () -> Unit = {},
) {
    BackHandler(onBack = { if (importErrors != null) onCloseImportErrors() else onBack() })
    var naming by rememberSaveable { mutableStateOf(false) }
    var weighing by rememberSaveable { mutableStateOf<Equipment?>(null) }
    Scaffold(
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(R.string.gyms)) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (importErrors != null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                com.hyperlog.app.ui.program.ImportErrors(importErrors, onClose = onCloseImportErrors, onRetry = onImport)
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            val chevron = chevron()
            val add = stringResource(R.string.gym_add)
            val import = stringResource(R.string.gym_import)
            val counts = gyms.map { pluralStringResource(R.plurals.gym_item_count, it.items.size, it.items.size) }
            Spacer(Modifier.height(8.dp))
            SegmentedList {
                gyms.forEachIndexed { i, gym -> row(gym.name, counts[i], Symbol.LocationOn, onClick = { onOpenGym(gym.id) }, trailing = chevron) }
                addRow(add, Symbol.Add) { naming = true }
                addRow(import, Symbol.FileOpen, onImport)
            }

            SectionLabel(stringResource(R.string.gym_shared))
            val barLines = bars.values.map { stringResource(R.string.gym_bar_summary, formatKg(it)) }
            val platesTitle = stringResource(R.string.gym_plates)
            SegmentedList {
                row(platesTitle, plates.platesLine(), Symbol.FitnessCenter, onClick = onPlates, trailing = chevron)
                bars.entries.forEachIndexed { i, (bar, _) -> row(bar.label, barLines[i], Symbol.FitnessCenter, onClick = { weighing = bar }, trailing = chevron) }
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
    /** Sets the gym's small plates for the pin of its stacks. */
    onPinPlates: (List<Double>) -> Unit = {},
    /** Opens the gym's dumbbell rack. */
    onDumbbells: () -> Unit = {},
    /** What the active program still needs here; null without a program. */
    programSetup: com.hyperlog.app.gym.ProgramSetup? = null,
    programTitle: String? = null,
    /** Adds [programSetup] on the gym's stack, chosen here the first time. */
    onAddProgramEquipment: (LadderSpec) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var renaming by rememberSaveable { mutableStateOf(false) }
    var pinning by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(gym.name) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { renaming = true }) { SymbolIcon(Symbol.Edit, stringResource(R.string.gym_rename)) }
                    IconButton(onClick = { deleting = true }) { SymbolIcon(Symbol.Delete, stringResource(R.string.gym_delete)) }
                },
            )
        },
        // The screen's one primary action, reachable however long the list (SPEC, Gyms and equipment).
        floatingActionButton = {
            // The content-slot form: always expanded, so its label is there from the first frame.
            androidx.compose.material3.ExtendedFloatingActionButton(onClick = onAddItem) {
                SymbolIcon(Symbol.Add, null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.gym_add_item))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (programSetup != null && !programSetup.isEmpty && programTitle != null) {
                ProgramEquipmentCard(programSetup, programTitle, gym.defaultStack, onAddProgramEquipment)
            }
            val chevron = chevron()
            // One section per kind, each in name order; a kind the gym has none of is left out.
            gym.sections().forEach { (kind, items) ->
                SectionLabel(stringResource(kind.title))
                val lines = items.map { item -> itemLine(item, gym.pinPlates) }
                SegmentedList {
                    items.forEachIndexed { i, item ->
                        // Machines open their setup; bars, racks and benches only say what they are.
                        val open = if (item.kind == com.hyperlog.app.gym.EquipmentItem.MACHINE) ({ onOpenItem(item.id) }) else null
                        row(item.name, lines[i], item.symbol, onClick = open, trailing = if (open != null) chevron else null)
                    }
                }
            }

            SectionLabel(stringResource(R.string.gym_free_weights))
            val dumbbells = gym.dumbbells.summary()
            val pins = pinPlatesText(gym.pinPlates)
            val dumbbellsTitle = stringResource(R.string.gym_dumbbells)
            val pinsTitle = stringResource(R.string.gym_pin_plates_row)
            SegmentedList {
                row(dumbbellsTitle, dumbbells, Symbol.Exercise, onClick = onDumbbells, trailing = chevron)
                row(pinsTitle, pins, Symbol.PushPin, onClick = { pinning = true }, trailing = chevron)
            }

            SectionLabel(stringResource(R.string.gym_days))
            SegmentedList {
                item {
                    Spacer(Modifier.height(16.dp))
                    WeekdayPicker(gym.weekdays, onWeekdays)
                    Text(
                        stringResource(R.string.gym_days_body, gym.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 72.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    )
                }
            }

            if (gym.missing.isNotEmpty()) {
                SectionLabel(stringResource(R.string.gym_missing))
                val missing = gym.missing.map { it to (exerciseNames[it] ?: it) }.sortedBy { it.second }
                val restore = missing.map { (_, name) -> stringResource(R.string.gym_missing_restore, name) }
                SegmentedList {
                    missing.forEachIndexed { i, (id, name) ->
                        row(name, null, Symbol.Block, trailing = {
                            IconButton(onClick = { onRestore(id) }) { SymbolIcon(Symbol.Close, restore[i]) }
                        })
                    }
                }
                Text(
                    stringResource(R.string.gym_missing_body, gym.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 84.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                )
            }
            // Clears the extended FAB, so the last row can scroll out from under it.
            Spacer(Modifier.height(96.dp))
        }
    }
    if (pinning) {
        PinPlatesDialog(gym.pinPlates, onDone = { pinning = false; onPinPlates(it) }, onDismiss = { pinning = false })
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
        Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp),
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

/**
 * "Add the equipment your program uses": what it adds (`6 machines and a cable station for Full
 * Body`) and, the first time, which stack the gym's machines are, then **Add**. A filled
 * `primaryContainer` card, the screen's one thing to act on.
 */
@Composable
private fun ProgramEquipmentCard(setup: com.hyperlog.app.gym.ProgramSetup, program: String, chosen: LadderSpec?, onAdd: (LadderSpec) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var lb by rememberSaveable { mutableStateOf(false) }
    com.hyperlog.app.ui.theme.SectionCard(color = cs.primaryContainer, padding = 16.dp, modifier = Modifier.testTag(ProgramEquipmentTag)) {
        Text(stringResource(R.string.gym_program_title), style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
        val machines = setup.machines.size
        val what = when {
            machines > 0 && setup.newStation -> pluralStringResource(R.plurals.gym_program_machines_station, machines, machines, program)
            machines > 0 -> pluralStringResource(R.plurals.gym_program_machines, machines, machines, program)
            setup.newStation -> stringResource(R.string.gym_program_station, program)
            else -> stringResource(R.string.gym_program_cables, setup.cables.size, program)
        }
        Text(what, style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer, modifier = Modifier.padding(top = 4.dp))
        if (chosen == null) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.gym_program_stack), style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            com.hyperlog.app.ui.theme.ConnectedChoice(
                listOf(stringResource(R.string.gym_program_kg), stringResource(R.string.gym_program_lb)),
                if (lb) 1 else 0,
                onSelect = { lb = it == 1 },
            )
            Text(
                stringResource(R.string.gym_program_guess),
                style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer, modifier = Modifier.padding(top = 8.dp),
            )
        }
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            androidx.compose.material3.Button(onClick = { onAdd(chosen ?: if (lb) LadderSpec.LbPinStack else LadderSpec.DefaultPinStack) }) {
                Text(stringResource(R.string.gym_program_add))
            }
        }
    }
}

internal const val ProgramEquipmentTag = "program-equipment"

/** `2.5 + 1.25 kg`, the gym's plates for the pin, or that it has none. */
@Composable
private fun pinPlatesText(plates: List<Double>): String =
    if (plates.isEmpty()) stringResource(R.string.gym_pin_plates_none)
    else stringResource(R.string.gym_pin_plates_list, plates.sortedDescending().joinToString(" + ") { formatKg(it) })

/** The gym's plates for the pin, typed as weights separated by spaces, `2.5 1.25` (or `2,5`). */
@Composable
private fun PinPlatesDialog(plates: List<Double>, onDone: (List<Double>) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(plates.sortedDescending().joinToString(" ") { formatKg(it) }) }
    val value = text.trim().split(Regex("[\\s;]+")).filter { it.isNotEmpty() }.map { it.replace(',', '.').toDoubleOrNull()?.takeIf { kg -> kg > 0 && kg <= 25 } }
        .takeIf { it.none { kg -> kg == null } && it.size <= 6 }?.filterNotNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gym_pin_plates_row)) },
        text = {
            Column {
                Text(stringResource(R.string.gym_pin_plates_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.gym_pin_plates_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onDone) }, enabled = value != null) { Text(stringResource(R.string.gym_bar_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.gym_cancel)) } },
    )
}

/** A section's header on the gym screen. */
private val com.hyperlog.app.gym.EquipmentKind.title: Int
    get() = when (this) {
        com.hyperlog.app.gym.EquipmentKind.Stack -> R.string.gym_section_stacks
        com.hyperlog.app.gym.EquipmentKind.PlateLoaded -> R.string.gym_section_plate_loaded
        com.hyperlog.app.gym.EquipmentKind.Bar -> R.string.gym_section_bars
        com.hyperlog.app.gym.EquipmentKind.RackBench -> R.string.gym_section_racks
    }

/** Each kind's own symbol: a stack, a cable, a plate, a barbell, a bench. */
internal val com.hyperlog.app.gym.EquipmentItem.symbol: Symbol
    get() = when (equipmentKind) {
        com.hyperlog.app.gym.EquipmentKind.Stack -> if (cableStation) Symbol.Cable else Symbol.Stacks
        com.hyperlog.app.gym.EquipmentKind.PlateLoaded -> Symbol.Album
        com.hyperlog.app.gym.EquipmentKind.Bar -> Symbol.FitnessCenter
        com.hyperlog.app.gym.EquipmentKind.RackBench -> Symbol.AirlineSeatFlat
    }

/** An item's line: a machine's ladder (`Every cable exercise · …` for the station), a bar's weight, or that it is a rack or bench. */
@Composable
private fun itemLine(item: com.hyperlog.app.gym.EquipmentItem, pinPlates: List<Double>): String = when (item.equipmentKind) {
    com.hyperlog.app.gym.EquipmentKind.Bar -> stringResource(R.string.gym_kit_bar, formatKg(item.bar!!.second))
    com.hyperlog.app.gym.EquipmentKind.RackBench -> stringResource(R.string.gym_kit_rack)
    else -> item.ladder.summary(pinPlates).let { if (item.cableStation) stringResource(R.string.gym_cable_station, it) else it }
}

/** The rows' trailing chevron. */
@Composable
private fun chevron(): @Composable () -> Unit {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    return { SymbolIcon(Symbol.ChevronRight, null, tint = tint) }
}

/** An add row: its symbol in the tonal circle and its title in `primary`. */
private fun com.hyperlog.app.ui.theme.SegmentedListScope.addRow(title: String, symbol: Symbol, onClick: () -> Unit) =
    item(onClick) { com.hyperlog.app.ui.theme.SegmentedRow(title, symbol = symbol, titleColor = MaterialTheme.colorScheme.primary) }

/** `20 kg bar · 20 20 20 15 10 5 2.5 1.25`: the base, then one number per pair. */
internal fun LadderSpec.Plates.platesLine(): String =
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
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun AddRow(label: String, symbol: Symbol = Symbol.Add, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, color = MaterialTheme.colorScheme.primary) },
        leadingContent = { SymbolIcon(symbol, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun SectionLabel(text: String) {
    // A group's head over its card, at the 24 dp edge, in primary.
    GroupHead(text)
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
