package com.hyperlog.app.ui.program

import com.hyperlog.app.ui.theme.interFigures
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.week.WeekdayPlan
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.ProgramActions
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.program.ListedProgram
import com.hyperlog.app.program.Program
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.ui.coach.scopeText
import com.hyperlog.app.ui.coach.shortDate
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.EmptyState
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.SegmentedList
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors

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
    /** Opens the Change in force with this id: what it does from today on, as from its card on Coach. */
    onOpenChange: (Long) -> Unit = {},
    exerciseNames: Map<String, String> = emptyMap(),
    /** Finished sessions, so a done day shows what was logged. */
    sessions: List<PastSessionItem> = emptyList(),
    /** Opens a finished session in History. */
    onOpenSession: (Long) -> Unit = {},
    /**
     * What Program shows, kept by the host so it survives a session opened in History or a
     * review; null keeps it here.
     */
    view: ProgramView? = null,
    onView: (ProgramView) -> Unit = {},
    /** Your training days, shown by the program and set in their sheet (SPEC, Training days). */
    weekdayPlan: WeekdayPlan = WeekdayPlan.None,
    /** The gyms set up: with two or more, each training day can pick one. */
    gyms: List<Gym> = emptyList(),
    onWeekdayPlan: (WeekdayPlan) -> Unit = {},
    /** Every program held, for ⋮ → Programs: the one running, those saved for later, past runs. */
    listedPrograms: List<ListedProgram> = emptyList(),
    onRunAgain: (Long) -> Unit = {},
    /** Resume: a program replaced mid-way picks up where it was. */
    onResume: (Long) -> Unit = {},
    /** Opens a saved program's page, read-only with Start program. */
    onOpenSaved: (Long) -> Unit = {},
    onExportProgram: (ListedProgram) -> Unit = {},
) {
    var kept by rememberSaveable(stateSaver = ProgramView.Saver) { mutableStateOf(ProgramView()) }
    val shown = view ?: kept
    val show = { v: ProgramView -> if (view == null) kept = v else onView(v) }
    /** The replacement waiting for "Replace <title>?": a file, or the starter program. */
    var confirmReplace by rememberSaveable { mutableStateOf<Replacement?>(null) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var shifting by rememberSaveable { mutableStateOf(false) }
    var editingWeekdayPlan by rememberSaveable { mutableStateOf(false) }
    if (editingWeekdayPlan) {
        com.hyperlog.app.ui.theme.Sheet(onDismiss = { editingWeekdayPlan = false }) {
            WeekdayPlanSheetContent(weekdayPlan, gyms, onWeekdayPlan, onDone = { editingWeekdayPlan = false })
        }
    }
    if (shown.programsOpen && errors == null) {
        val close = { show(shown.copy(programsOpen = false)) }
        // Started, it is the program Program shows.
        ProgramsPage(
            listedPrograms, active,
            onRunAgain = { close(); onRunAgain(it) }, onResume = { close(); onResume(it) }, onOpenSaved = onOpenSaved,
            onExport = onExportProgram, onBack = close, modifier = modifier,
        )
        return
    }
    if (shown.changesOpen && active != null && errors == null) {
        ChangesPage(active, onRevert, onOpenChange, onBack = { show(shown.copy(changesOpen = false)) }, modifier = modifier)
        return
    }
    val import = { if (active != null) confirmReplace = Replacement.File else onImport() }
    val openPrograms = { show(shown.copy(programsOpen = true)) }
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
                    // With no program running, Import and Programs are on the screen itself.
                    if (active != null && errors == null) {
                        Box {
                            IconButton(onClick = { menu = true }) { SymbolIcon(Symbol.MoreVert, stringResource(R.string.session_menu)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                // The program actions are Changes: each opens its diff and applies only on Accept.
                                if (active.next != null) {
                                    ProgramActionItems(active, onPropose, onShift = { shifting = true }, close = { menu = false })
                                    HorizontalDivider()
                                }
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.programs_title)) },
                                    leadingIcon = { SymbolIcon(Symbol.List, null) },
                                    onClick = { menu = false; openPrograms() },
                                )
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
                    // Saved programs are there to start with no program running too.
                    if (listedPrograms.isNotEmpty()) TextButton(onClick = openPrograms) { Text(stringResource(R.string.programs_title)) }
                }
                active == null -> NoProgram(onImport, openPrograms.takeIf { listedPrograms.isNotEmpty() })
                else -> ProgramWeeks(
                    active, onOpenChanges = { show(shown.copy(changesOpen = true)) }, exerciseNames, sessions, onOpenSession, shown.week,
                    onWeek = { show(shown.copy(week = it)) }, weekdayPlanLine = weekdayPlanLine(weekdayPlan), onOpenWeekdayPlan = { editingWeekdayPlan = true },
                )
            }
        }
    }
    if (shifting) {
        ShiftDialog(
            onReview = { weeks, summary -> shifting = false; onPropose(ProgramActions.shift(summary, weeks)) },
            onDismiss = { shifting = false },
        )
    }
    val replacing = confirmReplace
    if (replacing != null && active != null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = null },
            title = { Text(interFigures(stringResource(R.string.program_replace_title, active.program.title))) },
            // Replacing loses the position, so the question says where you are.
            text = {
                Text(
                    if (active.finished) stringResource(R.string.program_replace_body)
                    else stringResource(R.string.program_replace_body_at, active.position.week, active.program.title),
                )
            },
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

