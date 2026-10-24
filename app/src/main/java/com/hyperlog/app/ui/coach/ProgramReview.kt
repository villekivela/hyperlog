package com.hyperlog.app.ui.coach

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.ProgramProposal
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.Position
import com.hyperlog.app.ui.program.ProgramWeek
import com.hyperlog.app.ui.program.blockItems
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.SectionCard
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.SegmentedRow
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.theme.interFigures

internal const val ProgramReviewTag = "program-review"

/**
 * A new program from Claude, before it runs (SPEC, Coaching → New program): its title and what it
 * is, each swap to a library exercise, the exercises it adds to the library, then the program as
 * Program shows it, block by block and week by week. Start program asks first when a program runs
 * ([running]), as import does; Reject, × and Back drop it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramReviewScreen(
    proposal: ProgramProposal,
    exerciseNames: Map<String, String>,
    running: ActiveProgram?,
    onStart: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    /** × on a check-in's review drops the check-in, where Reject and Back step back; null: × is Reject. */
    onClose: (() -> Unit)? = null,
) {
    BackHandler(onBack = onReject)
    var replacing by rememberSaveable { mutableStateOf(false) }
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
    val cs = MaterialTheme.colorScheme
    val program = proposal.program
    // The new exercises aren't in the library yet: their names come with the proposal.
    val names = remember(exerciseNames, proposal) { exerciseNames + proposal.newExercises.associate { it.id to it.name } }
    // It reads as it will run: from week 1, day 1, with nothing trained yet.
    val fresh = remember(program) { ActiveProgram(0, program, Position(1, 0)) }
    var week by rememberSaveable(proposal) { mutableStateOf(1) }
    val block = program.blockAt(week) ?: program.blocks.first()
    val days = remember(fresh, week) { ProgramWeek.days(fresh, week, emptyList()) }
    val comparison = remember(program, block) { ProgramWeek.comparison(program, block) }
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(R.string.program_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose ?: onReject) {
                        SymbolIcon(Symbol.Close, stringResource(if (onClose != null) R.string.checkin_close else R.string.change_reject))
                    }
                },
            )
        },
        bottomBar = {
            BottomActions(padding = PaddingValues(16.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onReject) { Text(stringResource(R.string.change_reject)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (running != null) replacing = true else onStart() }) { Text(stringResource(R.string.program_review_start)) }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag(ProgramReviewTag)) {
            // What Claude built, in one card at the top, as a workout's review has.
            item(key = "head") {
                SectionCard(Modifier.padding(top = 8.dp)) {
                    Text(interFigures(program.title), style = MaterialTheme.typography.headlineSmall)
                    program.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                    Spacer(Modifier.height(4.dp))
                    val perWeek = program.blocks.first().days.size
                    Text(
                        interFigures(
                            pluralStringResource(R.plurals.program_review_weeks, program.weeks, program.weeks) + " · " +
                                pluralStringResource(R.plurals.program_review_days, perWeek, perWeek),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                    )
                    program.description?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
            if (proposal.swaps.isNotEmpty()) {
                item(key = "swaps-head") { GroupHead(stringResource(R.string.program_review_swaps)) }
                proposal.swaps.forEachIndexed { i, swap ->
                    item(key = "swap-$i") {
                        Segment(i, proposal.swaps.size) {
                            SegmentedRow(stringResource(R.string.program_review_swap, swap.from, names[swap.to] ?: swap.to), symbol = Symbol.SwapHoriz)
                        }
                    }
                }
            }
            if (proposal.newExercises.isNotEmpty()) {
                item(key = "new-head") { GroupHead(stringResource(R.string.program_review_new)) }
                proposal.newExercises.forEachIndexed { i, exercise ->
                    item(key = "new-${exercise.id}") {
                        Segment(i, proposal.newExercises.size) { SegmentedRow(exercise.name, exercise.summary, Symbol.LibraryAdd) }
                    }
                }
            }
            item(key = "gap") { Spacer(Modifier.height(16.dp)) }
            blockItems(
                program, block, fresh, comparison, week, days, names,
                onBlock = { i -> week = program.blocks[i].weekStart },
                onWeek = { week = it }, onOpenSession = {},
            )
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }
}
