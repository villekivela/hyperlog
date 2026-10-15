package com.hyperlog.app.ui.session

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperlog.app.R
import com.hyperlog.app.domain.Extension
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.WarmupSet
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.program.backoffWeight
import com.hyperlog.app.program.plannedWeight
import com.hyperlog.app.session.Correction
import com.hyperlog.app.session.DEFAULT_DROP_REPS
import com.hyperlog.app.session.FollowUp
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.session.WarmupEdit
import com.hyperlog.app.ui.program.symbol
import com.hyperlog.app.ui.program.techniqueName
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

// The set table (docs/design/logging-screen.md): flat rows on the screen's surface, columns
// index · Last · kg · Reps · RIR · ✓. Warm-ups, sets and their drops or mini-sets are one list
// that scrolls as one, fading at an edge that has more, with the active row kept in view.

/** What the set table needs besides the exercise: the pad's entry and what it is for. */
internal class TableState(
    val current: SessionExercise,
    val entry: SetEntry?,
    val followUp: FollowUp?,
    val weightFlagged: Boolean,
    /** Seconds on the running hold timer; null with no hold running. */
    val holdSeconds: Int?,
    val actions: PadActions,
    /** Opens set [index]'s sheet (its technique, target, left and right). */
    val onSetOptions: (Int) -> Unit,
    /** The warm-ups before the first work set; empty once it is logged. */
    val warmups: List<WarmupSet> = emptyList(),
    val onTickWarmup: (Int) -> Unit = {},
    /** The warm-up on the pad; null when the pad is on the set to do. */
    val warmupEdit: WarmupEdit? = null,
    /** Puts a warm-up on the pad; null where warm-ups can't be typed. */
    val onEditWarmup: ((Int) -> Unit)? = null,
    /** Adds a set to a program exercise; null where sets can't be added (a past session). */
    val onAddSet: (() -> Unit)? = null,
    /** The logged row loaded into the pad, of any exercise; null when none. */
    val correction: Correction? = null,
    /** Loads logged set [set], or its drop, mini-set or hold [extension], into the pad; null while rows can't be corrected. */
    val onCorrect: ((set: Int, extension: Int?) -> Unit)? = null,
    /** The check on the row being done: logs it as shown. Null where nothing is logged from the table. */
    val onLogShown: (() -> Unit)? = null,
    /** Set [index]'s check takes it back. */
    val undoable: (Int) -> Boolean = { false },
    val onUndo: () -> Unit = {},
    /** `+` at the foot of a drop set, before or during it: one more drop. Null where sets can't change. */
    val onAddDrop: ((Int) -> Unit)? = null,
) {
    /** The weight being typed for the next set; a drop's or mini-set's entry isn't one. */
    val activeKg: Double? get() = if (followUp == null) entry?.weightKg else null

    /** While a row is corrected or a warm-up typed, the set to do steps back to its upcoming look. */
    val steppedBack: Boolean get() = correction != null || warmupEdit != null

    fun corrects(set: Int, extension: Int?): Boolean =
        correction?.let { it.sessionExerciseId == current.id && it.set == set && it.extension == extension } == true

    fun correctSet(set: Int): (() -> Unit)? = onCorrect?.let { { it(set, null) } }

    /** Set [index] is logged and open for its drops, mini-sets or hold. */
    fun isOpen(index: Int): Boolean = followUp != null && index == current.sets.size - 1
}

/** One row of the list, with its key. */
private class TableItem(val key: String, val content: @Composable () -> Unit)

/** How a row's index circle joins its set's drops or mini-sets in one tall pill. */
internal enum class Join { None, Top, Middle, Foot }

/** The check column: still to do, ready to log with a tap, or done. */
internal enum class Check { Todo, Ready, Done }

/** The index circle's tone. */
private enum class Tone { Plain, Active, Technique }