/** What Program shows: the week picked (null for the week you're in) and whether the Changes or Programs page is open. */
data class ProgramView(val week: Int? = null, val changesOpen: Boolean = false, val programsOpen: Boolean = false) {
    companion object {
        val Saver = androidx.compose.runtime.saveable.listSaver<ProgramView, Any?>(
            save = { listOf(it.week, it.changesOpen, it.programsOpen) },
            restore = { ProgramView(it[0] as Int?, it[1] as Boolean, it[2] as Boolean) },
        )
    }
}

@Composable
private fun NoProgram(onImport: () -> Unit, onOpenPrograms: (() -> Unit)?) {
    EmptyState(Symbol.ViewTimeline, stringResource(R.string.today_empty_title), stringResource(R.string.program_none_body)) {
        Button(onClick = onImport) { Text(stringResource(R.string.program_import)) }
        onOpenPrograms?.let { TextButton(onClick = it) { Text(stringResource(R.string.programs_title)) } }
    }
}

@Composable
private fun ProgramWeeks(
    active: ActiveProgram,
    onOpenChanges: () -> Unit,
    names: Map<String, String>,
    sessions: List<PastSessionItem>,
    onOpenSession: (Long) -> Unit,
    /** The week picked; null for the week you're in. */
    picked: Int?,
    onWeek: (Int) -> Unit,
    /** The weekday plan as a line, and what opens its sheet. */
    weekdayPlanLine: String,
    onOpenWeekdayPlan: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val program = active.program
    val next = active.next
    // It opens on the block and week you're in; past the end, on the last.
    val currentWeek = active.position.week.coerceAtMost(program.weeks)
    val week = picked?.takeIf { it in 1..program.weeks } ?: currentWeek
    val block = program.blockAt(week) ?: program.blocks.last()
    val days = remember(active, week, sessions) { ProgramWeek.days(active, week, sessions) }
    val comparison = remember(program, block) { ProgramWeek.comparison(program, block) }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
            // Where you are, with your training days and the Changes under it as one group: the
            // title and Next, then the rows that open the training days and the Changes page.
            val count = active.inForce.size
            val changes = if (count == 0) stringResource(R.string.program_changes_none) else pluralStringResource(R.plurals.program_changes_in_force, count, count)
            val chevron = cs.onSurfaceVariant
            val nextDay = next?.let { stringResource(R.string.program_week_day, it.position.week, it.day.name) }
            SegmentedList(Modifier.padding(top = 8.dp)) {
                item { ProgramHeader(program.title, program.author, nextDay) }
                row(
                    title = weekdayPlanLine,
                    symbol = Symbol.CalendarToday,
                    onClick = onOpenWeekdayPlan,
                    trailing = { SymbolIcon(Symbol.ChevronRight, null, tint = chevron) },
                )
                if (active.history.isNotEmpty()) {
                    row(
                        title = changes,
                        symbol = Symbol.SwapHoriz,
                        onClick = onOpenChanges,
                        trailing = { SymbolIcon(Symbol.ChevronRight, null, tint = chevron) },
                    )
                }
            }
        }
        blockItems(
            program, block, active, comparison, week, days, names,
            onBlock = { i -> program.blocks[i].let { b -> onWeek(if (currentWeek in b.weekStart..b.weekEnd) currentWeek else b.weekStart) } },
            onWeek = onWeek, onOpenSession = onOpenSession,
        )
        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
}

