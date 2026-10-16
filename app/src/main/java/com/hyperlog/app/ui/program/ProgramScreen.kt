package com.hyperlog.app.ui.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.ProgramActions
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.ui.coach.scopeText
import com.hyperlog.app.ui.coach.shortDate
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.text.style.TextDecoration
import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Program
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.EmptyState
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.SectionCard
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape

private val Margin = 24.dp

/**
 * The program: its blocks and weeks, with where you are. The program runs as a sequence, so
 * every day before the next one is done. Importing a file goes through the validator; a rejected
 * file's problems replace the screen until closed (docs/design/program-and-today.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramScreen(
    active: ActiveProgram?,
    errors: List<String>?,
    onImport: () -> Unit,
    onCloseErrors: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** The starter program, offered with no program and kept in the menu; null if unreadable. */
    starter: Program? = null,
    onRunStarter: () -> Unit = {},
    /** Shows a Program action's Change for review; it applies only once accepted. */
    onPropose: (Change) -> Unit = {},
    /** Reverts the accepted Change with this id. */
    onRevert: (Long) -> Unit = {},
    onSettings: () -> Unit = {},
) {
    /** The replacement waiting for "Replace <title>?": a file, or the starter program. */
    var confirmReplace by rememberSaveable { mutableStateOf<Replacement?>(null) }
    var menu by rememberSaveable { mutableStateOf(false) }
    val import = { if (active != null) confirmReplace = Replacement.File else onImport() }
    BackHandler { if (errors != null) onCloseErrors() else onBack() }
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                // The bar sets titles 16 dp in; the screen's text edge is 24 dp.
                title = { Text(stringResource(R.string.program_title), Modifier.padding(start = 8.dp)) },
                // A destination of the navigation bar: no back arrow (back goes to Today); a rejected
                // file's problems close with their own button.
                actions = {
                    IconButton(onClick = onSettings) { SymbolIcon(Symbol.Settings, stringResource(R.string.settings)) }
                    if (active != null && errors == null) {
                        Box {
                            IconButton(onClick = { menu = true }) { SymbolIcon(Symbol.MoreVert, stringResource(R.string.session_menu)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.program_import)) },
                                    leadingIcon = { SymbolIcon(Symbol.FileOpen, null) },
                                    onClick = { menu = false; import() },
                                )
                                if (starter != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.starter_run_named, starter.title)) },
                                        leadingIcon = { SymbolIcon(Symbol.ViewTimeline, null) },
                                        onClick = { menu = false; confirmReplace = Replacement.Starter },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                errors != null -> ImportErrors(errors, onClose = onCloseErrors, onRetry = onImport)
                active == null && starter != null -> StarterOffer(starter, onRunStarter) {
                    TextButton(onClick = onImport) { Text(stringResource(R.string.program_import)) }
                }
                active == null -> NoProgram(onImport)
                else -> ProgramWeeks(active, onPropose, onRevert)
            }
        }
    }
    val replacing = confirmReplace
    if (replacing != null && active != null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = null },
            title = { Text(stringResource(R.string.program_replace_title, active.program.title)) },
            text = { Text(stringResource(R.string.program_replace_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = null
                    if (replacing == Replacement.Starter) onRunStarter() else onImport()
                }) { Text(stringResource(R.string.program_replace)) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = null }) { Text(stringResource(R.string.program_cancel)) } },
        )
    }
}

private enum class Replacement { File, Starter }

@Composable
private fun NoProgram(onImport: () -> Unit) {
    EmptyState(Symbol.ViewTimeline, stringResource(R.string.today_empty_title), stringResource(R.string.program_none_body)) {
        Button(onClick = onImport) { Text(stringResource(R.string.program_import)) }
    }
}

