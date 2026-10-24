package com.hyperlog.app.ui.coach

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.CheckInFailure
import com.hyperlog.app.coach.CheckInStep
import com.hyperlog.app.coach.CheckInView
import com.hyperlog.app.coach.Tile
import com.hyperlog.app.library.MuscleGroup
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.ui.program.Problem
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.GroupGap
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.SegmentedRow
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.theme.interFigures

private val Margin = 24.dp

/** A tile on the first step: its label and symbol. */
private data class TileLook(val label: Int, val symbol: Symbol)

private val Looks = mapOf(
    Tile.Hurts to TileLook(R.string.checkin_tile_hurts, Symbol.PanTool),
    Tile.ShortOnTime to TileLook(R.string.checkin_tile_short, Symbol.Timer),
    Tile.Gym to TileLook(R.string.checkin_tile_gym, Symbol.LocationOn),
    Tile.Tired to TileLook(R.string.checkin_tile_tired, Symbol.AirlineSeatFlat),
    Tile.ChangeProgram to TileLook(R.string.checkin_tile_change, Symbol.Tune),
    Tile.Workout to TileLook(R.string.checkin_tile_workout, Symbol.FitnessCenter),
    Tile.NewProgram to TileLook(R.string.checkin_tile_new_program, Symbol.ViewTimeline),
    Tile.SomethingElse to TileLook(R.string.checkin_tile_else, Symbol.Edit),
)

/** The answer buttons of a question, for tests. */
internal const val AnswerTag = "checkin-answer"

/**
 * Coach as a check-in with Claude (SPEC, Coaching → Check-in; docs/design/coach.md): "What's going
 * on?" with its tiles and the Changes in force, then Claude's questions one a screen, and its
 * answer in words or the problems of a reply it couldn't fix. A Change or a workout opens its
 * review, which the caller shows over this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    /** The check-in under way; null on the tiles. */
    view: CheckInView?,
    program: ActiveProgram?,
    onTile: (Tile) -> Unit,
    onAnswer: (String) -> Unit,
    /** Back on the tiles: leave Coach. */
    onLeave: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onTellMeMore: () -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
    /** The focus's marks, shown on the answers that name those groups (`Back · 4 sets`). */
    lowest: List<Pair<String, Double>> = emptyList(),
    /** The Changes in force page is open; kept by the caller, so a review over it comes back to it. */
    inForceOpen: Boolean = false,
    onInForceOpen: (Boolean) -> Unit = {},
    onOpenChange: (Long) -> Unit = {},
    onRevert: (Long) -> Unit = {},
    onHowItsGoing: (ChangeRecord) -> Unit = {},
    /** Ask Claude in the Claude app instead, after the key is refused or the spend limit is hit. */
    onAskClaude: () -> Unit = {},
    onPaste: () -> Unit = {},
    pasteErrors: List<String>? = null,
    onCopyProblems: (List<String>) -> Unit = {},
) {
    when {
        view != null -> Step(view, lowest, onAnswer, onBack, onClose, onTellMeMore, onTryAgain, modifier, onAskClaude, onPaste, pasteErrors, onCopyProblems)
        inForceOpen && program != null && program.inForce.isNotEmpty() -> InForcePage(
            program, onBack = { onInForceOpen(false) }, onOpenChange, onRevert, onHowItsGoing = { onInForceOpen(false); onHowItsGoing(it) }, modifier,
        )
        else -> Tiles(program, onTile, onLeave, onInForce = { onInForceOpen(true) }, modifier)
    }
}

