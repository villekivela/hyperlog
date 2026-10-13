package com.hyperlog.app.ui.gym

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.withPlates
import com.hyperlog.app.domain.StackReading
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.domain.summary
import com.hyperlog.app.ui.session.Margin
import com.hyperlog.app.ui.session.Tnum
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.ConnectedChoice
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors

/** The ways to describe a weight ladder exactly (SPEC, Gyms and equipment). */
enum class LadderKind(val label: Int) {
    PinStack(R.string.ladder_pin_stack), Stack(R.string.ladder_stack), List(R.string.ladder_list), Plates(R.string.ladder_plates);

    companion object {
        /** What an equipment item can be: a pin stack (with add-ons) first. */
        val Items = listOf(PinStack, Stack, List, Plates)
    }
}

/**
 * Edits one weight ladder: an equipment item's (with its [name]), or a shared one (no name).
 *
 * With [readable], it opens on reading the stack from its labels: two or three weights as printed,
 * the add-ons, and the app fills in the rest (SPEC, Setting up a stack from its labels). **Edit
 * exactly** gives the start-and-step, list and plates fields of [kinds]. Either way the weights it
 * gives show live under the fields, as a one-line check that opens to the whole list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LadderEditor(
    title: String,
    name: String?,
    spec: LadderSpec?,
    kinds: List<LadderKind>,
    onSave: (name: String, spec: LadderSpec) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
    /** The kind a new ladder opens on; null for the first of [kinds]. */
    defaultKind: LadderKind? = null,
    /** The gym's plates, which a plate-loaded machine with no pairs of its own loads. */
    gymPlates: List<Double> = LadderSpec.DefaultGymPlates,
    /** The gym's plates for the pin, which a pin stack's ladder includes. */
    pinPlates: List<Double> = emptyList(),
    /** Opens on reading the stack from its labels (a stack machine, a dumbbell rack). */
    readable: Boolean = false,
    /** The reading is a machine's, with add-ons and pin plates; a rack's is just its weights. */
    machine: Boolean = name != null,
    /** A line over the fields saying why the editor opened, when it opened by itself. */
    intro: String? = null,
) {
    var itemName by rememberSaveable { mutableStateOf(name.orEmpty()) }
    // A plate-loaded machine, or a ladder of a kind that isn't a stack, opens on its exact fields.
    val startsReading = readable && defaultKind != LadderKind.Plates &&
        (spec == null || spec is LadderSpec.PinStack || (spec is LadderSpec.Explicit && !machine))
    var reading by rememberSaveable { mutableStateOf(startsReading) }
    var kind by rememberSaveable {
        mutableStateOf(
            when (spec) {
                is LadderSpec.Explicit -> LadderKind.List
                is LadderSpec.Plates -> LadderKind.Plates
                is LadderSpec.PinStack -> LadderKind.PinStack
                is LadderSpec.Steps -> LadderKind.Stack
                else -> defaultKind?.takeIf { spec == null && it in kinds } ?: kinds.first()
            }.takeIf { it in kinds } ?: kinds.first(),
        )
    }
    val pin = spec as? LadderSpec.PinStack
    // Reading from the labels.
    var labels by rememberSaveable {
        mutableStateOf(
            when (spec) {
                is LadderSpec.PinStack -> shorthand(spec, asLabels = true)
                is LadderSpec.Explicit -> if (!machine) shorthand(LadderSpec.PinStack(WeightUnit.Kg, spec.kgs.filter { it > 0 }.distinct().sorted()), asLabels = true) else ""
                else -> ""
            },
        )
    }
    // The field shows a stack's labels in kg, so it reads back the same way.
    var labelUnit by rememberSaveable { mutableStateOf(WeightUnit.Kg) }
    var addOns by rememberSaveable { mutableStateOf(pin?.builtIn?.takeIf { it.isNotEmpty() }?.let(::pairsText).orEmpty()) }
    var takesPinPlates by rememberSaveable { mutableStateOf(pin?.pinPlates ?: true) }
    // Exact fields.
    val steps = spec as? LadderSpec.Steps
    var unit by rememberSaveable { mutableStateOf(steps?.unit ?: WeightUnit.Kg) }
    var start by rememberSaveable { mutableStateOf(steps?.start?.let(::formatKg).orEmpty()) }
    var step by rememberSaveable { mutableStateOf(steps?.step?.let(::formatKg).orEmpty()) }
    var count by rememberSaveable { mutableStateOf(steps?.count?.toString() ?: "20") }
    var list by rememberSaveable { mutableStateOf((spec as? LadderSpec.Explicit)?.kgs?.joinToString(" ") { formatKg(it) }.orEmpty()) }
    val plates = spec as? LadderSpec.Plates
    // A machine starts at 0 kg (its starting weight, editable) and, with no pairs typed, loads the gym's plates.
    var base by rememberSaveable { mutableStateOf(plates?.baseKg?.let(::formatKg) ?: if (machine) "0" else "") }
    var pairs by rememberSaveable { mutableStateOf(plates?.pairs?.let(::pairsText).orEmpty()) }
    var pinUnit by rememberSaveable { mutableStateOf(pin?.unit ?: WeightUnit.Kg) }
    var pinExact by rememberSaveable { mutableStateOf((pin ?: LadderSpec.DefaultPinStack).let { shorthand(it, asLabels = false) }) }

    val builtIn = if (machine) addOns.toPairs()?.filter { it > 0 } else emptyList()
    val read = if (reading) StackReading.read(labels, labelUnit, builtIn.orEmpty(), pinPlates = machine && takesPinPlates) else null
    val draft: LadderSpec? = if (reading) {
        val stack = (read as? StackReading.Result.Read)?.spec?.takeIf { builtIn != null }
        // A rack is its weights; a machine keeps how its stack was read.
        if (machine) stack else stack?.let { LadderSpec.Explicit(it.pins) }
    } else when (kind) {
        LadderKind.Stack -> {
            val s = start.toNumber(); val d = step.toNumber(); val n = count.toIntOrNull()
            if (s != null && s >= 0 && d != null && d > 0 && n != null && n in 1..200) LadderSpec.Steps(s, d, unit, n) else null
        }
        LadderKind.PinStack -> {
            val p = StackReading.exact(pinExact)
            if (p != null && builtIn != null) {
                // Labels stay as they were read while the unit does; a kg stack's are its weights.
                val keep = pin?.takeIf { it.unit == pinUnit }
                LadderSpec.PinStack(
                    pinUnit, p, labelKg = keep?.labelKg ?: if (pinUnit == WeightUnit.Lb) 0.1 else 0.01, labelsCut = keep?.labelsCut ?: false,
                    builtIn = builtIn, pinPlates = machine && takesPinPlates,
                )
            } else null
        }
        LadderKind.List -> list.toNumbers()?.filter { it > 0 }?.takeIf { it.isNotEmpty() }?.let { LadderSpec.Explicit(it) }
        LadderKind.Plates -> {
            val b = base.toNumber(); val p = pairs.toPairs()
            if (b != null && b >= 0 && p != null) LadderSpec.Plates(b, p.filter { it > 0 }) else null
        }
    }
    val canSave = draft != null && (name == null || itemName.isNotBlank())
    var deleting by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    if (deleting && onDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.ladder_delete_title, name.orEmpty())) },
            confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.gym_delete_confirm)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.gym_keep)) } },
        )
    }

    Scaffold(
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onClose) { SymbolIcon(Symbol.Close, stringResource(R.string.editor_close)) } },
                actions = {
                    if (onDelete != null) IconButton(onClick = { deleting = true }) { SymbolIcon(Symbol.Delete, stringResource(R.string.gym_delete_item)) }
                    TextButton(onClick = { draft?.let { onSave(itemName.trim(), it) } }, enabled = canSave) { Text(stringResource(R.string.editor_save)) }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = Margin),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            intro?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (name != null) {
                OutlinedTextField(
                    itemName,
                    onValueChange = { itemName = it },
                    label = { Text(stringResource(R.string.ladder_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (reading) {
                OutlinedTextField(
                    labels,
                    onValueChange = { labels = it },
                    label = { Text(stringResource(if (machine) R.string.reading_field else R.string.reading_field_rack)) },
                    placeholder = { Text(stringResource(if (machine) R.string.reading_placeholder else R.string.reading_placeholder_rack)) },
                    supportingText = { Text(stringResource(R.string.reading_help)) },
                    textStyle = MaterialTheme.typography.bodyLarge.merge(Tnum),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().testTag(ReadingFieldTag),
                )
                ConnectedChoice(
                    listOf(stringResource(R.string.reading_in_kg), stringResource(R.string.reading_in_lb)),
                    if (labelUnit == WeightUnit.Kg) 0 else 1,
                    onSelect = { labelUnit = if (it == 0) WeightUnit.Kg else WeightUnit.Lb },
                )
                if (machine) AddOnFields(addOns, { addOns = it }, takesPinPlates, { takesPinPlates = it }, pinPlates)
                LadderCheck(draft, pinPlates, status = if (labels.isBlank()) stringResource(R.string.reading_empty) else readingStatus(read, builtIn == null))
                TextButton(onClick = { reading = false }, Modifier.padding(start = 0.dp)) { Text(stringResource(R.string.reading_exactly)) }
            } else {
                if (kinds.size > 1) {
                    ConnectedChoice(kinds.map { stringResource(it.label) }, kinds.indexOf(kind), onSelect = { kind = kinds[it] })
                }
                when (kind) {
                    LadderKind.PinStack -> {
                        ConnectedChoice(WeightUnit.entries.map { it.name.lowercase() }, WeightUnit.entries.indexOf(pinUnit), onSelect = { pinUnit = WeightUnit.entries[it] })
                        NumberField(pinExact, { pinExact = it }, stringResource(R.string.ladder_pin_plates_exact), Modifier.fillMaxWidth(), many = true)
                        if (machine) AddOnFields(addOns, { addOns = it }, takesPinPlates, { takesPinPlates = it }, pinPlates)
                    }
                    LadderKind.Stack -> {
                        AssistChip(
                            onClick = { unit = WeightUnit.Lb; start = "2.5"; step = "5"; count = "40" },
                            label = { Text(stringResource(R.string.ladder_lb_preset)) },
                        )
                        ConnectedChoice(WeightUnit.entries.map { it.name.lowercase() }, WeightUnit.entries.indexOf(unit), onSelect = { unit = WeightUnit.entries[it] })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(start, { start = it }, stringResource(R.string.ladder_start), Modifier.weight(1f))
                            NumberField(step, { step = it }, stringResource(R.string.ladder_step), Modifier.weight(1f))
                            NumberField(count, { count = it }, stringResource(R.string.ladder_count), Modifier.weight(1f), whole = true)
                        }
                    }
                    LadderKind.List -> NumberField(list, { list = it }, stringResource(R.string.ladder_list_field), Modifier.fillMaxWidth(), many = true)
                    LadderKind.Plates -> {
                        NumberField(base, { base = it }, stringResource(if (name != null) R.string.ladder_base_machine else R.string.ladder_base), Modifier.fillMaxWidth())
                        NumberField(
                            pairs, { pairs = it }, stringResource(R.string.ladder_pairs), Modifier.fillMaxWidth(), many = true,
                            supporting = if (machine) stringResource(R.string.ladder_pairs_gym) else null,
                        )
                    }
                }
                LadderCheck(draft?.withPlates(gymPlates), pinPlates, status = null)
                if (readable) TextButton(onClick = { reading = true }) { Text(stringResource(R.string.reading_from_labels)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

internal const val LadderPreviewTag = "ladder-preview"
internal const val ReadingFieldTag = "reading-field"

/** What the reading needs, when it has no stack yet: one more weight, plates next to each other, or no stack at all. */
@Composable
private fun readingStatus(read: StackReading.Result?, badAddOns: Boolean): String? = when {
    badAddOns -> stringResource(R.string.reading_bad_add_ons)
    read is StackReading.Result.Read -> stringResource(R.string.reading_reads_as, StackReading.steps(read.spec))
    read is StackReading.Result.OneMore && read.need == StackReading.Need.NextToEachOther -> stringResource(R.string.reading_next_to_each_other)
    read is StackReading.Result.OneMore -> stringResource(R.string.reading_one_more)
    read is StackReading.Result.NoStack -> stringResource(R.string.reading_no_stack)
    else -> null
}

/** A machine's own add-ons and whether the gym's plates go on its pin. */
@Composable
private fun AddOnFields(addOns: String, onAddOns: (String) -> Unit, takes: Boolean, onTakes: (Boolean) -> Unit, pinPlates: List<Double>) {
    NumberField(addOns, onAddOns, stringResource(R.string.ladder_add_ons), Modifier.fillMaxWidth(), many = true)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ladder_takes_pin_plates), style = MaterialTheme.typography.bodyLarge)
            Text(
                pinPlates.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.ladder_pin_plates_list, it.sortedDescending().joinToString(" + ") { kg -> formatKg(kg) }) }
                    ?: stringResource(R.string.ladder_pin_plates_gym),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(takes, onCheckedChange = onTakes)
    }
}

/**
 * The result of the fields above, as a check: `You can pick these weights`, the ladder in one line
 * with its count, then `Check: 36 · 43 · 49 …, looks right?`, which opens to every weight. [status]
 * says how the labels were read, or what they still need.
 */
@Composable
internal fun LadderCheck(draft: LadderSpec?, pinPlates: List<Double>, status: String?) {
    val cs = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val rungs = draft?.ladder(pinPlates)?.rungs
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Tones.card)
            .then(if (rungs != null) Modifier.clickable(onClickLabel = stringResource(if (open) R.string.ladder_check_hide else R.string.ladder_check_show)) { open = !open } else Modifier)
            .padding(16.dp).animateContentSize().testTag(LadderPreviewTag),
    ) {
        Text(stringResource(R.string.ladder_preview), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (draft == null || rungs == null) {
            Text(status ?: stringResource(R.string.ladder_incomplete), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            return@Column
        }
        Text(
            stringResource(R.string.ladder_summary_count, draft.summary(pinPlates), pluralStringResource(R.plurals.ladder_rung_count, rungs.size, rungs.size)),
            style = MaterialTheme.typography.titleMedium.merge(Tnum),
        )
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.primary) }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A stack is checked by its pin weights, as printed; the add-ons fill in between them.
            val checked = (draft as? LadderSpec.PinStack)?.pins ?: rungs
            val firstFew = checked.take(3).joinToString(" · ") { formatKg(it) } + if (rungs.size > 3) " …" else " kg"
            Text(
                if (open) rungs.joinToString(" · ") { formatKg(it) } + " kg" else stringResource(R.string.ladder_check, firstFew),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                color = cs.onSurfaceVariant,
            )
            SymbolIcon(if (open) Symbol.ExpandLess else Symbol.ExpandMore, null, Modifier.size(24.dp), tint = cs.onSurfaceVariant)
        }
    }
}

