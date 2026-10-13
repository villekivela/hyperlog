package com.hyperlog.app.ui.gym

import com.hyperlog.app.ui.theme.ConnectedChoice
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.LadderSpec
import com.hyperlog.app.domain.WeightUnit
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.ui.session.Margin
import com.hyperlog.app.ui.session.Tnum
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

/** The three ways to describe a weight ladder (SPEC, Gyms and equipment). */
enum class LadderKind(val label: Int) { Stack(R.string.ladder_stack), List(R.string.ladder_list), Plates(R.string.ladder_plates) }

/**
 * Edits one weight ladder: an equipment item's (with its [name]), or a shared one (no name). The
 * real weights it gives show live under the fields, in kg, so an lb stack reads as it will in a
 * session. Only [kinds] are offered.
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
) {
    var itemName by rememberSaveable { mutableStateOf(name.orEmpty()) }
    var kind by rememberSaveable {
        mutableStateOf(
            when (spec) {
                is LadderSpec.Explicit -> LadderKind.List
                is LadderSpec.Plates -> LadderKind.Plates
                else -> kinds.first()
            },
        )
    }
    val steps = spec as? LadderSpec.Steps
    var unit by rememberSaveable { mutableStateOf(steps?.unit ?: WeightUnit.Kg) }
    var start by rememberSaveable { mutableStateOf(steps?.start?.let(::formatKg).orEmpty()) }
    var step by rememberSaveable { mutableStateOf(steps?.step?.let(::formatKg).orEmpty()) }
    var count by rememberSaveable { mutableStateOf(steps?.count?.toString() ?: "20") }
    var list by rememberSaveable { mutableStateOf((spec as? LadderSpec.Explicit)?.kgs?.joinToString(" ") { formatKg(it) }.orEmpty()) }
    val plates = spec as? LadderSpec.Plates
    var base by rememberSaveable { mutableStateOf(plates?.baseKg?.let(::formatKg).orEmpty()) }
    var pairs by rememberSaveable { mutableStateOf(plates?.pairs?.sortedDescending()?.joinToString(" ") { formatKg(it) }.orEmpty()) }

    val draft: LadderSpec? = when (kind) {
        LadderKind.Stack -> {
            val s = start.toNumber(); val d = step.toNumber(); val n = count.toIntOrNull()
            if (s != null && s >= 0 && d != null && d > 0 && n != null && n in 1..200) LadderSpec.Steps(s, d, unit, n) else null
        }
        LadderKind.List -> list.toNumbers()?.filter { it > 0 }?.takeIf { it.isNotEmpty() }?.let { LadderSpec.Explicit(it) }
        LadderKind.Plates -> {
            val b = base.toNumber(); val p = pairs.toNumbers()
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
            if (kinds.size > 1) {
                ConnectedChoice(kinds.map { stringResource(it.label) }, kinds.indexOf(kind), onSelect = { kind = kinds[it] })
            }
            when (kind) {
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
                    NumberField(base, { base = it }, stringResource(R.string.ladder_base), Modifier.fillMaxWidth())
                    NumberField(pairs, { pairs = it }, stringResource(R.string.ladder_pairs), Modifier.fillMaxWidth(), many = true)
                }
            }
            Column(Modifier.testTag(LadderPreviewTag)) {
                Text(
                    stringResource(R.string.ladder_preview),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    draft?.ladder()?.preview(first = 6) ?: stringResource(R.string.ladder_incomplete),
                    style = MaterialTheme.typography.titleMedium.merge(Tnum),
                    color = if (draft == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                draft?.ladder()?.rungs?.size?.let {
                    Text(
                        stringResource(R.string.ladder_rung_count, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

internal const val LadderPreviewTag = "ladder-preview"

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, whole: Boolean = false, many: Boolean = false) {
    OutlinedTextField(
        value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = !many,
        textStyle = MaterialTheme.typography.bodyLarge.merge(Tnum),
        keyboardOptions = KeyboardOptions(keyboardType = if (whole) KeyboardType.Number else if (many) KeyboardType.Text else KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** A decimal comma works too: phones in Finnish type one. */
private fun String.toNumber() = trim().replace(',', '.').toDoubleOrNull()

/** Weights separated by spaces: `20 20 15 10 2.5` (or `2,5`). */
private fun String.toNumbers(): List<Double>? =
    trim().split(Regex("[\\s;]+")).filter { it.isNotEmpty() }.map { it.toNumber() ?: return null }
