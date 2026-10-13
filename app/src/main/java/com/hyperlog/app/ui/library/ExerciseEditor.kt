package com.hyperlog.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.GripDemand
import com.hyperlog.app.library.MovementPattern
import com.hyperlog.app.library.Muscles
import com.hyperlog.app.library.TechniqueVideo
import com.hyperlog.app.ui.session.Margin
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

/**
 * Adds or edits one exercise: every field on one page. Save is enabled once the exercise has a
 * name, equipment, a movement pattern, a grip demand and a primary muscle, because the coach's
 * swaps rely on them. Hiding applies at once and can be undone here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditor(
    exercise: Exercise?,
    onSave: (Exercise) -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The drafts start from the exercise as stored, and start again if the library catches up with a
    // save after the editor opened. Hiding applies at once, so it doesn't reset them.
    val stored = exercise?.copy(hidden = false)
    var name by rememberSaveable(stored) { mutableStateOf(exercise?.name.orEmpty()) }
    var equipment by rememberSaveable(stored) { mutableStateOf(exercise?.equipment) }
    var pattern by rememberSaveable(stored) { mutableStateOf(exercise?.pattern) }
    var grip by rememberSaveable(stored) { mutableStateOf(exercise?.grip) }
    var muscles by rememberSaveable(stored) { mutableStateOf(exercise?.muscles.orEmpty()) }
    var secondary by rememberSaveable(stored) { mutableStateOf(exercise?.secondary.orEmpty()) }
    var video by rememberSaveable(stored) { mutableStateOf(exercise?.video.orEmpty()) }
    var note by rememberSaveable(stored) { mutableStateOf(exercise?.note.orEmpty()) }
    var askDiscard by remember { mutableStateOf(false) }

    val videoOk = video.isBlank() || TechniqueVideo.parse(video) != null
    val draft = if (name.isNotBlank() && equipment != null && pattern != null && grip != null && muscles.isNotEmpty() && videoOk) {
        Exercise(
            id = exercise?.id.orEmpty(), name = name.trim(), equipment = equipment!!, pattern = pattern!!, grip = grip!!,
            muscles = muscles, secondary = secondary, video = video.trim().ifEmpty { null }, note = note.trim().ifEmpty { null },
            hidden = exercise?.hidden ?: false,
        )
    } else {
        null
    }
    val dirty = name != exercise?.name.orEmpty() || equipment != exercise?.equipment || pattern != exercise?.pattern ||
        grip != exercise?.grip || muscles != exercise?.muscles.orEmpty() || secondary != exercise?.secondary.orEmpty() ||
        video != exercise?.video.orEmpty() || note != exercise?.note.orEmpty()
    val close = { if (dirty) askDiscard = true else onClose() }
    BackHandler(onBack = close)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (exercise == null) R.string.editor_new else R.string.editor_edit)) },
                navigationIcon = {
                    IconButton(onClick = close) { SymbolIcon(Symbol.Close, stringResource(R.string.editor_close)) }
                },
                actions = {
                    TextButton(onClick = { draft?.let(onSave) }, enabled = draft != null && (dirty || exercise == null)) {
                        Text(stringResource(R.string.editor_save))
                    }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = Margin),
        ) {
            OutlinedTextField(
                name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Section(stringResource(R.string.editor_equipment)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Equipment.entries.forEach { e ->
                        FilterChip(
                            selected = equipment == e,
                            onClick = { equipment = e },
                            label = { Text(e.label) },
                            leadingIcon = if (equipment == e) {
                                { SymbolIcon(Symbol.Check, null, Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            Section(stringResource(R.string.editor_pattern)) { PatternField(pattern) { pattern = it } }

            Section(stringResource(R.string.editor_grip)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    GripDemand.entries.forEachIndexed { i, g ->
                        SegmentedButton(
                            selected = grip == g,
                            onClick = { grip = g },
                            shape = SegmentedButtonDefaults.itemShape(i, GripDemand.entries.size),
                        ) { Text(g.label) }
                    }
                }
            }

            Section(stringResource(R.string.editor_muscles)) {
                MuscleChips(muscles, available = Muscles - muscles.toSet() - secondary.toSet(), onChange = { muscles = it })
            }
            Section(stringResource(R.string.editor_secondary)) {
                MuscleChips(secondary, available = Muscles - muscles.toSet() - secondary.toSet(), onChange = { secondary = it })
            }

            Spacer(Modifier.height(24.dp))
            val parsed = TechniqueVideo.parse(video)
            OutlinedTextField(
                video,
                onValueChange = { video = it },
                label = { Text(stringResource(R.string.editor_video)) },
                placeholder = { Text(stringResource(R.string.editor_video_hint)) },
                supportingText = when {
                    video.isBlank() -> null
                    parsed == null -> { { Text(stringResource(R.string.editor_video_invalid)) } }
                    else -> {
                        {
                            val at = parsed.startLabel
                            Text(if (at != null) stringResource(R.string.editor_video_opens_at, at) else stringResource(R.string.editor_video_opens))
                        }
                    }
                },
                isError = !videoOk,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.editor_note)) },
                placeholder = { Text(stringResource(R.string.editor_note_hint)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (exercise != null) {
                Spacer(Modifier.height(24.dp))
                if (exercise.hidden) {
                    Text(
                        stringResource(R.string.editor_hidden_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // The text button's own padding would push its label off the 24 dp edge.
                TextButton(onClick = { onSetHidden(!exercise.hidden) }, Modifier.offset(x = (-12).dp)) {
                    SymbolIcon(if (exercise.hidden) Symbol.Visibility else Symbol.VisibilityOff, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (exercise.hidden) R.string.editor_unhide else R.string.editor_hide))
                }
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            confirmButton = { TextButton(onClick = { askDiscard = false }) { Text(stringResource(R.string.editor_keep_editing)) } },
            dismissButton = { TextButton(onClick = { askDiscard = false; onClose() }) { Text(stringResource(R.string.editor_discard)) } },
        )
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    content()
}

/** A read-only field that opens the list of patterns. */
@Composable
private fun PatternField(pattern: MovementPattern?, onPick: (MovementPattern) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var width by remember { mutableStateOf(0) }
    val label = stringResource(R.string.editor_pattern)
    val density = LocalDensity.current
    Box(Modifier.onSizeChanged { width = it.width }) {
        OutlinedTextField(
            value = pattern?.label ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(stringResource(R.string.editor_pattern_pick)) },
            trailingIcon = { SymbolIcon(Symbol.ArrowDropDown, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // The text field takes taps itself; this layer opens the menu instead.
        Box(Modifier.matchParentSize().clickable(onClickLabel = label) { open = true }.semantics { contentDescription = label })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(with(density) { width.toDp() })) {
            MovementPattern.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label) },
                    onClick = { onPick(p); open = false },
                    trailingIcon = if (p == pattern) {
                        { SymbolIcon(Symbol.Check, null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** The chosen muscles as chips (tap to remove), then Add, which lists the muscles not chosen yet. */
@Composable
private fun MuscleChips(chosen: List<String>, available: List<String>, onChange: (List<String>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chosen.forEach { m ->
            val remove = stringResource(R.string.editor_remove_muscle, m)
            InputChip(
                selected = true,
                onClick = { onChange(chosen - m) },
                label = { Text(m) },
                trailingIcon = { SymbolIcon(Symbol.Close, remove, Modifier.size(18.dp)) },
            )
        }
        Box {
            AssistChip(
                onClick = { adding = true },
                label = { Text(stringResource(R.string.editor_add_muscle)) },
                leadingIcon = { SymbolIcon(Symbol.Add, null, Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                available.forEach { m ->
                    DropdownMenuItem(text = { Text(m) }, onClick = { onChange(chosen + m); adding = false })
                }
            }
        }
    }
}