/** The first step: "What's going on?" over the tiles, and the Changes in force in one line above them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tiles(program: ActiveProgram?, onTile: (Tile) -> Unit, onLeave: () -> Unit, onInForce: () -> Unit, modifier: Modifier) {
    BackHandler(onBack = onLeave)
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(colors = hyperlogTopBarColors(), title = { Text(stringResource(R.string.coach_title), Modifier.padding(start = 8.dp)) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = GroupGap)) {
            val inForce = program?.inForce.orEmpty()
            if (inForce.isNotEmpty()) {
                Segment(0, 1, onClick = onInForce) {
                    SegmentedRow(
                        pluralStringResource(R.plurals.checkin_in_force, inForce.size, inForce.size),
                        inForce.first().change.summary,
                        Symbol.Tune,
                        trailing = { SymbolIcon(Symbol.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
                Spacer(Modifier.size(GroupGap))
            }
            Text(
                stringResource(R.string.checkin_question),
                Modifier.padding(horizontal = Margin),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.checkin_intro),
                Modifier.padding(horizontal = Margin).padding(top = 4.dp, bottom = GroupGap),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val tiles = Tile.entries
            tiles.forEachIndexed { i, tile ->
                val look = Looks.getValue(tile)
                Segment(i, tiles.size, onClick = { onTile(tile) }) {
                    SegmentedRow(stringResource(look.label), symbol = look.symbol)
                }
            }
        }
    }
}

/** The Changes in force, newest first: each with how long it runs, Revert and How's it going?. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InForcePage(
    program: ActiveProgram,
    onBack: () -> Unit,
    onOpenChange: (Long) -> Unit,
    onRevert: (Long) -> Unit,
    onHowItsGoing: (ChangeRecord) -> Unit,
    modifier: Modifier,
) {
    BackHandler(onBack = onBack)
    var reverting by rememberSaveable { mutableStateOf<Long?>(null) }
    val inForce = program.inForce
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(R.string.checkin_in_force_title)) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.checkin_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = GroupGap)) {
            inForce.forEachIndexed { i, record ->
                Segment(i, inForce.size, onClick = { onOpenChange(record.id) }, onClickLabel = stringResource(R.string.coach_open_change)) {
                    InForce(
                        record, program,
                        onRevert = { reverting = record.id },
                        onHowItsGoing = { onHowItsGoing(record) },
                        howItsGoing = stringResource(R.string.checkin_hows_it_going),
                    )
                }
            }
        }
    }
    inForce.firstOrNull { it.id == reverting }?.let { record ->
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
}

/** One of Claude's steps, with Back and × in the bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step(
    view: CheckInView,
    lowest: List<Pair<String, Double>>,
    onAnswer: (String) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onTellMeMore: () -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier,
    onAskClaude: () -> Unit,
    onPaste: () -> Unit,
    pasteErrors: List<String>?,
    onCopyProblems: (List<String>) -> Unit,
) {
    BackHandler(onBack = onBack)
    val step = view.step
    val title = view.checkIn.tile?.let { stringResource(Looks.getValue(it).label) } ?: stringResource(R.string.checkin_title)
    /** Ask Claude was pressed for this failure: Paste reply is the next step. */
    var asked by rememberSaveable(view.checkIn.turns.size) { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.checkin_back)) } },
                actions = { IconButton(onClick = onClose) { SymbolIcon(Symbol.Close, stringResource(R.string.checkin_close)) } },
            )
        },
        bottomBar = {
            when (step) {
                is CheckInStep.Words -> BottomActions {
                    TextButton(onClick = onTellMeMore) { Text(stringResource(R.string.checkin_tell_me_more)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onClose) { Text(stringResource(R.string.checkin_done)) }
                }
                is CheckInStep.Problems -> BottomActions {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.program_cancel)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onTryAgain) { Text(stringResource(R.string.checkin_try_again)) }
                }
                is CheckInStep.Failed -> BottomActions {
                    val handOff = step.failure != CheckInFailure.NoConnection
                    val retry = step.failure == CheckInFailure.NoConnection || step.failure == CheckInFailure.Refused
                    when {
                        asked -> {
                            TextButton(onClick = onAskClaude) { Text(stringResource(R.string.coach_ask)) }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = onPaste) { Text(stringResource(R.string.coach_paste)) }
                        }
                        retry && handOff -> {
                            TextButton(onClick = { asked = true; onAskClaude() }) { Text(stringResource(R.string.coach_ask)) }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = onTryAgain) { Text(stringResource(R.string.checkin_try_again)) }
                        }
                        retry -> {
                            Spacer(Modifier.weight(1f))
                            Button(onClick = onTryAgain) { Text(stringResource(R.string.checkin_try_again)) }
                        }
                        else -> {
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { asked = true; onAskClaude() }) { Text(stringResource(R.string.coach_ask)) }
                        }
                    }
                }
                else -> Unit
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (step) {
                // A review shows over this screen; until it does, the step reads as thinking.
                CheckInStep.Thinking, is CheckInStep.ChangeReview, is CheckInStep.WorkoutReview -> Thinking()
                is CheckInStep.Question -> Question(step, lowest, onAnswer, key = view.checkIn.turns.size)
                is CheckInStep.Words -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Margin, vertical = 8.dp)) {
                    Text(step.text, style = MaterialTheme.typography.bodyLarge)
                }
                is CheckInStep.Problems -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Margin, vertical = 8.dp)) {
                    Text(stringResource(R.string.checkin_problems_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.checkin_problems_body),
                        Modifier.padding(top = 4.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    step.errors.forEach { Problem(it) }
                }
                is CheckInStep.Failed -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    val (head, body) = when (step.failure) {
                        CheckInFailure.NoConnection -> R.string.checkin_no_connection to R.string.checkin_no_connection_body
                        CheckInFailure.KeyRefused -> R.string.checkin_key_refused to R.string.checkin_ask_claude_body
                        CheckInFailure.SpendLimit -> R.string.checkin_spend_limit to R.string.checkin_spend_limit_body
                        CheckInFailure.Refused -> R.string.checkin_refused to R.string.checkin_refused_body
                    }
                    Text(stringResource(head), Modifier.padding(horizontal = Margin), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(body),
                        Modifier.padding(horizontal = Margin).padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (step.failure == CheckInFailure.SpendLimit) {
                        Text(
                            stringResource(R.string.checkin_ask_claude_body),
                            Modifier.padding(horizontal = Margin).padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (pasteErrors != null) PasteProblems(pasteErrors, onCopyProblems)
                }
            }
        }
    }
}