@Composable
private fun ProgramWeeks(active: ActiveProgram, onPropose: (Change) -> Unit, onRevert: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val program = active.program
    val next = active.next
    var shifting by rememberSaveable { mutableStateOf(false) }
    var reverting by rememberSaveable { mutableStateOf<Long?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            // Where you are, and what can be done about it, in one card at the top.
            SectionCard(Modifier.padding(top = 8.dp)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(program.title, style = MaterialTheme.typography.headlineSmall)
                program.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.heightIn(min = 32.dp), verticalAlignment = if (next != null) Alignment.Top else Alignment.CenterVertically) {
                    if (next != null) {
                        Text(
                            stringResource(R.string.program_next),
                            Modifier.width(56.dp).alignByBaseline(),
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.program_week_day, next.position.week, next.day.name),
                            Modifier.alignByBaseline(),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        SymbolIcon(Symbol.Flag, null, Modifier.size(20.dp), tint = cs.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.program_finished), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (next != null) {
                    Spacer(Modifier.height(16.dp))
                    ProgramActionRow(active, onPropose, onShift = { shifting = true })
                }
            }
            }
        }
        val history = active.history.asReversed()
        if (history.isNotEmpty()) {
            item(key = "changes") { SectionHead(stringResource(R.string.program_changes)) }
            itemsIndexed(history, key = { _, it -> "change-${it.id}" }) { i, record ->
                Segment(i, history.size) { ChangeRow(record, program, replaced = record.id in active.replaced, onRevert = { reverting = record.id }) }
            }
        }
        program.blocks.forEach { block ->
            val current = active.position.week in block.weekStart..block.weekEnd
            item(key = "block-${block.id}") { BlockHead(block, current) }
            val weeks = (block.weekStart..block.weekEnd).toList()
            itemsIndexed(weeks, key = { _, it -> "week-$it" }) { i, week ->
                Segment(i, weeks.size) { WeekRow(week, block, active) }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
    if (shifting) {
        ShiftDialog(
            onReview = { weeks, summary -> shifting = false; onPropose(ProgramActions.shift(summary, weeks)) },
            onDismiss = { shifting = false },
        )
    }
    active.history.firstOrNull { it.id == reverting }?.let { record ->
        AlertDialog(
            onDismissRequest = { reverting = null },
            title = { Text(stringResource(R.string.program_revert_title, record.change.summary)) },
            text = { Text(stringResource(R.string.program_revert_body)) },
            confirmButton = {
                TextButton(onClick = { reverting = null; onRevert(record.id) }) { Text(stringResource(R.string.program_change_revert)) }
            },
            dismissButton = { TextButton(onClick = { reverting = null }) { Text(stringResource(R.string.program_cancel)) } },
        )
    }
}

/**
 * Skip day, repeat week, deload week and shift plan: each makes a Change and opens its review,
 * so nothing moves until it is accepted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramActionRow(active: ActiveProgram, onPropose: (Change) -> Unit, onShift: () -> Unit) {
    val position = active.position
    val next = active.next ?: return
    val skip = stringResource(R.string.program_skip_summary, position.week, next.day.name)
    val repeat = stringResource(R.string.program_repeat_summary, ProgramActions.repeatedWeek(position))
    val deload = stringResource(R.string.program_deload_summary, position.week)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionChip(stringResource(R.string.program_action_skip), Symbol.SkipNext) { onPropose(ProgramActions.skipDay(skip)) }
        ActionChip(stringResource(R.string.program_action_repeat), Symbol.Replay) { onPropose(ProgramActions.repeatWeek(repeat)) }
        ActionChip(stringResource(R.string.program_action_deload), Symbol.Spa) { onPropose(ProgramActions.deloadWeek(deload, position.week)) }
        ActionChip(stringResource(R.string.program_action_shift), Symbol.MoreTime, onShift)
    }
}

@Composable
private fun ActionChip(label: String, symbol: Symbol, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { SymbolIcon(symbol, null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

@Composable
private fun SectionHead(text: String) {
    GroupHead(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** An accepted Change: its summary, period and day, with Revert while it is in force. */
@Composable
private fun ChangeRow(record: ChangeRecord, program: Program, replaced: Boolean, onRevert: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val reverted = record.revertedAt != null
    val block = program.blocks.firstOrNull { it.id == record.accepted.blockId }?.name
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = CardPadding, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                record.change.summary,
                style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (reverted) TextDecoration.LineThrough else null),
                color = if (reverted) cs.onSurfaceVariant else cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (reverted && replaced) stringResource(R.string.program_change_replaced, shortDate(java.time.Instant.ofEpochMilli(record.revertedAt!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate()))
                else if (reverted) stringResource(R.string.program_change_reverted)
                else stringResource(R.string.program_change_accepted, scopeText(record.change.scope, block), shortDate(record.accepted.acceptedOn)),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
        // One that only ended others has nothing of its own to revert.
        if (!reverted && record.change.operations.isNotEmpty()) {
            IconButton(onClick = onRevert) { SymbolIcon(Symbol.Undo, stringResource(R.string.program_change_revert), tint = cs.onSurfaceVariant) }
        }
    }
}

/** How many weeks later the rest of the plan starts, by steps. */
@Composable
private fun ShiftDialog(onReview: (Int, String) -> Unit, onDismiss: () -> Unit) {
    var weeks by rememberSaveable { mutableStateOf(1) }
    val summary = pluralStringResource(R.plurals.program_shift_summary, weeks, weeks)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.program_shift_title)) },
        text = {
            Column {
                Text(stringResource(R.string.program_shift_body))
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { weeks-- }, enabled = weeks > 1) { SymbolIcon(Symbol.Remove, stringResource(R.string.program_shift_fewer)) }
                    Text(
                        pluralStringResource(R.plurals.program_shift_weeks, weeks, weeks),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { weeks++ }, enabled = weeks < ProgramActions.MAX_SHIFT_WEEKS) {
                        SymbolIcon(Symbol.Add, stringResource(R.string.program_shift_more))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onReview(weeks, summary) }) { Text(stringResource(R.string.program_review)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.program_cancel)) } },
    )
}

