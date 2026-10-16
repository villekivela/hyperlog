package com.hyperlog.app.ui.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.SlotDiff
import com.hyperlog.app.domain.summary
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.ui.coach.shortDate
import com.hyperlog.app.ui.session.day
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.ConnectedChoice
import com.hyperlog.app.ui.theme.SectionCard
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.today.SlotRows

/**
 * The block picked (Program, docs/design/program-and-today.md): the blocks as a connected group,
 * the block's card with what changed from the block before and its weeks, then the picked week's
 * days in full: a day to come as the session will prescribe it, a done day as it was logged.
 */
internal fun LazyListScope.blockItems(
    program: Program,
    block: Block,
    active: ActiveProgram,
    from: BlockComparison?,
    week: Int,
    days: List<PlanDay>,
    names: Map<String, String>,
    onBlock: (Int) -> Unit,
    onWeek: (Int) -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    item(key = "blocks") { BlockChoice(program, program.blocks.indexOf(block), onBlock) }
    item(key = "block") { BlockCard(block, active, from, week, names, onWeek) }
    days.forEach { day ->
        val position = day.position
        item(key = "head-${position.week}-${position.day}") { DayHead(day) }
        when (day) {
            is PlanDay.Done -> item(key = "done-${position.week}-${position.day}") {
                Segment(0, 1, onClick = { onOpenSession(day.session.id) }) { DoneDay(day.session) }
            }
            is PlanDay.Ahead, is PlanDay.Skipped -> {
                val planned = if (day is PlanDay.Ahead) day.day else (day as PlanDay.Skipped).day
                val ends = (day as? PlanDay.Ahead)?.endsOn
                val groups = planned.slots.groupBy { it.group }.values.toList()
                val count = groups.size + if (ends != null) 1 else 0
                if (ends != null) {
                    item(key = "ends-${position.week}-${position.day}") { Segment(0, count) { EndsLine(ends) } }
                }
                // A skipped day's plan is there to read, not to train: it sits back from the days to come.
                val content = if (day is PlanDay.Skipped) Modifier.alpha(SkippedAlpha) else Modifier
                groups.forEachIndexed { i, group ->
                    item(key = "slots-${position.week}-${position.day}-$i") {
                        Segment(i + count - groups.size, count) { Column(content) { SlotRows(group, names) } }
                    }
                }
            }
        }
    }
}

/** The blocks, the picked one filled: a connected group while they fit, else a row of chips. */
@Composable
private fun BlockChoice(program: Program, picked: Int, onBlock: (Int) -> Unit) {
    val names = program.blocks.map { it.name }
    Column(Modifier.padding(top = 16.dp)) {
        if (names.size <= MaxConnected && names.sumOf { it.length } <= MaxConnectedChars) {
            ConnectedChoice(
                names, picked, onBlock,
                Modifier.padding(horizontal = CardMargin),
                contentPadding = PaddingValues(horizontal = 8.dp),
                fitLabels = true,
            )
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = CardMargin),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                names.forEachIndexed { i, name ->
                    FilterChip(selected = i == picked, onClick = { onBlock(i) }, label = { Text(name) }, colors = com.hyperlog.app.ui.theme.selectedChipColors())
                }
            }
        }
    }
}

private const val MaxConnected = 4

private const val SkippedAlpha = 0.6f

/** About what four buttons hold across a phone; longer names scroll as chips. */
private const val MaxConnectedChars = 40

/** A comparison this long shows its first slots and folds the rest behind "N more". */
private const val ComparedShown = 3
private const val ComparedFolded = 5

