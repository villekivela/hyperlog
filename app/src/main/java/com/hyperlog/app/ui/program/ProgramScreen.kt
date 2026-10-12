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
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Block
import com.hyperlog.app.program.Program
import com.hyperlog.app.ui.session.Tnum
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

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
) {
    /** The replacement waiting for "Replace <title>?": a file, or the starter program. */
    var confirmReplace by rememberSaveable { mutableStateOf<Replacement?>(null) }
    var menu by rememberSaveable { mutableStateOf(false) }
    val import = { if (active != null) confirmReplace = Replacement.File else onImport() }
    BackHandler { if (errors != null) onCloseErrors() else onBack() }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.program_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (errors != null) onCloseErrors() else onBack() }) {
                        SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
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
                else -> ProgramWeeks(active)
            }
        }
    }
    val replacing = confirmReplace
    if (replacing != null && active != null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = null },
            title = { Text(stringResource(R.string.program_replace_title, active.program.title)) },
            text = { Text(stringResource(R.string.program_replace_body)) },
            confirmButton = { TextButton(onClick = { confirmReplace = null }) { Text(stringResource(R.string.program_cancel)) } },
            dismissButton = {
                TextButton(onClick = {
                    confirmReplace = null
                    if (replacing == Replacement.Starter) onRunStarter() else onImport()
                }) { Text(stringResource(R.string.program_replace)) }
            },
        )
    }
}

private enum class Replacement { File, Starter }

@Composable
private fun NoProgram(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SymbolIcon(Symbol.ViewTimeline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.program_none_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onImport) { Text(stringResource(R.string.program_import)) }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ProgramWeeks(active: ActiveProgram) {
    val cs = MaterialTheme.colorScheme
    val program = active.program
    val next = active.next
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = Margin).padding(top = 8.dp, bottom = 8.dp)) {
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
            }
        }
        program.blocks.forEach { block ->
            val current = active.position.week in block.weekStart..block.weekEnd
            item(key = "block-${block.id}") { BlockHead(block, current) }
            items((block.weekStart..block.weekEnd).toList(), key = { "week-$it" }) { week ->
                WeekRow(week, block, active)
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
}

@Composable
private fun BlockHead(block: Block, current: Boolean) {
    val text = if (block.weekStart == block.weekEnd) stringResource(R.string.program_block_week, block.name, block.weekStart)
    else stringResource(R.string.program_block_weeks, block.name, block.weekStart, block.weekEnd)
    Text(
        text,
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = Margin).padding(top = 20.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
    )
}

/** A week: its number and a token per day, done, next or to come. The Next line names the next day. */
@Composable
private fun WeekRow(week: Int, block: Block, active: ActiveProgram) {
    val cs = MaterialTheme.colorScheme
    val position = active.position
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = Margin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$week",
            Modifier.width(40.dp),
            style = MaterialTheme.typography.titleMedium.merge(Tnum),
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

@Composable
private fun DayToken(letter: String, state: DayState, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = Modifier.size(32.dp).then(
        when (state) {
            DayState.Done -> Modifier.background(cs.surfaceContainerHighest, CircleShape)
            DayState.Next -> Modifier.background(cs.primary, CircleShape)
            DayState.Upcoming -> Modifier.border(1.dp, cs.outlineVariant, CircleShape)
        },
    )
    Box(modifier.then(shape), contentAlignment = Alignment.Center) {
        Text(
            letter,
            style = MaterialTheme.typography.labelLarge,
            color = if (state == DayState.Next) cs.onPrimary else cs.onSurfaceVariant,
            fontWeight = if (state == DayState.Next) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** A rejected file's problems, path then message, so it can be fixed in one pass. */
@Composable
private fun ImportErrors(errors: List<String>, onClose: () -> Unit, onRetry: () -> Unit) {
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
            items(errors) { error ->
                val split = error.indexOf(": ")
                Column(Modifier.fillMaxWidth().padding(horizontal = Margin, vertical = 8.dp)) {
                    if (split > 0 && !error.startsWith("not valid JSON")) {
                        val path = error.substring(0, split)
                        Text(
                            // Zero-width spaces let the path break after its dots; it reads as written.
                            path.replace(".", ".\u200B"),
                            Modifier.clearAndSetSemantics { text = AnnotatedString(path) },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = cs.onSurfaceVariant,
                        )
                        Text(error.substring(split + 2), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
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
