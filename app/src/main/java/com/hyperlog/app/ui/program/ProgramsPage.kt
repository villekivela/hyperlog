package com.hyperlog.app.ui.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.KeptProgram
import com.hyperlog.app.ui.coach.shortDate
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.theme.interFigures
import java.time.Instant
import java.time.ZoneId

/**
 * Program → ⋮ → Programs (SPEC, Program → Programs): every program the app holds, newest first,
 * under Running, Saved and Past. Start (a saved one) and Run again (one that has run) ask first
 * while a program runs ([active]), as import does; Delete asks first, and only a program no
 * session trained offers it; every program has Export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsPage(
    kept: List<KeptProgram>,
    active: ActiveProgram?,
    onStart: (Long) -> Unit,
    onRunAgain: (Long) -> Unit,
    onExport: (KeptProgram) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    /** The program waiting for "Replace <title>?", and whether it runs again. */
    var replacing by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val start = { p: KeptProgram ->
        if (active != null) replacing = p.id
        else if (p.status is KeptProgram.Status.Saved) onStart(p.id) else onRunAgain(p.id)
    }
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(R.string.programs_title)) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val sections = listOf(
            R.string.programs_running to kept.filter { it.status is KeptProgram.Status.Running },
            R.string.programs_saved to kept.filter { it.status is KeptProgram.Status.Saved },
            R.string.programs_past to kept.filter { it.status is KeptProgram.Status.Past },
        ).filter { it.second.isNotEmpty() }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            sections.forEach { (head, programs) ->
                item(key = "head-$head") { GroupHead(stringResource(head)) }
                programs.forEachIndexed { i, p ->
                    item(key = "program-${p.id}") {
                        Segment(i, programs.size) { ProgramRow(p, onStart = { start(p) }, onExport = { onExport(p) }, onDelete = { deleting = p.id }) }
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
    val replaced = kept.firstOrNull { it.id == replacing }
    if (replaced != null && active != null) {
        AlertDialog(
            onDismissRequest = { replacing = null },
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
                    replacing = null
                    if (replaced.status is KeptProgram.Status.Saved) onStart(replaced.id) else onRunAgain(replaced.id)
                }) { Text(stringResource(R.string.program_replace)) }
            },
            dismissButton = { TextButton(onClick = { replacing = null }) { Text(stringResource(R.string.program_cancel)) } },
        )
    }
    kept.firstOrNull { it.id == deleting }?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(interFigures(stringResource(R.string.programs_delete_title, p.title))) },
            text = { Text(stringResource(R.string.programs_delete_body)) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(p.id) }) { Text(stringResource(R.string.programs_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.program_cancel)) } },
        )
    }
}

/**
 * A program: its title, then its blocks and weeks with where it stands (`3 blocks · 12 weeks ·
 * Week 6`, `Saved 24 Oct`, `1 Sep – 13 Oct`); Start or Run again, and ⋮ for Export and Delete.
 */
@Composable
private fun ProgramRow(p: KeptProgram, onStart: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    val program = p.program
    val size = pluralStringResource(R.plurals.programs_blocks, program.blocks.size, program.blocks.size) + " · " +
        pluralStringResource(R.plurals.program_review_weeks, program.weeks, program.weeks)
    val status = when (val s = p.status) {
        is KeptProgram.Status.Running -> if (s.finished) stringResource(R.string.program_finished) else stringResource(R.string.programs_week, s.position.week)
        is KeptProgram.Status.Saved -> stringResource(R.string.programs_saved_on, shortDate(dateOf(s.savedAt)))
        is KeptProgram.Status.Past -> s.endedAt?.let { stringResource(R.string.programs_ran, shortDate(dateOf(s.startedAt)), shortDate(dateOf(it))) }
            ?: stringResource(R.string.programs_started, shortDate(dateOf(s.startedAt)))
    }
    // A finished program runs again from here too.
    val action = when (val s = p.status) {
        is KeptProgram.Status.Saved -> R.string.programs_start
        is KeptProgram.Status.Past -> R.string.programs_run_again
        is KeptProgram.Status.Running -> if (s.finished) R.string.programs_run_again else null
    }
    var menu by rememberSaveable { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = CardPadding, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(interFigures(p.title), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(interFigures("$size · $status"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        action?.let { TextButton(onClick = onStart) { Text(stringResource(it)) } }
        Box {
            IconButton(onClick = { menu = true }) { SymbolIcon(Symbol.MoreVert, stringResource(R.string.programs_more, p.title), tint = cs.onSurfaceVariant) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.programs_export)) },
                    leadingIcon = { SymbolIcon(Symbol.Download, null) },
                    onClick = { menu = false; onExport() },
                )
                if (p.status !is KeptProgram.Status.Running && !p.trained) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.programs_delete)) },
                        leadingIcon = { SymbolIcon(Symbol.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

private fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** The name Export suggests for a program's file: `hyperlog-full-body.json`. */
fun programFileName(title: String): String =
    title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "program" } + ".json"