/** The program's title and author, then the next day, or "Program finished" with a flag. */
@Composable
private fun ProgramHeader(title: String, author: String?, next: String?) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = CardPadding).padding(top = 16.dp, bottom = 16.dp)) {
        Text(interFigures(title), style = MaterialTheme.typography.headlineSmall)
        author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = if (next != null) Alignment.Top else Alignment.CenterVertically) {
            if (next != null) {
                Text(
                    stringResource(R.string.program_next),
                    Modifier.width(56.dp).alignByBaseline(),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                )
                Text(next, Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                // The flag takes Next's column, so the words start where the next day's do.
                Box(Modifier.width(56.dp)) { SymbolIcon(Symbol.Flag, null, Modifier.size(20.dp), tint = cs.primary) }
                Text(stringResource(R.string.program_finished), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

internal const val ChangeDetailTag = "change-detail"

/**
 * The Changes, newest first: Revert on each in force; reverted and replaced ones stay, struck
 * through. Tapping one in force opens what it does; any other shows its summary in full.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangesPage(active: ActiveProgram, onRevert: (Long) -> Unit, onOpen: (Long) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var reverting by rememberSaveable { mutableStateOf<Long?>(null) }
    var reading by rememberSaveable { mutableStateOf<Long?>(null) }
    val inForce = active.inForce.map { it.id }.toSet()
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(R.string.program_changes)) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val history = active.history.asReversed()
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Spacer(Modifier.height(8.dp)) }
            itemsIndexed(history, key = { _, it -> "change-${it.id}" }) { i, record ->
                Segment(i, history.size) {
                    ChangeRow(
                        record, active.program, replaced = record.id in active.replaced,
                        onOpen = { if (record.id in inForce) onOpen(record.id) else reading = record.id },
                        onRevert = { reverting = record.id },
                    )
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
    active.history.firstOrNull { it.id == reverting }?.let { record ->
        AlertDialog(
            onDismissRequest = { reverting = null },
            title = { Text(interFigures(stringResource(R.string.program_revert_title, record.change.summary))) },
            text = { Text(stringResource(R.string.program_revert_body)) },
            confirmButton = {
                TextButton(onClick = { reverting = null; onRevert(record.id) }) { Text(stringResource(R.string.program_change_revert)) }
            },
            dismissButton = { TextButton(onClick = { reverting = null }) { Text(stringResource(R.string.program_cancel)) } },
        )
    }
    active.history.firstOrNull { it.id == reading }?.let { record ->
        AlertDialog(
            onDismissRequest = { reading = null },
            modifier = Modifier.testTag(ChangeDetailTag),
            text = {
                Column {
                    Text(interFigures(record.change.summary), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(changeStatus(record, active.program, record.id in active.replaced), style = MaterialTheme.typography.bodyMedium)
                    record.checkIn?.let { made ->
                        Spacer(Modifier.height(16.dp))
                        com.hyperlog.app.ui.coach.CheckInTranscript(made)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reading = null }) { Text(stringResource(R.string.change_close)) } },
        )
    }
}

/**
 * Skip day, repeat week, deload week and shift program, in the menu: each makes a Change and opens
 * its review, so nothing moves until it is accepted.
 */
@Composable
private fun ProgramActionItems(active: ActiveProgram, onPropose: (Change) -> Unit, onShift: () -> Unit, close: () -> Unit) {
    val position = active.position
    val next = active.next ?: return
    val skip = stringResource(R.string.program_skip_summary, position.week, next.day.name)
    val repeat = stringResource(R.string.program_repeat_summary, ProgramActions.repeatedWeek(position))
    val deload = stringResource(R.string.program_deload_summary, position.week)
    ActionItem(stringResource(R.string.program_action_skip), Symbol.SkipNext) { close(); onPropose(ProgramActions.skipDay(skip)) }
    // Named for the week it repeats, the last one trained, which isn't always the week you're in.
    ActionItem(repeat, Symbol.Replay) { close(); onPropose(ProgramActions.repeatWeek(repeat)) }
    ActionItem(stringResource(R.string.program_action_deload), Symbol.Spa) { close(); onPropose(ProgramActions.deloadWeek(deload, position.week)) }
    ActionItem(stringResource(R.string.program_action_shift), Symbol.MoreTime) { close(); onShift() }
}

@Composable
private fun ActionItem(label: String, symbol: Symbol, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { SymbolIcon(symbol, null) }, onClick = onClick)
}

@Composable
private fun SectionHead(text: String) {
    GroupHead(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** `Rest of Build · accepted 13 Oct`, `Reverted 18 Oct`, `Replaced 18 Oct`. */
@Composable
private fun changeStatus(record: ChangeRecord, program: Program, replaced: Boolean): String {
    val revertedOn = record.revertedAt?.let { shortDate(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) }
    val block = program.blocks.firstOrNull { it.id == record.accepted.blockId }?.name
    return if (revertedOn != null && replaced) stringResource(R.string.program_change_replaced, revertedOn)
    else if (revertedOn != null) stringResource(R.string.program_change_reverted, revertedOn)
    else stringResource(R.string.program_change_accepted, scopeText(record.change.scope, block), shortDate(record.accepted.acceptedOn))
}

/** An accepted Change: its summary, period and day, with Revert while it is in force; tapping it opens it in full. */
@Composable
private fun ChangeRow(record: ChangeRecord, program: Program, replaced: Boolean, onOpen: () -> Unit, onRevert: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val reverted = record.revertedAt != null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onOpen).padding(start = CardPadding, end = 4.dp, top = 8.dp, bottom = 8.dp),
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
                changeStatus(record, program, replaced),
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
