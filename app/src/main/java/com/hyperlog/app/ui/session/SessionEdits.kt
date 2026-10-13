package com.hyperlog.app.ui.session

import androidx.compose.foundation.lazy.itemsIndexed
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.cardBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.search
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.RepRange
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.ui.library.SearchField
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

// Editing the session on the fly (SPEC, Today → Edit on the fly; docs/design/logging-screen.md,
// Edit on the fly). Every edit is for this session only.

/** The last line of a program exercise's table: one more set like its last. */
internal fun LazyListScope.addSetRow(onAdd: () -> Unit) {
    item(key = "add-set") {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            Modifier.fillMaxWidth().height(SubRowHeight).clickable(onClick = onAdd).padding(horizontal = RowPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(ColSet), contentAlignment = Alignment.CenterStart) { SymbolIcon(Symbol.Add, null, Modifier.size(20.dp), tint = muted) }
            Text(stringResource(R.string.edit_add_set), style = MaterialTheme.typography.labelLarge, color = muted)
        }
    }
}

/**
 * Swapping [exercise] for another: with no search, the program's choices for the slot (its
 * planned exercise and alternatives) and then the library's exercises with the same movement
 * pattern; a search covers the whole library.
 */
@Composable
internal fun SwapPicker(exercise: SessionExercise, library: List<Exercise>, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val byId = remember(library) { library.associateBy { it.id } }
    val current = exercise.exercise.id
    val slot = exercise.slot
    val program = slot?.let { listOf(it.exercise) + it.alternatives }.orEmpty().filter { it != current }.mapNotNull { byId[it] }
    val shown = program.map { it.id }.toSet() + current
    val same = library.filter { !it.hidden && it.pattern == exercise.exercise.pattern && it.id !in shown }.sortedBy { it.name }
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.edit_swap_title, exercise.exercise.name),
            Modifier.padding(start = Margin, end = Margin, top = 16.dp, bottom = 16.dp),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SearchField(
            query,
            onChange = { query = it },
            placeholder = stringResource(R.string.session_search),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
        val listState = rememberLazyListState()
        LaunchedEffect(query) { listState.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).imePadding(), state = listState) {
            if (query.isNotBlank()) {
                val found = library.filter { !it.hidden && it.id != current }.search(query)
                itemsIndexed(found, key = { _, it -> it.id }) { i, e -> Segment(i, found.size, color = Tones.sheetCard, onClick = { onPick(e.id) }) { PickRow(e, null) } }
            } else {
                if (program.isNotEmpty()) {
                    item(key = "head-alternatives") { SectionHead(stringResource(R.string.edit_swap_alternatives)) }
                    // Swapped already: the planned exercise is the way back, so it says which it is.
                    itemsIndexed(program, key = { _, it -> it.id }) { i, e -> Segment(i, program.size, color = Tones.sheetCard, onClick = { onPick(e.id) }) { PickRow(e, if (e.id == slot?.exercise) stringResource(R.string.edit_planned) else null) } }
                }
                if (same.isNotEmpty()) {
                    item(key = "head-same") { SectionHead(stringResource(R.string.edit_swap_same_movement)) }
                    itemsIndexed(same, key = { _, it -> it.id }) { i, e -> Segment(i, same.size, color = Tones.sheetCard, onClick = { onPick(e.id) }) { PickRow(e, null) } }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHead(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(start = Margin, end = Margin, top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PickRow(exercise: Exercise, tag: String?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = CardPadding, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        tag?.let {
            Spacer(Modifier.width(16.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** "Remove <name>?": the exercise leaves this session; the program keeps it. */
@Composable
internal fun RemoveExerciseDialog(name: String, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_remove_title, name)) },
        text = { Text(stringResource(R.string.edit_remove_body)) },
        confirmButton = { TextButton(onClick = { onDismiss(); onRemove() }) { Text(stringResource(R.string.edit_remove_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) } },
    )
}

/** What a session list row's ⋮ can do with its exercise; null leaves an action out. */
internal class ExerciseEdits(
    val onSwap: (() -> Unit)?,
    val onAddSet: (() -> Unit)?,
    val onMoveUp: (() -> Unit)?,
    val onMoveDown: (() -> Unit)?,
    val onRemove: (() -> Unit)?,
)

/** The ⋮ at the end of a session list row. */
@Composable
internal fun ExerciseEditMenu(name: String, edits: ExerciseEdits) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            SymbolIcon(Symbol.MoreVert, stringResource(R.string.edit_exercise_description, name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun item(text: Int, symbol: Symbol, action: (() -> Unit)?) {
                action ?: return
                DropdownMenuItem(
                    text = { Text(stringResource(text)) },
                    leadingIcon = { SymbolIcon(symbol, null) },
                    onClick = { open = false; action() },
                )
            }
            item(R.string.edit_swap, Symbol.SwapHoriz, edits.onSwap)
            item(R.string.edit_add_set, Symbol.Add, edits.onAddSet)
            item(R.string.edit_move_up, Symbol.ArrowUpward, edits.onMoveUp)
            item(R.string.edit_move_down, Symbol.ArrowDownward, edits.onMoveDown)
            item(R.string.edit_remove_exercise, Symbol.Delete, edits.onRemove)
        }
    }
}

/**
 * A planned set's target in the set sheet: reps as a range, each end stepped on its own (a
 * fixed number is a range of one), and the target RIR. Each step applies at once.
 */
@Composable
internal fun TargetEditor(planned: SetPrescription, onChange: (RepRange, Int?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Quick taps build on each other before the session catches up with the first.
    var target by remember(planned) { mutableStateOf(planned) }
    fun change(reps: RepRange, rir: Int?) {
        target = target.copy(reps = reps, rir = rir)
        onChange(reps, rir)
    }
    val reps = target.reps
    Text(
        stringResource(R.string.edit_target),
        Modifier.padding(horizontal = Margin),
        style = MaterialTheme.typography.labelLarge,
        color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    // The three rows are one card on the sheet.
    Column(Modifier.cardBehind(Tones.sheetCard)) {
    // The two ends on rows of their own: a dash between two steppers would read as another minus.
    EditRow(stringResource(R.string.edit_reps_from)) {
        Stepper(
            reps.min.toString(),
            down = stringResource(R.string.edit_min_down) to (reps.min > 1),
            up = stringResource(R.string.edit_min_up) to true,
            onDown = { change(RepRange(reps.min - 1, reps.max), target.rir) },
            // Raising the bottom past the top carries the top with it.
            onUp = { change(RepRange(reps.min + 1, maxOf(reps.max, reps.min + 1)), target.rir) },
        )
    }
    EditRow(stringResource(R.string.edit_reps_to)) {
        Stepper(
            reps.max.toString(),
            down = stringResource(R.string.edit_max_down) to (reps.max > 1),
            up = stringResource(R.string.edit_max_up) to true,
            onDown = { change(RepRange(minOf(reps.min, reps.max - 1), reps.max - 1), target.rir) },
            onUp = { change(RepRange(reps.min, reps.max + 1), target.rir) },
        )
    }
    EditRow(stringResource(R.string.edit_rir)) {
        val rir = target.rir
        Stepper(
            rir?.toString().orEmpty(),
            down = stringResource(R.string.edit_rir_down) to ((rir ?: 0) > 0),
            up = stringResource(R.string.edit_rir_up) to ((rir ?: 0) < MaxRir),
            onDown = { change(reps, (rir ?: 1) - 1) },
            onUp = { change(reps, (rir ?: -1) + 1) },
            mark = rir?.let { { RirMark(it, filled = false) } },
        )
    }
    }
}

/** The highest target RIR the sheet offers; past it a set is a warm-up. */
private const val MaxRir = 5

@Composable
private fun EditRow(label: String, content: @Composable () -> Unit) {
    // Steppers end at the margin less their touch padding, so their values line up in one column.
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = Margin, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

/** − value +, the value in the middle at tabular width; a step that can't go further is dimmed. */
@Composable
private fun Stepper(
    value: String,
    down: Pair<String, Boolean>,
    up: Pair<String, Boolean>,
    onDown: () -> Unit,
    onUp: () -> Unit,
    mark: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    IconButton(onClick = onDown, enabled = down.second) {
        SymbolIcon(Symbol.Remove, down.first, tint = if (down.second) cs.onSurfaceVariant else cs.outlineVariant)
    }
    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
        if (mark != null) mark() else Text(value, style = MaterialTheme.typography.titleLarge.merge(Tnum))
    }
    IconButton(onClick = onUp, enabled = up.second) {
        SymbolIcon(Symbol.Add, up.first, tint = if (up.second) cs.onSurfaceVariant else cs.outlineVariant)
    }
}
