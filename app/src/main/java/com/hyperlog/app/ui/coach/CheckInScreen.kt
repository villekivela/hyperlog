package com.hyperlog.app.ui.coach

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.SectionCard
import com.hyperlog.app.ui.theme.SegmentGap
import com.hyperlog.app.ui.theme.ShapedSymbol
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.hyperlog.app.coach.Opening
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

/** The bottom strip's padding: a text button first keeps its label on the 24 dp edge. */
private val StripPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)

/** Ease back in's words end so (`coach_ease_back_words`). */
/** A tile on the first step: its label and symbol. */
internal data class TileLook(val label: Int, val symbol: Symbol)

internal val TileLooks = mapOf(
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
    /**
     * Type my own's words while it's open (null: closed), kept by the caller so they survive a tab
     * switch and come back on Back to a question answered in your own words. Without [onDraft] the
     * screen keeps its own.
     */
    draft: String? = null,
    onDraft: ((String?) -> Unit)? = null,
    /** Attach a spreadsheet, PDF or photo, on a step that offers it: the caller opens the file picker. */
    onAttach: () -> Unit = {},
    /** What became of the file picked, under Attach: being read, or why it can't go. */
    attachNote: String? = null,
    /** What Claude is doing while it replies, as its reply streams. */
    progress: com.hyperlog.app.coach.CoachProgress? = null,
    /** A new program was just saved for later: the tiles say so once, with a way to Programs. */
    savedNotice: Boolean = false,
    onSavedNoticeShown: () -> Unit = {},
    onOpenPrograms: () -> Unit = {},
) {
    when {
        view != null -> Step(view, lowest, onAnswer, onBack, onClose, onTellMeMore, onTryAgain, modifier, onAskClaude, onPaste, pasteErrors, onCopyProblems, draft, onDraft, onAttach, attachNote, progress)
        inForceOpen && program != null && program.inForce.isNotEmpty() -> InForcePage(
            program, onBack = { onInForceOpen(false) }, onOpenChange, onRevert, onHowItsGoing = { onInForceOpen(false); onHowItsGoing(it) }, modifier,
        )
        else -> Tiles(program, onTile, onLeave, onInForce = { onInForceOpen(true) }, modifier, savedNotice, onSavedNoticeShown, onOpenPrograms)
    }
}

/** The first step: "What's going on?" over the tiles, and the Changes in force in one line above them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tiles(
    program: ActiveProgram?,
    onTile: (Tile) -> Unit,
    onLeave: () -> Unit,
    onInForce: () -> Unit,
    modifier: Modifier,
    savedNotice: Boolean,
    onSavedNoticeShown: () -> Unit,
    onOpenPrograms: () -> Unit,
) {
    BackHandler(onBack = onLeave)
    val snackbars = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val savedText = stringResource(R.string.program_saved)
    val programsText = stringResource(R.string.programs_title)
    LaunchedEffect(savedNotice) {
        if (savedNotice) {
            // In the tiles' own scope, so marking it shown doesn't cancel it.
            snackScope.launch {
                if (snackbars.showSnackbar(savedText, actionLabel = programsText, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) onOpenPrograms()
            }
            onSavedNoticeShown()
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        snackbarHost = { SnackbarHost(snackbars) },
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
                Modifier.padding(horizontal = Margin).padding(top = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.checkin_intro),
                Modifier.padding(horizontal = Margin).padding(top = 4.dp, bottom = GroupGap),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TileGrid(onTile)
        }
    }
}

/**
 * The eight tiles as a 2 × 4 grid, in SPEC's order row by row: a symbol over a name on each, the
 * group's outer corners large and the joins small, as a segmented list's.
 */
@Composable
private fun TileGrid(onTile: (Tile) -> Unit) {
    val tiles = Tile.entries
    val rows = tiles.chunked(2)
    Column(Modifier.fillMaxWidth().padding(horizontal = CardMargin), verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        rows.forEachIndexed { r, pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(SegmentGap)) {
                pair.forEachIndexed { c, tile ->
                    val look = TileLooks.getValue(tile)
                    val shape = RoundedCornerShape(
                        topStart = if (r == 0 && c == 0) TileOuter else TileInner,
                        topEnd = if (r == 0 && c == 1) TileOuter else TileInner,
                        bottomStart = if (r == rows.lastIndex && c == 0) TileOuter else TileInner,
                        bottomEnd = if (r == rows.lastIndex && c == 1) TileOuter else TileInner,
                    )
                    TileCard(stringResource(look.label), look.symbol, shape, Modifier.weight(1f).fillMaxHeight()) { onTile(tile) }
                }
            }
        }
    }
}