/**
 * [spec] as the reading field takes it, `5, 10, 15 … 100` or `1, 2, 3 … 10, 12.5, 15, 17.5 … 50`:
 * as its labels read in kg ([asLabels]), or as its plates in its own unit.
 */
internal fun shorthand(spec: LadderSpec.PinStack, asLabels: Boolean): String {
    val label = { w: Double -> if (asLabels) LadderSpec.PinStack(spec.unit, listOf(w), spec.labelKg, spec.labelsCut).pins.single() else w }
    return spec.segments.joinToString(", ") { seg ->
        when {
            seg.single -> formatKg(label(seg.from))
            seg.count <= 3 -> List(seg.count) { formatKg(label(seg.from + it * seg.step)) }.joinToString(", ")
            else -> List(3) { formatKg(label(seg.from + it * seg.step)) }.joinToString(", ") + " … " + formatKg(label(seg.to))
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, whole: Boolean = false, many: Boolean = false, supporting: String? = null) {
    OutlinedTextField(
        value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = !many,
        textStyle = MaterialTheme.typography.bodyLarge.merge(Tnum),
        keyboardOptions = KeyboardOptions(keyboardType = if (whole) KeyboardType.Number else if (many) KeyboardType.Text else KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** A decimal comma works too: phones in many European locales type one. */
private fun String.toNumber() = trim().replace(',', '.').toDoubleOrNull()

/** Pairs as typed: one number a pair, or a count of the same plate, `3×20 15 10 2,5`. */
internal fun String.toPairs(): List<Double>? =
    trim().replace(Regex("\\s*[×xX*]\\s*"), "×").split(Regex("[\\s;]+")).filter { it.isNotEmpty() }.flatMap { token ->
        val parts = token.split(Regex("[×xX*]"))
        when (parts.size) {
            1 -> listOf(parts[0].toNumber() ?: return null)
            2 -> {
                val n = parts[0].toIntOrNull()?.takeIf { it in 1..20 } ?: return null
                val kg = parts[1].toNumber() ?: return null
                List(n) { kg }
            }
            else -> return null
        }
    }

/** Pairs written back with counts, heaviest first: `3×20 15 10 5 2.5 1.25`. */
internal fun pairsText(pairs: List<Double>): String =
    pairs.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }
        .joinToString(" ") { (kg, n) -> if (n > 1) "$n×${formatKg(kg)}" else formatKg(kg) }

/** Weights separated by spaces: `20 20 15 10 2.5` (or `2,5`). */
private fun String.toNumbers(): List<Double>? =
    trim().split(Regex("[\\s;]+")).filter { it.isNotEmpty() }.map { it.toNumber() ?: return null }