@Composable
internal fun SetTable(table: TableState, modifier: Modifier) {
    val current = table.current
    val items = mutableListOf<TableItem>()
    var active: String? = null
    fun add(key: String, isActive: Boolean = false, content: @Composable () -> Unit) {
        items += TableItem(key, content)
        if (isActive) active = key
    }
    buildWarmups(table, ::add)
    val plan = current.prescription
    if (plan != null) buildPlanned(table, plan, ::add) else buildAdHoc(table, ::add)

    // A new list per exercise, so it opens at the top with its warm-ups.
    val listState = remember(current.id) { LazyListState() }
    val activeIndex = items.indexOfFirst { it.key == active }
    // Warm-ups appearing above the sets would leave the list anchored below them: show them from the top.
    val warmups = table.warmups.size
    LaunchedEffect(current.id, warmups > 0) {
        if (warmups > 0) listState.scrollToItem(0)
    }
    LaunchedEffect(current.id, active, items.size) {
        if (activeIndex < 0) return@LaunchedEffect
        withFrameNanos { }
        listState.keepInView(activeIndex)
    }
    Column(modifier.fillMaxWidth()) {
        // The heads sit above the rows, so they stay while the rows scroll.
        Box(Modifier.padding(horizontal = TapInset)) {
            ColumnHeads(
                showLast = current.lastTime.isNotEmpty(),
                split = table.entry?.right != null && table.followUp == null && table.warmupEdit == null,
            )
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).fadingEdges(listState),
            state = listState,
            contentPadding = PaddingValues(start = TapInset, end = TapInset, bottom = 8.dp),
        ) {
            items(items, key = { it.key }) { it.content() }
        }
    }
}

/**
 * Scrolls as little as it takes to show the item at [index] whole, so the warm-ups above it stay
 * in view as long as they can; one out of view comes to the top.
 */
private suspend fun LazyListState.keepInView(index: Int) {
    val info = layoutInfo
    val start = info.viewportStartOffset
    val end = info.viewportEndOffset - info.afterContentPadding
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    when {
        item == null -> animateScrollToItem(index)
        item.offset < start -> animateScrollBy((item.offset - start).toFloat())
        item.offset + item.size > end -> animateScrollBy(minOf(item.offset + item.size - end, item.offset - start).toFloat())
    }
}

/** Fades the list out at an edge that has more to scroll to, so it reads as scrollable. */
private fun Modifier.fadingEdges(state: LazyListState): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 32.dp.toPx()
        if (state.canScrollBackward) {
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = fade), size = size.copy(height = fade), blendMode = BlendMode.DstIn)
        }
        if (state.canScrollForward) {
            drawRect(
                Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
                topLeft = Offset(0f, size.height - fade), size = size.copy(height = fade), blendMode = BlendMode.DstIn,
            )
        }
    }

// ---------------------------------------------------------------- building the rows

private typealias Add = (key: String, isActive: Boolean, content: @Composable () -> Unit) -> Unit

private fun buildWarmups(table: TableState, add: Add) {
    val done = table.current.warmupsDone
    table.warmups.forEachIndexed { i, w ->
        val editing = table.warmupEdit?.takeIf { it.index == i }
        add("warmup-$i", editing != null) {
            WarmupRow(i, w, done = i < done, editing = editing?.entry, table = table)
        }
    }
}

/**
 * A program exercise: a row for every planned set from the start, done, active or to come, each
 * followed by its drops, mini-sets or hold.
 */
private fun buildPlanned(table: TableState, plan: List<SetPrescription>, add: Add) {
    val current = table.current
    val entry = table.entry
    val logged = current.sets
    for (i in 0 until maxOf(plan.size, logged.size)) {
        val target = plan.getOrNull(i)
        val last = current.lastTime.getOrNull(i)
        val kg = plannedWeight(i, plan, logged, current.lastTime, current.ladder, table.activeKg)
        val technique = current.techniqueAt(i)
        val subs = subRows(table, i, technique, plannedKg = kg)
        val join = if (subs.isNotEmpty()) Join.Top else Join.None
        val isActive = i == logged.size && entry != null && table.followUp == null && !table.steppedBack
        add("set-$i", isActive || table.corrects(i, null)) {
            val c = table.correction
            when {
                c != null && table.corrects(i, null) -> ActiveSetRow(i, last, c.entry, false, table, target, technique, join, editing = true)
                i < logged.size -> DoneSetRow(i, last, logged[i], table, technique, join)
                i == logged.size && entry != null && table.followUp == null && table.steppedBack ->
                    UpcomingSetRow(i, last, entry.weight, entry.repsText, target, technique, join, table, steppedBack = true)
                isActive -> ActiveSetRow(i, last, entry!!, table.weightFlagged, table, target, technique, join)
                else -> {
                    // Targets are placeholders: the planned weight, the rep range and an RIR ring.
                    UpcomingSetRow(i, last, kg?.let(::formatKg).orEmpty(), target?.reps?.toString().orEmpty(), target, technique, join, table)
                }
            }
        }
        subs.forEach { add(it.key, it.active, it.content) }
    }
    table.onAddSet?.let { onAdd -> add("add-set", false) { AddSetRow(onAdd) } }
}