private val TileOuter = 32.dp
private val TileInner = 8.dp

@Composable
private fun TileCard(label: String, symbol: Symbol, shape: Shape, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.heightIn(min = 124.dp)
            .clip(shape)
            .background(Tones.card)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
            SymbolIcon(symbol, null, Modifier.size(24.dp), tint = cs.onSecondaryContainer)
        }
        Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    draft: String?,
    onDraft: ((String?) -> Unit)?,
    onAttach: () -> Unit,
    attachNote: String?,
    progress: com.hyperlog.app.coach.CoachProgress?,
) {
    BackHandler(onBack = onBack)
    val step = view.step
    // The bar names what the check-in is about: the tile, How's it going? or Ease back in.
    val title = view.checkIn.tile?.let { stringResource(TileLooks.getValue(it).label) } ?: when (view.checkIn.from) {
        Opening.HowsItGoing -> stringResource(R.string.checkin_title_hows_it_going)
        Opening.EaseBackIn -> stringResource(R.string.checkin_title_ease_back)
        Opening.Tile -> stringResource(R.string.checkin_title)
    }
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
                is CheckInStep.Words -> BottomActions(padding = StripPadding) {
                    TextButton(onClick = onTellMeMore) { Text(stringResource(R.string.checkin_tell_me_more)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onClose) { Text(stringResource(R.string.checkin_done)) }
                }
                // × in the bar drops it; Try again is the one thing to do here.
                is CheckInStep.Problems -> BottomActions(padding = StripPadding) {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onTryAgain) { Text(stringResource(R.string.checkin_try_again)) }
                }
                is CheckInStep.Failed -> BottomActions(padding = StripPadding) {
                    val handOff = step.failure != CheckInFailure.NoConnection && step.failure != CheckInFailure.Dropped
                    val retry = step.failure == CheckInFailure.NoConnection || step.failure == CheckInFailure.Dropped || step.failure == CheckInFailure.Refused || step.failure == CheckInFailure.Rejected
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
                CheckInStep.Thinking, is CheckInStep.ChangeReview, is CheckInStep.WorkoutReview, is CheckInStep.ProgramReview ->
                    Thinking(if (step == CheckInStep.Thinking) doing(view.checkIn, progress) else R.string.checkin_thinking)
                is CheckInStep.Question -> Question(step, lowest, onAnswer, key = view.checkIn.turns.size, draft, onDraft, onAttach, attachNote)
                is CheckInStep.Words -> {
                    // Tell me more adds a card under the words before; the newest comes into view.
                    val scroll = rememberScrollState()
                    LaunchedEffect(step.earlier.size) { scroll.animateScrollTo(scroll.maxValue) }
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = 8.dp, bottom = GroupGap)) {
                        (step.earlier + step.text).forEachIndexed { i, words ->
                            SectionCard(Modifier.padding(top = if (i == 0) 0.dp else 8.dp), padding = CardPadding) {
                                Text(
                                    words, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyLarge,
                                    color = if (i < step.earlier.size) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                is CheckInStep.Problems -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = GroupGap)) {
                    SectionCard(padding = CardPadding) {
                        val cs = MaterialTheme.colorScheme
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            SymbolIcon(Symbol.Warning, null, Modifier.size(24.dp), tint = cs.error)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.checkin_problems_title), style = MaterialTheme.typography.titleMedium, color = cs.error)
                        }
                        Text(
                            stringResource(R.string.checkin_problems_body),
                            Modifier.padding(top = 4.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                        step.errors.forEach { Problem(it) }
                    }
                }
                is CheckInStep.Failed -> Failure(step.failure, pasteErrors, onCopyProblems)
            }
        }
    }
}

