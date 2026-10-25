package com.hyperlog.app.ui.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ListedProgram
import com.hyperlog.app.ui.coach.shortDate
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.SegmentedList
import com.hyperlog.app.ui.theme.SegmentedRow
import com.hyperlog.app.ui.theme.Sheet
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.theme.interFigures
import java.time.Instant
import java.time.ZoneId

/**
 * Program → ⋮ → Programs (SPEC, Program → Programs): every program the app holds, newest first,
 * under Running, Saved and Past. A saved one opens its own page ([onOpenSaved]); another's row
 * opens its sheet: Resume (one replaced mid-way) and Run again (one that has run), which ask first
 * while a program runs ([active]) as import does, and Export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsPage(
    kept: List<ListedProgram>,
    active: ActiveProgram?,
    onRunAgain: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onOpenSaved: (Long) -> Unit,
    onExport: (ListedProgram) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    /** The program whose sheet is up. */
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    /** The program waiting for "Replace <title>?", and whether it resumes or runs again. */
    var replacing by rememberSaveable { mutableStateOf<Long?>(null) }
    var resuming by rememberSaveable { mutableStateOf(false) }
    val run = { p: ListedProgram, resume: Boolean -> if (resume) onResume(p.id) else onRunAgain(p.id) }
    val start = { p: ListedProgram, resume: Boolean ->
        if (active != null) { replacing = p.id; resuming = resume } else run(p, resume)
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
            R.string.programs_running to kept.filter { it.status is ListedProgram.Status.Running },
            R.string.programs_saved to kept.filter { it.status is ListedProgram.Status.Saved },
            R.string.programs_past to kept.filter { it.status is ListedProgram.Status.Past },
        ).filter { it.second.isNotEmpty() }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            sections.forEach { (head, programs) ->
                item(key = "head-$head") { GroupHead(stringResource(head)) }
                programs.forEachIndexed { i, p ->
                    item(key = "program-${p.id}") {
                        Segment(i, programs.size, onClick = { if (p.status is ListedProgram.Status.Saved) onOpenSaved(p.id) else open = p.id }) {
                            SegmentedRow(p.title, "${listedSize(p)} · ${listedStatus(p)}", symbol(p), selected = p.status is ListedProgram.Status.Running)
                        }
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
    kept.firstOrNull { it.id == open }?.let { p ->
        Sheet(onDismiss = { open = null }) {
            ProgramSheet(
                p,
                onRunAgain = { open = null; start(p, false) },
                onResume = { open = null; start(p, true) },
                onExport = { open = null; onExport(p) },
            )
        }
    }
    val replaced = kept.firstOrNull { it.id == replacing }
    if (replaced != null && active != null) {
        AlertDialog(
            onDismissRequest = { replacing = null },
            title = { Text(interFigures(stringResource(R.string.program_replace_title, active.program.title))) },
            // Replacing loses the position, so the question says where you are, and where the other starts.
            text = {
                val at = (replaced.status as? ListedProgram.Status.Past)?.resumeAt?.takeIf { resuming }
                Text(
                    when {
                        at != null && active.finished -> stringResource(R.string.program_resume_body, replaced.title, at.week, at.day + 1)
                        at != null -> stringResource(R.string.program_resume_body_at, active.position.week, active.program.title, replaced.title, at.week, at.day + 1)
                        active.finished -> stringResource(R.string.program_run_again_body, replaced.title)
                        else -> stringResource(R.string.program_run_again_body_at, active.position.week, active.program.title, replaced.title)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    replacing = null
                    run(replaced, resuming)
                }) { Text(stringResource(R.string.program_replace)) }
            },
            dismissButton = { TextButton(onClick = { replacing = null }) { Text(stringResource(R.string.program_cancel)) } },
        )
    }
}

/** Its blocks and weeks: `4 blocks · 12 weeks`. */
@Composable
internal fun listedSize(p: ListedProgram): String =
    pluralStringResource(R.plurals.programs_blocks, p.program.blocks.size, p.program.blocks.size) + " · " +
        pluralStringResource(R.plurals.program_review_weeks, p.program.weeks, p.program.weeks)

/** Where it stands: `Week 6` (or Program finished), `Saved 24 Oct`, `1 Sep – 13 Oct`. */
@Composable
internal fun listedStatus(p: ListedProgram): String = when (val s = p.status) {
    is ListedProgram.Status.Running -> if (s.finished) stringResource(R.string.program_finished) else stringResource(R.string.programs_week, s.position.week)
    is ListedProgram.Status.Saved -> stringResource(R.string.programs_saved_on, shortDate(dateOf(s.savedAt)))
    is ListedProgram.Status.Past -> s.endedAt?.let { ran(s.startedAt, it) } ?: stringResource(R.string.programs_started, shortDate(dateOf(s.startedAt)))
}

/** The days a run took: `1 Sep – 13 Oct`, or `25 Oct` for one that ended the day it started. */
private fun ran(startedAt: Long, endedAt: Long): String {
    val (from, to) = dateOf(startedAt) to dateOf(endedAt)
    return if (from == to) shortDate(from) else "${shortDate(from)} – ${shortDate(to)}"
}

/**
 * Where it stands on its sheet, which has no group head: `You're in week 7`, `Ran 1 Jun – 31 Aug`,
 * and where one replaced mid-way was left: `Ran 1 Sep – 13 Oct · Left in week 3`.
 */
@Composable
private fun sheetStatus(p: ListedProgram): String = when (val s = p.status) {
    is ListedProgram.Status.Running -> if (s.finished) stringResource(R.string.program_finished) else stringResource(R.string.programs_sheet_week, s.position.week)
    is ListedProgram.Status.Saved -> listedStatus(p)
    is ListedProgram.Status.Past -> listOfNotNull(
        s.endedAt?.let { stringResource(R.string.programs_sheet_ran, ran(s.startedAt, it)) } ?: listedStatus(p),
        s.resumeAt?.let { stringResource(R.string.programs_sheet_left, it.week) },
    ).joinToString(" · ")
}

/** The row's symbol says where it stands too: running (finished: the flag Next shows), saved, past. */
private fun symbol(p: ListedProgram): Symbol = when (val s = p.status) {
    is ListedProgram.Status.Running -> if (s.finished) Symbol.Flag else Symbol.PlayArrow
    is ListedProgram.Status.Saved -> Symbol.Bookmark
    is ListedProgram.Status.Past -> Symbol.History
}

/**
 * A program's sheet: its head as the review's (title, author, weeks and days a week, where it
 * stands, the description), its main way to run as the sheet's button (Resume for one replaced
 * mid-way, else Run again for one that has run or the running one once finished), then Run again
 * (below Resume) and Export as rows.
 */
@Composable
private fun ProgramSheet(p: ListedProgram, onRunAgain: () -> Unit, onResume: () -> Unit, onExport: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val program = p.program
    val perWeek = program.blocks.first().days.size
    val resumes = (p.status as? ListedProgram.Status.Past)?.resumeAt != null
    val runsAgain = when (val s = p.status) {
        is ListedProgram.Status.Saved -> false
        is ListedProgram.Status.Past -> true
        is ListedProgram.Status.Running -> s.finished
    }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(interFigures(p.title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                interFigures(
                    listOfNotNull(
                        program.author,
                        pluralStringResource(R.plurals.program_review_weeks, program.weeks, program.weeks),
                        pluralStringResource(R.plurals.program_review_days, perWeek, perWeek),
                    ).joinToString(" · "),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
            )
            // Without its group head, where it stands says what it is: `You're in week 7`, `Ran 1 Jun – 31 Aug`.
            Text(sheetStatus(p), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            program.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            val main = when {
                resumes -> Triple(R.string.programs_resume, Symbol.PlayArrow, onResume)
                runsAgain -> Triple(R.string.programs_run_again, Symbol.Replay, onRunAgain)
                else -> null
            }
            main?.let { (label, symbol, onClick) ->
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onClick,
                    Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.MediumContentPadding,
                ) {
                    SymbolIcon(symbol, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        // Read before the list: its builder isn't composable.
        val runAgainLabel = stringResource(R.string.programs_run_again)
        val exportLabel = stringResource(R.string.programs_export)
        SegmentedList(color = Tones.sheetCard) {
            if (resumes) row(runAgainLabel, symbol = Symbol.Replay, onClick = onRunAgain)
            row(exportLabel, symbol = Symbol.Download, onClick = onExport)
        }
    }
}

private fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** The name Export suggests for a program's file: `hyperlog-full-body.json`. */
fun programFileName(title: String): String =
    title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "program" } + ".json"