/**
 * The block: its name and weeks, what changed from the block before (each slot that differs,
 * before → after), and a chip per week, done weeks ticked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockCard(block: Block, active: ActiveProgram, from: BlockComparison?, week: Int, names: Map<String, String>, onWeek: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val name = { id: String -> names[id] ?: id }
    SectionCard(Modifier.padding(top = 16.dp)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                if (block.weekStart == block.weekEnd) stringResource(R.string.program_block_week, block.name, block.weekStart)
                else stringResource(R.string.program_block_weeks, block.name, block.weekStart, block.weekEnd),
                style = MaterialTheme.typography.titleLarge,
            )
            if (from != null) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.program_from, from.previous.name), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                // Every slot of a block that moves RIR differs, so a long comparison folds after its first few.
                val total = from.days.sumOf { it.slots.size }
                var open by rememberSaveable(block.id) { mutableStateOf(false) }
                var budget = if (open || total <= ComparedFolded) total else ComparedShown
                val several = from.days.size > 1
                from.days.forEach { day ->
                    if (budget <= 0) return@forEach
                    if (several) {
                        Text(
                            day.day.name.substringBefore(" — "),
                            Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    day.slots.take(budget).forEach { ComparedSlot(it, name) }
                    budget -= day.slots.size
                }
                if (total > ComparedFolded) {
                    val label = if (open) stringResource(R.string.program_from_fewer) else pluralStringResource(R.plurals.program_from_more, total - ComparedShown, total - ComparedShown)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp).clickable { open = !open },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = cs.primary)
                        Spacer(Modifier.width(4.dp))
                        SymbolIcon(if (open) Symbol.ExpandLess else Symbol.ExpandMore, null, Modifier.size(20.dp), tint = cs.primary)
                    }
                }
                if (from.days.isEmpty()) {
                    Text(stringResource(R.string.program_from_same, from.previous.name), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (block.weekStart..block.weekEnd).forEach { w ->
                    val done = w < active.position.week
                    val description = if (done) stringResource(R.string.program_week_done, w) else stringResource(R.string.program_week_n, w)
                    FilterChip(
                        selected = w == week,
                        onClick = { onWeek(w) },
                        label = { Text("$w") },
                        modifier = Modifier.semantics { contentDescription = description },
                        leadingIcon = if (done) ({ SymbolIcon(Symbol.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                        colors = com.hyperlog.app.ui.theme.selectedChipColors(),
                    )
                }
            }
        }
    }
}

/** One slot that changed: the exercise, then its sets before → after (or added, or gone). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComparedSlot(slot: SlotDiff, name: (String) -> String) {
    val cs = MaterialTheme.colorScheme
    val before = slot.before
    val after = slot.after
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (before != null && after != null && before.exercise != after.exercise) {
            Text(stringResource(R.string.edit_swapped_from, name(before.exercise)), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Text(
            name((after ?: before!!).exercise),
            style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (after == null) TextDecoration.LineThrough else null),
            color = if (after == null) cs.onSurfaceVariant else cs.onSurface,
        )
        when {
            after == null -> Text(stringResource(R.string.change_removed), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            before == null -> SetSummary(after.sets, color = cs.onSurface)
            // The arrow leads the new sets, so a pair too long for one line breaks before it.
            else -> FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                SetSummary(before.sets, Modifier.padding(end = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SymbolIcon(Symbol.ArrowForward, null, Modifier.padding(end = 8.dp).size(20.dp), tint = cs.primary)
                    SetSummary(after.sets, color = cs.onSurface)
                }
            }
        }
    }
}

/** A day's head: its name, with Next, Done, Skipped or Deload at the end. */
@Composable
private fun DayHead(day: PlanDay) {
    val cs = MaterialTheme.colorScheme
    val (name, state, color) = when (day) {
        is PlanDay.Ahead -> Triple(
            day.day.day.name,
            when {
                day.next -> stringResource(R.string.program_next)
                day.day.deload -> stringResource(R.string.change_deload)
                else -> null
            },
            if (day.next) cs.primary else cs.tertiary,
        )
        is PlanDay.Skipped -> Triple(day.day.day.name, stringResource(R.string.program_day_skipped), cs.onSurfaceVariant)
        is PlanDay.Done -> Triple(day.day.day.name, stringResource(R.string.program_day_done_label), cs.onSurfaceVariant)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = if (day is PlanDay.Ahead && day.next) cs.primary else cs.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (state != null) {
            Spacer(Modifier.width(16.dp))
            Text(state, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

/** A day trained: the date, then each exercise's top set, opening the session in History. */
@Composable
private fun DoneDay(session: PastSessionItem) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = CardPadding, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(day(session.startedAt), style = MaterialTheme.typography.bodyLarge)
            session.exercises.forEach { e ->
                val top = e.sets.maxWithOrNull(compareBy({ it.weightKg }, { it.fullReps })) ?: return@forEach
                Row {
                    Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(16.dp))
                    Text(top.summary(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        SymbolIcon(Symbol.ChevronRight, null, tint = cs.onSurfaceVariant)
    }
}

/** A date-ranged Change over a day to come: the day may fall after it ends. */
@Composable
private fun EndsLine(ends: java.time.LocalDate) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = CardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(Symbol.SwapHoriz, null, Modifier.size(24.dp), tint = cs.tertiary)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.program_ends, shortDate(ends)), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
    }
}