/** Why a call has no reply, as a calm centred state; its way forward is the bottom strip. */
@Composable
private fun Failure(failure: CheckInFailure, pasteErrors: List<String>?, onCopyProblems: (List<String>) -> Unit) {
    val (symbol, head, body) = when (failure) {
        CheckInFailure.NoConnection -> Triple(Symbol.Cable, R.string.checkin_no_connection, R.string.checkin_no_connection_body)
        CheckInFailure.Dropped -> Triple(Symbol.Cable, R.string.checkin_dropped, R.string.checkin_dropped_body)
        CheckInFailure.KeyRefused -> Triple(Symbol.Key, R.string.checkin_key_refused, R.string.checkin_ask_claude_body)
        CheckInFailure.SpendLimit -> Triple(Symbol.Toll, R.string.checkin_spend_limit, R.string.checkin_spend_limit_body)
        CheckInFailure.Refused -> Triple(Symbol.Block, R.string.checkin_refused, R.string.checkin_refused_body)
        CheckInFailure.Rejected -> Triple(Symbol.Warning, R.string.checkin_rejected, R.string.checkin_rejected_body)
    }
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 48.dp, bottom = GroupGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapedSymbol(symbol)
        Text(
            stringResource(head),
            Modifier.padding(horizontal = Margin).padding(top = 24.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(body),
            Modifier.padding(horizontal = Margin).padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (failure == CheckInFailure.SpendLimit) {
            Text(
                stringResource(R.string.checkin_ask_claude_body),
                Modifier.padding(horizontal = Margin).padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (pasteErrors != null) {
            Spacer(Modifier.height(24.dp))
            SectionCard(padding = 0.dp) {
                Box(Modifier.padding(bottom = CardPadding)) { PasteProblems(pasteErrors, onCopyProblems, edge = CardPadding) }
            }
        }
    }
}

/** Thinking…: the expressive loading indicator, alone a little above the middle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Thinking(doing: Int) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(Modifier.size(96.dp), color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(doing),
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Optical centre: sit a little above the middle.
        Spacer(Modifier.height(96.dp))
    }
}

/**
 * What the step says while Claude replies, from its stream: what it writes once it writes, and
 * before that the file it reads when the answer was one, else Thinking….
 */
private fun doing(checkIn: com.hyperlog.app.coach.CheckIn, progress: com.hyperlog.app.coach.CoachProgress?): Int {
    when (progress) {
        is com.hyperlog.app.coach.CoachProgress.Writing -> when (progress.tool) {
            com.hyperlog.app.coach.CoachTools.PROPOSE_PROGRAM -> return R.string.checkin_doing_program
            com.hyperlog.app.coach.CoachTools.PROPOSE_CHANGE -> return R.string.checkin_doing_change
            com.hyperlog.app.coach.CoachTools.PROPOSE_WORKOUT -> return R.string.checkin_doing_workout
        }
        com.hyperlog.app.coach.CoachProgress.Answering -> return R.string.checkin_doing_words
        else -> Unit
    }
    // The answer just sent carries a file: Claude is reading it.
    val file = checkIn.turns.lastOrNull()?.answer?.results.orEmpty().firstNotNullOfOrNull { it.attachment } ?: return R.string.checkin_thinking
    return when {
        file.pdf -> R.string.checkin_doing_pdf
        file.mediaType == com.hyperlog.app.coach.Attachment.TEXT -> R.string.checkin_doing_spreadsheet
        else -> R.string.checkin_doing_photo
    }
}

/**
 * Claude's question, large, over its answers to tap, and Type my own under them when Claude allows
 * it. An answer naming a muscle group behind this week carries its sets (`Back · 4 sets`).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Question(
    step: CheckInStep.Question,
    lowest: List<Pair<String, Double>>,
    onAnswer: (String) -> Unit,
    key: Int,
    draft: String?,
    onDraft: ((String?) -> Unit)?,
    onAttach: () -> Unit,
    attachNote: String?,
) {
    var kept by rememberSaveable(key) { mutableStateOf<String?>(null) }
    // Type my own's words, null while it's closed: the caller's draft, or the screen's own.
    val typed = if (onDraft != null) draft else kept
    val setTyped: (String?) -> Unit = onDraft ?: { kept = it }
    val typing = typed != null
    val own = typed.orEmpty()
    val marks = lowest.toMap()
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = GroupGap)) {
        Text(interFigures(step.question), Modifier.padding(horizontal = Margin).padding(bottom = 24.dp), style = MaterialTheme.typography.headlineMedium)
        step.answers.forEachIndexed { i, answer ->
            val group = MuscleGroup.entries.firstOrNull { it.label.equals(answer, ignoreCase = true) }?.label
            val label = group?.let { g -> marks[g]?.let { stringResource(R.string.coach_focus_marked, answer, setsText(it)) } } ?: answer
            Segment(i, step.answers.size, onClick = { onAnswer(answer) }, modifier = Modifier.testTag(AnswerTag)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = CardPadding, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                    // The mark reads quieter than the answer: `Back` and then ` · 4 sets` in onSurfaceVariant.
                    val shown = if (label == answer || !label.startsWith(answer)) AnnotatedString(label) else buildAnnotatedString {
                        append(answer)
                        withStyle(SpanStyle(color = cs.onSurfaceVariant, fontWeight = FontWeight.Normal)) { append(label.removePrefix(answer)) }
                    }
                    Text(shown, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        // A program you have as a file: a PDF, a printed sheet, a screenshot (New program).
        if (step.attach) {
            Spacer(Modifier.height(GroupGap))
            // The note belongs to the row, so it's the row's second line. The symbol says what
            // became of the file: the loading indicator while it's read (no taps then), `warning`
            // in error when it can't go, as a reply's problems show.
            val reading = attachNote == stringResource(R.string.checkin_attach_reading)
            Segment(0, 1, onClick = if (reading) null else onAttach) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = CardPadding, vertical = 12.dp),
                    verticalAlignment = if (attachNote == null) Alignment.CenterVertically else Alignment.Top,
                ) {
                    Box(Modifier.padding(top = if (attachNote == null) 0.dp else 2.dp).size(24.dp), contentAlignment = Alignment.Center) {
                        when {
                            reading -> LoadingIndicator(Modifier.size(32.dp), color = cs.primary)
                            attachNote != null -> SymbolIcon(Symbol.Warning, null, Modifier.size(24.dp), tint = cs.error)
                            else -> SymbolIcon(Symbol.AttachFile, null, Modifier.size(24.dp), tint = cs.primary)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.checkin_attach), style = MaterialTheme.typography.titleMedium)
                        attachNote?.let {
                            Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        // Open after Reject even when Claude didn't offer it: that's where you say what was wrong.
        if (step.own || typing) {
            Spacer(Modifier.height(GroupGap))
            if (!typing) {
                Segment(0, 1, onClick = { setTyped("") }) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = CardPadding), verticalAlignment = Alignment.CenterVertically) {
                        SymbolIcon(Symbol.Keyboard, null, Modifier.size(24.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.checkin_own), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                    }
                }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                val send = { if (own.isNotBlank()) onAnswer(own.trim()) }
                Segment(0, 1) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // The cursor starts after words brought back, so they can be added to.
                        var field by remember(key) { mutableStateOf(TextFieldValue(own, TextRange(own.length))) }
                        TextField(
                            field, { field = it; setTyped(it.text) },
                            Modifier.weight(1f).offset(x = (-4).dp).focusRequester(focus),
                            placeholder = { Text(stringResource(R.string.checkin_own_label)) },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                        )
                        FilledIconButton(
                            onClick = send,
                            enabled = own.isNotBlank(),
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                        ) { SymbolIcon(Symbol.ArrowUpward, stringResource(R.string.checkin_send)) }
                    }
                }
            }
        }
    }
}

/**
 * The check-in a Change keeps: what opened it, then each of Claude's questions over your answer.
 * Your words carry the reply arrow; Claude's questions don't.
 */
@Composable
internal fun CheckInTranscript(made: com.hyperlog.app.coach.SavedCheckIn) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(stringResource(R.string.checkin_transcript), style = MaterialTheme.typography.labelLarge, color = cs.primary)
        Yours(made.opening, Modifier.padding(top = 8.dp))
        made.steps.forEach { step ->
            Text(step.question, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Yours(step.answer, Modifier.padding(top = 4.dp))
        }
    }
}

/** Your words in a transcript, after the reply arrow. */
@Composable
private fun Yours(text: String, modifier: Modifier) {
    Row(modifier) {
        SymbolIcon(Symbol.SubdirectoryArrowRight, null, Modifier.padding(end = 8.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