/** An ad-hoc exercise: the logged sets, the active one, then last time's remaining sets as muted rows. */
private fun buildAdHoc(table: TableState, add: Add) {
    val current = table.current
    val entry = table.entry
    for (i in current.sets.indices) {
        val technique = current.sets[i].technique
        val subs = subRows(table, i, technique, plannedKg = null)
        val join = if (subs.isNotEmpty()) Join.Top else Join.None
        add("set-$i", table.corrects(i, null)) {
            val c = table.correction
            if (c != null && table.corrects(i, null)) ActiveSetRow(i, current.lastTime.getOrNull(i), c.entry, false, table, null, technique, join, editing = true)
            else DoneSetRow(i, current.lastTime.getOrNull(i), current.sets[i], table, technique, join)
        }
        subs.forEach { add(it.key, it.active, it.content) }
    }
    if (entry != null && table.followUp == null) {
        val i = current.sets.size
        val technique = current.techniqueAt(i)
        val subs = subRows(table, i, technique, plannedKg = entry.weightKg)
        val join = if (subs.isNotEmpty()) Join.Top else Join.None
        add("set-$i", !table.steppedBack) {
            if (table.steppedBack) UpcomingSetRow(i, current.lastTime.getOrNull(i), entry.weight, entry.repsText, null, technique, join, table, steppedBack = true)
            else ActiveSetRow(i, current.lastTime.getOrNull(i), entry, table.weightFlagged, table, null, technique, join)
        }
        subs.forEach { add(it.key, it.active, it.content) }
        for (k in i + 1 until current.lastTime.size) {
            val t = current.techniqueAt(k)
            val more = subRows(table, k, t, plannedKg = entry.weightKg)
            add("set-$k", false) {
                UpcomingSetRow(k, current.lastTime[k], entry.weight, entry.full, null, t, if (more.isNotEmpty()) Join.Top else Join.None, table)
            }
            more.forEach { add(it.key, it.active, it.content) }
        }
    }
}

private class SubItem(val key: String, val active: Boolean, val content: @Composable () -> Unit)

/**
 * The drops, mini-sets or hold of set [index], as rows joined to it by the index column's pill;
 * planned before the set, filled in as they're done. [plannedKg] is the set's planned or typed
 * weight, for drops and mini-sets still to come.
 */
private fun subRows(table: TableState, index: Int, technique: Technique?, plannedKg: Double?): List<SubItem> {
    technique ?: return emptyList()
    val current = table.current
    val set = current.sets.getOrNull(index)
    val open = table.isOpen(index)
    val logged = set?.extensions.orEmpty()
    val lastTime = current.lastTime.getOrNull(index)?.extensions.orEmpty()
    val out = mutableListOf<SubItem>()
    // `+` at the foot of a drop set, not started yet or under way, adds a drop.
    val plus = table.onAddDrop?.takeIf { technique is Technique.Dropset && ((set == null && current.editable(index)) || open) }?.let { { it(index) } }
    fun foot(j: Int, count: Int) = if (j == count - 1) Join.Foot else Join.Middle
    when (technique) {
        is Technique.Dropset -> {
            val lastDrops = lastTime.filter { it.kind == Extension.Kind.Drop }
            val count = if (set == null || open) maxOf(technique.drops, logged.size) else logged.size
            val weights = arrayOfNulls<Double>(count)
            var before = if (set == null) plannedKg else logged.lastOrNull()?.weightKg ?: set.weightKg
            for (j in logged.size until count) {
                val kg = if (open && j == logged.size) table.entry?.weightKg else before?.let { backoffWeight(it, technique.percent, current.ladder) }
                weights[j] = kg
                before = kg
            }
            for (j in 0 until count) {
                val last = lastDrops.getOrNull(j)?.let { "${formatKg(it.weightKg)} × ${it.reps}" }
                val join = foot(j, count)
                val footPlus = if (join == Join.Foot) plus else null
                val active = (open && j == logged.size) || table.corrects(index, j)
                out += SubItem("ext-$index-$j", active) {
                    when {
                        table.corrects(index, j) -> ActiveSubRow(last, table, weightLocked = false, join = join, toFailure = true, correcting = table.correction!!.entry, hold = false)
                        j < logged.size -> DoneSubRow(
                            last, logged[j].weightKg, "${logged[j].reps}", join,
                            onCorrect = table.onCorrect?.let { { it(index, j) } },
                            description = stringResource(R.string.correct_drop_description, j + 1, index + 1),
                        )
                        open && j == logged.size -> ActiveSubRow(last, table, weightLocked = false, join = join, toFailure = true, plus = footPlus)
                        else -> PlannedSubRow(last, weights[j], "${lastDrops.getOrNull(j)?.reps ?: DEFAULT_DROP_REPS}", join, toFailure = true, plus = footPlus)
                    }
                }
            }
        }
        is Technique.MyoReps -> {
            val rows = logged.size + if (open) 1 else 0
            logged.forEachIndexed { j, mini ->
                out += SubItem("ext-$index-$j", table.corrects(index, j)) {
                    if (table.corrects(index, j)) ActiveSubRow(null, table, weightLocked = true, join = foot(j, rows), correcting = table.correction!!.entry, hold = false)
                    else DoneSubRow(
                        null, mini.weightKg, "${mini.reps}", foot(j, rows),
                        onCorrect = table.onCorrect?.let { { it(index, j) } },
                        description = stringResource(R.string.correct_mini_set_description, j + 1, index + 1),
                    )
                }
            }
            when {
                open -> out += SubItem("ext-$index-${logged.size}", true) { ActiveSubRow(null, table, weightLocked = true, join = Join.Foot) }
                // Before the set, one planned mini-set shows the target of the first, at the set's weight.
                set == null -> out += SubItem("ext-$index-0", false) {
                    PlannedSubRow(null, plannedKg, "${technique.miniSetReps}", Join.Foot)
                }
            }
        }
        is Technique.IsoHold -> {
            val last = lastTime.firstOrNull { it.kind == Extension.Kind.Hold }?.let { seconds(it.reps) }
            when {
                logged.isNotEmpty() -> out += SubItem("ext-$index-0", table.corrects(index, 0)) {
                    if (table.corrects(index, 0)) ActiveSubRow(last, table, weightLocked = true, join = Join.Foot, correcting = table.correction!!.entry, hold = true)
                    else DoneSubRow(
                        last, logged[0].weightKg, seconds(logged[0].reps), Join.Foot,
                        onCorrect = table.onCorrect?.let { { it(index, 0) } },
                        description = stringResource(R.string.correct_hold_description, index + 1),
                    )
                }
                open -> out += SubItem("ext-$index-0", true) { ActiveSubRow(last, table, weightLocked = true, join = Join.Foot) }
                set == null -> out += SubItem("ext-$index-0", false) { PlannedSubRow(last, plannedKg, seconds(technique.seconds), Join.Foot) }
            }
        }
        else -> Unit
    }
    return out
}