@Composable
private fun Thinking() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.checkin_thinking),
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Claude's question over its answers to tap, and Type my own under them when Claude allows it. An
 * answer naming a muscle group behind this week carries its sets (`Back · 4 sets`).
 */
@Composable
private fun Question(step: CheckInStep.Question, lowest: List<Pair<String, Double>>, onAnswer: (String) -> Unit, key: Int) {
    var typing by rememberSaveable(key) { mutableStateOf(false) }
    var own by rememberSaveable(key) { mutableStateOf("") }
    val marks = lowest.toMap()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = GroupGap)) {
        Text(step.question, Modifier.padding(horizontal = Margin).padding(bottom = GroupGap), style = MaterialTheme.typography.headlineSmall)
        step.answers.forEachIndexed { i, answer ->
            val group = MuscleGroup.entries.firstOrNull { it.label.equals(answer, ignoreCase = true) }?.label
            val label = group?.let { g -> marks[g]?.let { stringResource(R.string.coach_focus_marked, answer, setsText(it)) } } ?: answer
            Segment(i, step.answers.size, onClick = { onAnswer(answer) }, modifier = Modifier.testTag(AnswerTag)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = CardPadding + 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    SymbolIcon(Symbol.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (step.own) {
            if (!typing) {
                TextButton(onClick = { typing = true }, modifier = Modifier.padding(start = Margin - 12.dp, top = 8.dp)) {
                    SymbolIcon(Symbol.Keyboard, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.checkin_own))
                }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                val send = { if (own.isNotBlank()) onAnswer(own.trim()) }
                Row(Modifier.fillMaxWidth().padding(start = Margin, end = 12.dp, top = GroupGap), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        own, { own = it },
                        Modifier.weight(1f).focusRequester(focus),
                        label = { Text(stringResource(R.string.checkin_own_label)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                    )
                    IconButton(onClick = send, enabled = own.isNotBlank()) { SymbolIcon(Symbol.ArrowForward, stringResource(R.string.checkin_send)) }
                }
            }
        }
    }
}

/** The check-in a Change keeps: what opened it, then each of Claude's questions over your answer. */
@Composable
internal fun CheckInTranscript(made: com.hyperlog.app.coach.SavedCheckIn) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.checkin_transcript), style = MaterialTheme.typography.labelLarge, color = cs.primary)
        Text(made.opening, style = MaterialTheme.typography.bodyMedium)
        made.steps.forEach { step ->
            Text(step.question, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Text(step.answer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** `0 sets`, `1 set`, `2.5 sets`. */
@Composable
private fun setsText(sets: Double): String {
    val n = com.hyperlog.app.domain.formatKg(sets)
    return if (sets == 1.0) stringResource(R.string.coach_focus_set, n) else stringResource(R.string.coach_focus_sets, n)
}
