package com.hyperlog.app.ui.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.ProgramProposal
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ListedProgram
import com.hyperlog.app.ui.coach.ProgramReviewList
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.SegmentedRow
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.theme.interFigures

/**
 * A saved program's page (SPEC, Program → Programs → Saved), opened from its row on Programs or on
 * Today: the program read-only as its review showed it, its new exercises with it, and Start
 * program, which asks first while a program runs ([running]) as import does. ⋮ has Export and
 * Delete, which asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedProgramScreen(
    saved: ListedProgram,
    exerciseNames: Map<String, String>,
    running: ActiveProgram?,
    onStart: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var menu by remember { mutableStateOf(false) }
    var replacing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    // Its swaps were for the review; what it adds to the library waits with it.
    val proposal = remember(saved) {
        ProgramProposal(saved.program, saved.source, emptyList(), (saved.status as? ListedProgram.Status.Saved)?.newExercises.orEmpty())
    }
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(R.string.programs_saved_title)) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { SymbolIcon(Symbol.MoreVert, stringResource(R.string.session_menu)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.programs_export)) },
                                leadingIcon = { SymbolIcon(Symbol.Download, null) },
                                onClick = { menu = false; onExport() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.programs_delete)) },
                                leadingIcon = { SymbolIcon(Symbol.Delete, null) },
                                onClick = { menu = false; deleting = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomActions {
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (running != null) replacing = true else onStart() }) {
                    Text(stringResource(R.string.program_review_start), maxLines = 1)
                }
            }
        },
    ) { padding ->
        // When it was saved, as its row on Programs says: `Saved 24 Oct`.
        ProgramReviewList(proposal, exerciseNames, origin = status(saved), Modifier.fillMaxSize().padding(padding))
    }
    if (replacing && running != null) {
        AlertDialog(
            onDismissRequest = { replacing = false },
            title = { Text(interFigures(stringResource(R.string.program_replace_title, running.program.title))) },
            // Replacing loses the position, so the question says where you are.
            text = {
                Text(
                    if (running.finished) stringResource(R.string.program_replace_body)
                    else stringResource(R.string.program_replace_body_at, running.position.week, running.program.title),
                )
            },
            confirmButton = { TextButton(onClick = { replacing = false; onStart() }) { Text(stringResource(R.string.program_replace)) } },
            dismissButton = { TextButton(onClick = { replacing = false }) { Text(stringResource(R.string.program_cancel)) } },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(interFigures(stringResource(R.string.programs_delete_title, saved.title))) },
            text = { Text(stringResource(R.string.programs_delete_body)) },
            confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text(stringResource(R.string.programs_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.program_cancel)) } },
        )
    }
}

/**
 * Today while no program runs, or the one running is finished (SPEC, Starter program): [main], the
 * starter offer or the empty state, then "Your saved programs", each opening its page. The group
 * sits at the bottom when everything fits, with [main] a little above the middle of the space over
 * it; on a small screen or at a large font the whole screen scrolls, so neither is squeezed.
 */
@Composable
fun WithSavedPrograms(saved: List<ListedProgram>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier, main: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val viewport = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport),
            // Equal space over [main], under it and again over the group: it sits a third of the
            // free space above the group, the optical centre the other empty states keep.
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier)
            main()
            Spacer(Modifier)
            Column(Modifier.padding(bottom = 16.dp)) {
                GroupHead(stringResource(R.string.today_saved_programs))
                saved.forEachIndexed { i, p ->
                    Segment(i, saved.size, onClick = { onOpen(p.id) }) { SegmentedRow(p.title, "${size(p)} · ${status(p)}", Symbol.Bookmark) }
                }
            }
        }
    }
}