@Composable
private fun BlockHead(block: Block, current: Boolean) {
    val text = if (block.weekStart == block.weekEnd) stringResource(R.string.program_block_week, block.name, block.weekStart)
    else stringResource(R.string.program_block_weeks, block.name, block.weekStart, block.weekEnd)
    // The block you're in is headed in primary; the others step back.
    GroupHead(text, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A week: its number and a token per day, done, next or to come. The Next line names the next day. */
@Composable
private fun WeekRow(week: Int, block: Block, active: ActiveProgram) {
    val cs = MaterialTheme.colorScheme
    val position = active.position
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$week",
            Modifier.width(40.dp),
            style = MaterialTheme.typography.titleMedium,
            color = if (week == position.week) cs.onSurface else cs.onSurfaceVariant,
            fontWeight = if (week == position.week) FontWeight.Bold else FontWeight.Normal,
        )
        block.days.forEachIndexed { i, day ->
            val letter = ('A' + i).toString()
            val state = when {
                week < position.week || (week == position.week && i < position.day) -> DayState.Done
                week == position.week && i == position.day -> DayState.Next
                else -> DayState.Upcoming
            }
            val description = when (state) {
                DayState.Done -> stringResource(R.string.program_day_done, "$week$letter")
                DayState.Next -> stringResource(R.string.program_day_next, "$week$letter")
                DayState.Upcoming -> stringResource(R.string.program_day_upcoming, "$week$letter")
            }
            DayToken(letter, state, Modifier.semantics { contentDescription = description })
            Spacer(Modifier.width(8.dp))
        }
    }
}

private enum class DayState { Done, Next, Upcoming }

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayToken(letter: String, state: DayState, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = Modifier.size(32.dp).then(
        when (state) {
            // Done days are a tint of primary, so they read on a card in any palette, grey included.
            DayState.Done -> Modifier.background(cs.primary.copy(alpha = 0.2f), CircleShape)
            // The one filled shape: the expressive cookie in primary.
            DayState.Next -> Modifier.size(36.dp).background(cs.primary, MaterialShapes.Cookie9Sided.toShape())
            DayState.Upcoming -> Modifier.border(1.dp, cs.outlineVariant, CircleShape)
        },
    )
    Box(modifier.then(shape), contentAlignment = Alignment.Center) {
        Text(
            letter,
            style = MaterialTheme.typography.labelLarge,
            color = when (state) {
                DayState.Next -> cs.onPrimary
                DayState.Done -> cs.onSurface
                DayState.Upcoming -> cs.onSurfaceVariant
            },
            fontWeight = if (state == DayState.Next) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * One problem of a rejected program file or pasted Change: the path in small monospace over the
 * message (`operations[0].slot` / `unknown slot "rows"`), or the message alone when it has no path.
 */
@Composable
fun Problem(error: String, modifier: Modifier = Modifier) {
    val split = error.indexOf(": ")
    val path = error.substring(0, split.coerceAtLeast(0))
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (split > 0 && !path.contains(' ')) {
            Text(
                // Zero-width spaces let the path break after its dots; it reads as written.
                path.replace(".", ".​"),
                Modifier.clearAndSetSemantics { text = AnnotatedString(path) },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(error.substring(split + 2), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A rejected file's problems, path then message, so it can be fixed in one pass. */
@Composable
internal fun ImportErrors(errors: List<String>, onClose: () -> Unit, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Column(Modifier.padding(horizontal = Margin).padding(top = 8.dp, bottom = 16.dp)) {
                    Text(stringResource(R.string.program_not_imported), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralStringResource(R.plurals.program_problems, errors.size, errors.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            items(errors) { error -> Problem(error, Modifier.padding(horizontal = Margin)) }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.program_close)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.program_choose_file)) }
        }
    }
}