private fun seconds(s: Int) = "$s s"

// ---------------------------------------------------------------- rows

/**
 * A row's frame: [height] tall, flat on the surface, the content at the 24 dp margin. No row is
 * filled: the active one is marked by its outlined cells and its primary index circle.
 */
@Composable
private fun RowFrame(
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.fillMaxWidth().height(height)
            .then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = null, onClickLabel = clickLabel, onClick = onClick) else Modifier),
    ) {
        // The ripple covers the row right of the index column, so it never clips the technique's pill.
        if (onClick != null) Box(Modifier.matchParentSize().padding(start = RowPad + ColIndex).clip(RowRipple).indication(source, ripple()))
        Row(Modifier.fillMaxSize().padding(horizontal = RowPad), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun WarmupRow(i: Int, w: WarmupSet, done: Boolean, editing: SetEntry?, table: TableState) {
    val cs = MaterialTheme.colorScheme
    val description = stringResource(R.string.warmup_description, i + 1, formatKg(w.weightKg), w.reps)
    val tick = Modifier.toggleable(value = done, role = Role.Checkbox, onValueChange = { table.onTickWarmup(i) })
        .semantics { contentDescription = description }
    if (editing != null) {
        RowFrame(RowHeight, modifier = Modifier.testTag(ActiveWarmupTag)) {
            IndexCell(stringResource(R.string.warmup_lead), Tone.Active, null, Join.None, RowHeight, bold = true)
            Spacer(Modifier.weight(1f))
            EntryCells(editing, flagged = false, table.actions)
            Spacer(Modifier.width(ColRir))
            CheckCell(if (done) Check.Done else Check.Todo, tick)
        }
        return
    }
    val edit = table.onEditWarmup?.takeIf { table.correction == null }
    RowFrame(RowHeight, onClick = edit?.let { { it(i) } }, clickLabel = stringResource(R.string.warmup_edit, i + 1)) {
        IndexCell(stringResource(R.string.warmup_lead), Tone.Plain, null, Join.None, RowHeight)
        Spacer(Modifier.weight(1f))
        // Planned looks planned, done looks done: muted like a prefill until ticked.
        val color = if (done) cs.onSurface else cs.outline
        ValueText(formatKg(w.weightKg), ColKg, color)
        ValueText("${w.reps}", ColReps, color)
        Spacer(Modifier.width(ColRir))
        CheckCell(if (done) Check.Done else Check.Todo, tick)
    }
}

/** A set still to come: the planned weight and the target as muted placeholders, an RIR ring for the target RIR. */
@Composable
private fun UpcomingSetRow(
    i: Int, last: LoggedSet?, weight: String, reps: String, target: SetPrescription?, technique: Technique?, join: Join,
    table: TableState, steppedBack: Boolean = false,
) {
    val color = MaterialTheme.colorScheme.outline
    // The set to do while a warm-up is typed: a tap brings the pad back to it.
    val back = if (steppedBack && table.warmupEdit != null) table.actions::closeWarmup else null
    RowFrame(RowHeight, onClick = back, clickLabel = stringResource(R.string.pad_close_warmup)) {
        IndexCell("${i + 1}", if (technique != null) Tone.Technique else Tone.Plain, technique, join, RowHeight,
            onClick = if (!steppedBack && table.current.editable(i) || table.current.prescription == null && !steppedBack) { { table.onSetOptions(i) } } else null,
            description = stringResource(R.string.set_options_description, i + 1))
        LastCell(last)
        ValueText(weight, ColKg, color)
        ValueText(reps, ColReps, color)
        RirCell { target?.rir?.let { RirMark(it, filled = false) } }
        CheckCell(Check.Todo, null)
    }
}

@Composable
private fun ActiveSetRow(
    i: Int, last: LoggedSet?, entry: SetEntry, flagged: Boolean, table: TableState, target: SetPrescription?,
    technique: Technique?, join: Join, editing: Boolean = false,
) {
    RowFrame(RowHeight) {
        if (editing) EditCell(stringResource(R.string.correct_set_editing, i + 1))
        else IndexCell("${i + 1}", Tone.Active, technique, join, RowHeight,
            onClick = { table.onSetOptions(i) }, description = stringResource(R.string.set_options_description, i + 1), bold = true)
        LastCell(last)
        EntryCells(entry, flagged, table.actions, repsPlaceholder = target?.reps?.toString())
        RirCell {
            when {
                entry.rir != null -> RirMark(entry.rir, filled = true)
                target?.rir != null -> RirMark(target.rir, filled = false)
            }
        }
        if (editing) CheckCell(Check.Done, null)
        else {
            val log = table.onLogShown
            val description = stringResource(R.string.check_log, i + 1)
            // Nothing logs until a weight and reps are typed: the check stays a grey ring and does nothing.
            if (entry.canLog) CheckCell(
                Check.Ready,
                if (log != null) Modifier.toggleable(false, role = Role.Checkbox, onValueChange = { log() }).semantics { contentDescription = description } else null,
            ) else CheckCell(Check.Todo, null)
        }
    }
}

@Composable
private fun DoneSetRow(i: Int, last: LoggedSet?, set: LoggedSet, table: TableState, technique: Technique?, join: Join) {
    // The set whose drops, mini-sets or hold are being done keeps its primary circle for the whole group.
    val open = table.isOpen(i)
    val color = MaterialTheme.colorScheme.onSurface
    val onClick = table.correctSet(i)
    val correct = stringResource(R.string.correct_set_description, i + 1)
    // Anything logged can be tapped and corrected.
    RowFrame(RowHeight, onClick = onClick, clickLabel = correct,
        modifier = if (onClick != null) Modifier.semantics { contentDescription = correct } else Modifier) {
        IndexCell("${i + 1}", if (open) Tone.Active else if (technique != null) Tone.Technique else Tone.Plain, technique, join, RowHeight, bold = open)
        LastCell(last)
        ValueText(formatKg(set.weightKg), ColKg, color)
        val reps = set.repsText()
        ValueText(reps, ColReps, color, small = reps.length > 6)
        RirCell { set.rir?.let { RirMark(it, filled = true) } }
        val logged = stringResource(R.string.check_logged, i + 1)
        val undo = stringResource(R.string.check_undo, i + 1)
        CheckCell(
            Check.Done,
            if (table.undoable(i)) Modifier.toggleable(true, role = Role.Checkbox, onValueChange = { table.onUndo() }).semantics { contentDescription = undo }
            else Modifier.semantics { contentDescription = logged },
        )
    }
}

@Composable
private fun AddSetRow(onAdd: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    RowFrame(SubRowHeight, onClick = onAdd) {
        Box(Modifier.width(ColIndex), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.size(IndexSize), contentAlignment = Alignment.Center) { SymbolIcon(Symbol.Add, null, Modifier.size(20.dp), tint = muted) }
        }
        Text(stringResource(R.string.edit_add_set), style = MaterialTheme.typography.labelLarge, color = muted)
    }
}

/** A drop or mini-set still to come: muted like every prefill; a drop is to failure, a 0 ring. */
@Composable
private fun PlannedSubRow(
    last: String?, kg: Double?, reps: String, join: Join, toFailure: Boolean = false, plus: (() -> Unit)? = null,
) {
    val color = MaterialTheme.colorScheme.outline
    RowFrame(SubRowHeight) {
        IndexCell(null, Tone.Technique, null, join, SubRowHeight, plus = plus)
        SubLast(last)
        ValueText(kg?.let(::formatKg).orEmpty(), ColKg, color)
        ValueText(reps, ColReps, color)
        RirCell { if (toFailure) RirMark(0, filled = false) }
        CheckCell(Check.Todo, null, small = true)
    }
}

/** A drop or mini-set done: full contrast and a filled check. A tap loads it into the pad to correct it. */
@Composable
private fun DoneSubRow(last: String?, kg: Double, reps: String, join: Join, onCorrect: (() -> Unit)?, description: String) {
    val color = MaterialTheme.colorScheme.onSurface
    RowFrame(SubRowHeight, onClick = onCorrect, clickLabel = description,
        modifier = if (onCorrect != null) Modifier.semantics { contentDescription = description } else Modifier) {
        IndexCell(null, Tone.Technique, null, join, SubRowHeight)
        SubLast(last)
        ValueText(formatKg(kg), ColKg, color)
        ValueText(reps, ColReps, color)
        Spacer(Modifier.width(ColRir))
        val logged = stringResource(R.string.sub_row_logged)
        CheckCell(Check.Done, Modifier.semantics { contentDescription = logged }, small = true)
    }
}

/**
 * The drop, mini-set or hold being done, marked like the active row by its outlined cells. A mini-set and a hold
 * are at the set's weight, so their weight is muted and can't be tapped.
 */
@Composable
private fun ActiveSubRow(
    last: String?,
    table: TableState,
    weightLocked: Boolean,
    join: Join,
    toFailure: Boolean = false,
    /** A logged drop, mini-set or hold being corrected; null for the one being done. */
    correcting: SetEntry? = null,
    hold: Boolean = table.followUp is FollowUp.Hold,
    /** `+` at the pill's foot: one more drop. */
    plus: (() -> Unit)? = null,
) {
    val entry = correcting ?: table.entry ?: return
    val actions = table.actions
    fun tap(field: Field) {
        actions.focus(field)
        actions.setPadVisible(true)
    }
    RowFrame(SubRowHeight, modifier = Modifier.testTag(ActiveSubRowTag)) {
        if (correcting != null) EditCell() else IndexCell(null, Tone.Technique, null, join, SubRowHeight, plus = plus)
        SubLast(last)
        if (weightLocked) ValueText(entry.weight, ColKg, MaterialTheme.colorScheme.outline)
        else Cell(AnnotatedString(entry.weight), focused = entry.field == Field.Weight, description = stringResource(R.string.col_kg), modifier = Modifier.width(ColKg), height = SubCellHeight) { tap(Field.Weight) }
        val reps = when {
            correcting == null && table.holdSeconds != null -> AnnotatedString(seconds(table.holdSeconds))
            hold -> AnnotatedString(seconds(entry.full.toIntOrNull() ?: 0))
            else -> repsAnnotated(entry)
        }
        Cell(reps, focused = entry.field == Field.Reps, description = stringResource(R.string.col_reps), modifier = Modifier.width(ColReps), height = SubCellHeight) { tap(Field.Reps) }
        RirCell { if (toFailure) RirMark(0, filled = false) }
        if (correcting != null) CheckCell(Check.Done, null, small = true)
        else {
            val log = table.onLogShown
            val description = stringResource(R.string.check_log_follow)
            CheckCell(Check.Ready, if (log != null) Modifier.toggleable(false, role = Role.Checkbox, onValueChange = { log() }).semantics { contentDescription = description } else null, small = true)
        }
    }
}

/** The index column of the row whose circle reads [label] (`W`, the set number). */
internal fun indexTag(label: String) = "index-$label"

internal const val ActiveSubRowTag = "active-sub-row"
internal const val ActiveWarmupTag = "active-warmup"

// ---------------------------------------------------------------- cells

/** The kg and reps cells of the row being typed: tap either to type into it. */
@Composable
private fun RowScope.EntryCells(entry: SetEntry, flagged: Boolean, actions: PadActions, repsPlaceholder: String? = null) {
    fun tap(field: Field) {
        actions.focus(field)
        actions.setPadVisible(true)
    }
    Cell(AnnotatedString(entry.weight), focused = entry.field == Field.Weight, description = stringResource(R.string.col_kg), flagged = flagged, modifier = Modifier.width(ColKg)) { tap(Field.Weight) }
    Cell(repsAnnotated(entry), focused = entry.field != Field.Weight, description = stringResource(R.string.col_reps), modifier = Modifier.width(ColReps), placeholder = repsPlaceholder) { tap(Field.Reps) }
}

/**
 * The index column: `W` or the set number on a circle. A set with a technique is tinted, with
 * the technique's symbol as a badge on its corner, and joins its drops or mini-sets in one tall
 * pill ([join]); `+` at the pill's foot adds a drop.
 */
@Composable
private fun RowScope.IndexCell(
    label: String?,
    tone: Tone,
    /** The set's technique, drawn as a badge on the circle's corner; null for none. */
    badge: Technique?,
    join: Join,
    rowHeight: Dp,
    onClick: (() -> Unit)? = null,
    description: String? = null,
    plus: (() -> Unit)? = null,
    bold: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val fill = when (tone) {
        Tone.Plain -> cs.surfaceContainerHighest
        Tone.Active -> cs.primary
        Tone.Technique -> cs.tertiaryContainer
    }
    val ink = when (tone) {
        Tone.Plain -> cs.onSurfaceVariant
        Tone.Active -> cs.onPrimary
        Tone.Technique -> cs.onTertiaryContainer
    }
    val pill = cs.tertiaryContainer
    Box(
        Modifier.width(ColIndex).fillMaxHeight().then(if (label != null) Modifier.testTag(indexTag(label)) else Modifier).drawBehind {
            val w = IndexSize.toPx()
            val r = CornerRadius(w / 2)
            val top = (size.height - w) / 2
            val shape = when (join) {
                Join.None -> null
                Join.Top -> RoundRect(0f, top, w, size.height, topLeftCornerRadius = r, topRightCornerRadius = r, bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero)
                Join.Middle -> RoundRect(0f, 0f, w, size.height, CornerRadius.Zero)
                Join.Foot -> RoundRect(0f, 0f, w, size.height - 8.dp.toPx(), topLeftCornerRadius = CornerRadius.Zero, topRightCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = r, bottomLeftCornerRadius = r)
            }
            shape?.let { drawPath(Path().apply { addRoundRect(it) }, pill) }
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (label != null) {
            Box(
                // The active set's circle is primary, even at the top of its technique's tall pill.
                Modifier.size(IndexSize).clip(CircleShape).background(if (join == Join.None || tone == Tone.Active) fill else pill)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                    .then(if (description != null && onClick != null) Modifier.semantics { contentDescription = description } else Modifier.clearAndSetSemantics { }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.merge(Tnum),
                    color = if (join == Join.None || tone == Tone.Active) ink else cs.onTertiaryContainer,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                )
            }
            // The technique's symbol on the circle's corner, so a technique reads without colour.
            badge?.let {
                Box(
                    Modifier.offset(x = IndexSize - 14.dp, y = -(IndexSize / 2) + 2.dp).size(16.dp).clip(CircleShape).background(cs.tertiary),
                    contentAlignment = Alignment.Center,
                ) { SymbolIcon(it.symbol(), techniqueName(it), Modifier.size(12.dp), tint = cs.onTertiary) }
            }
        }
        plus?.let { add ->
            val text = stringResource(R.string.add_drop)
            Box(
                Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp).size(IndexSize).clip(CircleShape)
                    .clickable(onClickLabel = text, onClick = add).semantics { contentDescription = text },
                contentAlignment = Alignment.Center,
            ) { SymbolIcon(Symbol.Add, null, Modifier.size(20.dp), tint = cs.onTertiaryContainer) }
        }
    }
}

/** The index column of a row being corrected: `edit` in primary. */
@Composable
private fun RowScope.EditCell(description: String? = null) {
    Box(
        Modifier.width(ColIndex).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.size(IndexSize), contentAlignment = Alignment.Center) {
            SymbolIcon(Symbol.Edit, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The check column: a ring still to do (primary when a tap logs it), a filled primary check once done. */
@Composable
private fun RowScope.CheckCell(state: Check, action: Modifier?, small: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val size = if (small) 24.dp else 28.dp
    Box(Modifier.width(ColCheck).fillMaxHeight().then(action ?: Modifier), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier.padding(end = (ColCheck - size) / 4).size(size).clip(CircleShape).then(
                when (state) {
                    Check.Done -> Modifier.background(cs.primary)
                    Check.Ready -> Modifier.border(2.dp, cs.primary, CircleShape)
                    Check.Todo -> Modifier.border(2.dp, cs.outlineVariant, CircleShape)
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (state == Check.Done) SymbolIcon(Symbol.Check, null, Modifier.size(size - 8.dp), tint = cs.onPrimary)
        }
    }
}

@Composable
private fun RowScope.RirCell(content: @Composable () -> Unit) {
    Box(Modifier.width(ColRir), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun RowScope.LastCell(last: LoggedSet?) = SubLast(last?.short())

@Composable
private fun RowScope.SubLast(last: String?) {
    Text(
        last.orEmpty(),
        Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium.merge(Tnum),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ValueText(text: String, width: Dp, color: Color, small: Boolean = false) {
    Text(
        text,
        Modifier.width(width),
        color = color,
        textAlign = TextAlign.Center,
        style = (if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).merge(Tnum),
        maxLines = 1,
    )
}

/** The column heads. "Last" only shows once there is a last time to compare with. */
@Composable
private fun ColumnHeads(showLast: Boolean, split: Boolean = false) {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    val st = MaterialTheme.typography.labelMedium
    Row(Modifier.fillMaxWidth().height(LabelHeight).padding(horizontal = RowPad), verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.width(ColIndex))
        Text(if (showLast) stringResource(R.string.col_last) else "", Modifier.weight(1f), style = st, color = c)
        Text(stringResource(R.string.col_kg), Modifier.width(ColKg), style = st, color = c, textAlign = TextAlign.Center)
        Text(stringResource(if (split) R.string.col_reps_split else R.string.col_reps), Modifier.width(ColReps), style = st, color = c, textAlign = TextAlign.Center)
        Text(stringResource(R.string.col_rir), Modifier.width(ColRir), style = st, color = c, textAlign = TextAlign.Center)
        Spacer(Modifier.width(ColCheck))
    }
}

/** `25 × 10`, `25 × 10 + 6`, `30 × 10/9`: last time's set, without its RIR. */
internal fun LoggedSet.short() = "${formatKg(weightKg)} × $fullReps" + (rightReps?.let { "/$it" } ?: "") + if (partialReps > 0) " + $partialReps" else ""

/** `12`, `12 + 6`, `10 / 9` for a set split into left and right. */
private fun LoggedSet.repsText() = (if (partialReps > 0) "$fullReps + $partialReps" else "$fullReps") + (rightReps?.let { " / $it" } ?: "")

/**
 * While partials are being typed, the full reps step back so the number being typed stands out;
 * in a split set the side not being typed steps back.
 */
@Composable
internal fun repsAnnotated(entry: SetEntry): AnnotatedString {
    val text = entry.repsText
    val muted = MaterialTheme.colorScheme.outline
    val slash = text.indexOf(" / ")
    if (slash >= 0 && entry.field != Field.Weight) {
        return buildAnnotatedString {
            val left = text.substring(0, slash)
            val right = text.substring(slash)
            if (entry.field == Field.Right) {
                withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append(left) }
                append(right)
            } else {
                append(left)
                withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append(right) }
            }
        }
    }
    val split = text.indexOf(" +")
    if (split < 0 || !entry.partialMode || entry.field != Field.Reps) return AnnotatedString(text)
    val end = (split + 3).coerceAtMost(text.length)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append(text.substring(0, end)) }
        append(text.substring(end))
    }
}

/**
 * A value in the active row, outlined so the row reads as active with no highlight behind it. The
 * focused one is a well outlined in primary; tap any to type into it.
 */
@Composable
internal fun Cell(
    text: AnnotatedString,
    focused: Boolean,
    description: String,
    modifier: Modifier,
    /** The weight check is on this value: it reads in the error colour, like the check's reason. */
    flagged: Boolean = false,
    height: Dp = CellHeight,
    /** Shown muted while the value is empty: the target (`6–8`). */
    placeholder: String? = null,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .padding(horizontal = CellInset)
            .height(height)
            .clip(CellShape)
            // The focused cell is a well: the lowest surface, outlined in primary. A flagged weight is outlined in error.
            .then(
                when {
                    focused -> Modifier.background(cs.surfaceContainerLowest).border(2.dp, if (flagged) cs.error else cs.primary, CellShape)
                    flagged -> Modifier.border(1.dp, cs.error, CellShape)
                    else -> Modifier.border(1.dp, cs.outline, CellShape)
                },
            )
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val empty = text.isEmpty() && placeholder != null
        // Long values (`100 + 20`) step down a size; anything still too wide (a `10–12` range) shrinks to fit inside the padding.
        val style = (if (text.length > 6) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).merge(Tnum)
        Text(
            if (empty) AnnotatedString(placeholder!!) else text,
            Modifier.padding(horizontal = CellPad),
            style = style,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize),
            color = when { empty -> cs.outline; flagged -> cs.error; else -> cs.onSurface },
            fontWeight = if ((focused || flagged) && !empty) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
