package com.hyperlog.app.ui.coach

import com.hyperlog.app.ui.theme.accentButtonColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hyperlog.app.R
import com.hyperlog.app.coach.AskClaude
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.coach.Tile
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.ui.program.Problem
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.GroupGap
import com.hyperlog.app.ui.theme.SectionCard
import com.hyperlog.app.ui.theme.SegmentedRow
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Margin = 24.dp

/** The words a tile puts in Ask Claude's field to edit. */
internal val TileAskWords = mapOf(
    Tile.Hurts to R.string.checkin_ask_hurts,
    Tile.ShortOnTime to R.string.checkin_ask_short,
    Tile.Gym to R.string.checkin_ask_gym,
    Tile.Tired to R.string.checkin_ask_tired,
    Tile.ChangeProgram to R.string.checkin_ask_change,
    Tile.Workout to R.string.checkin_ask_workout,
    Tile.NewProgram to R.string.checkin_ask_new_program,
    Tile.SomethingElse to R.string.checkin_ask_else,
)

/** The filled button of the bottom bar: the next step. */
internal const val NextStepTag = "coach-next-step"

/**
 * Ask Claude, the check-in without a key (SPEC, Coaching → Without a key: Ask Claude; design:
 * docs/design/coach.md): one screen for your words, the tile's sentence in them to edit, then Ask
 * Claude shares the prompt with the Claude app and Paste reply takes its reply back. Nothing here
 * changes the plan; a reply opens its review.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskClaudeScreen(
    /** What the check-in is about: the tile, How's it going? or Ease back in. */
    title: String,
    /** The words for Claude; kept by the caller, so a tab switch keeps them. */
    words: TextFieldValue,
    onWords: (TextFieldValue) -> Unit,
    onBack: () -> Unit,
    /** Shares the prompt for the words with the Claude app. */
    onAskClaude: (words: String) -> Unit,
    modifier: Modifier = Modifier,
    /** A tile starts it: the tile alone is a request, so Ask Claude doesn't wait for words. */
    tileAsked: Boolean = false,
    /** Ask Claude was pressed for these words: pasting the reply is the next step. Kept by the caller, which ends it. */
    asked: Boolean = false,
    onAskedChange: (Boolean) -> Unit = {},
    /** Reads the clipboard as Claude's reply. */
    onPaste: () -> Unit = {},
    /** The problems of the reply last pasted; null when there are none to show. */
    pasteErrors: List<String>? = null,
    /** The words changed: the paste problems no longer apply. */
    onClosePasteErrors: () -> Unit = {},
    /** Copies the paste problems, to give them back to Claude. */
    onCopyProblems: (List<String>) -> Unit = {},
    /** The clipboard held a valid Change or workout on coming back from Claude. */
    replyReady: Boolean = false,
    /** Coming back after asking: read the clipboard once, quietly. */
    onCheckReply: () -> Unit = {},
    /** Opens the reply found for review. */
    onReview: () -> Unit = {},
) {
    /** Hyperlog went to the background after asking: on coming back, the clipboard is read once. */
    var away by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // A reply found on return is the one thing to do: the keyboard makes way for Review.
    LaunchedEffect(replyReady) {
        if (replyReady) {
            keyboard?.hide()
            focus.clearFocus()
        }
    }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!asked) runCatching { field.requestFocus() } }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (asked) away = true }
    // Android only lets an app read the clipboard while its window has focus.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(away, focused) {
        if (away && focused) {
            away = false
            onCheckReply()
        }
    }

    fun edit(value: TextFieldValue) {
        if (value.text != words.text) onClosePasteErrors()
        // Cleared words end the request: Ask Claude is the next step again.
        if (value.text.isBlank() && asked) onAskedChange(false)
        onWords(value)
    }

    BackHandler(onBack = onBack)
    Scaffold(
        // The keyboard shrinks the screen, so the words field scrolls into view above it, with Ask Claude.
        modifier = modifier.imePadding(),
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.checkin_back)) } },
            )
        },
        bottomBar = {
            BottomBar(
                asked = asked,
                replyReady = replyReady,
                canAsk = words.text.isNotBlank() || tileAsked,
                onAsk = {
                    onAskedChange(true)
                    onAskClaude(words.text.trim())
                },
                onPaste = onPaste,
                onReview = onReview,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = GroupGap)) {
            Text(
                stringResource(R.string.checkin_ask_intro),
                Modifier.padding(horizontal = Margin).padding(top = 8.dp, bottom = GroupGap),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionCard(padding = 0.dp) {
                OutlinedTextField(
                    value = words,
                    onValueChange = ::edit,
                    modifier = Modifier.fillMaxWidth().padding(CardPadding).focusRequester(field),
                    label = { Text(stringResource(R.string.coach_words_label)) },
                    placeholder = { Text(stringResource(R.string.coach_words_hint)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            if (pasteErrors != null) PasteProblems(pasteErrors, onCopyProblems)
        }
    }
}

/** A Change in force: its summary and how long it runs, with Revert and Tell Claude how it's going. */
@Composable
internal fun InForce(
    record: ChangeRecord,
    program: ActiveProgram,
    onRevert: () -> Unit,
    onHowItsGoing: () -> Unit,
    howItsGoing: String = stringResource(R.string.coach_how_its_going),
) {
    SegmentedRow(record.change.summary, runsText(record, program), Symbol.Tune)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = CardPadding, bottom = CardPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onRevert) { Text(stringResource(R.string.program_change_revert)) }
        FilledTonalButton(onClick = onHowItsGoing) { Text(howItsGoing) }
    }
}

private val WeekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** How long a Change in force runs: `Until Tue 27 Oct`, `Rest of Build`, `Rest of the program`. */
@Composable
internal fun runsText(record: ChangeRecord, program: ActiveProgram): String = when (val scope = record.change.scope) {
    Scope.RestOfProgram -> stringResource(R.string.change_scope_program)
    Scope.RestOfBlock -> stringResource(
        R.string.change_scope_block,
        program.program.blocks.firstOrNull { it.id == record.accepted.blockId }?.name.orEmpty(),
    )
    is Scope.Dates -> when {
        scope.from > program.today -> stringResource(R.string.coach_dates, WeekdayDayMonth.format(scope.from), WeekdayDayMonth.format(scope.to))
        scope.to == program.today -> stringResource(R.string.coach_until_today)
        else -> stringResource(R.string.coach_until, WeekdayDayMonth.format(scope.to))
    }
}

/**
 * The bottom strip: Paste reply and Ask Claude, the next step filled and always at the end; over
 * them, once Claude's reply is found on the clipboard, the ready card with Review.
 */
@Composable
private fun BottomBar(
    asked: Boolean,
    replyReady: Boolean,
    canAsk: Boolean,
    onAsk: () -> Unit,
    onPaste: () -> Unit,
    onReview: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Tones.bar)
            .navigationBarsPadding(),
    ) {
        if (replyReady) {
            Row(
                Modifier.padding(start = CardPadding, end = CardPadding, top = CardPadding)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.primaryContainer)
                    .padding(start = CardPadding, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SymbolIcon(Symbol.CheckCircle, null, Modifier.size(24.dp), tint = cs.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.coach_reply_ready),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onPrimaryContainer,
                )
                Button(onClick = onReview, modifier = Modifier.testTag(NextStepTag), colors = accentButtonColors()) { Text(stringResource(R.string.coach_review_reply)) }
            }
        }
        Row(
            // A text button's label, not its edge, lines up with the 24 dp margin.
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = Margin - 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ask = @Composable { Text(stringResource(R.string.coach_ask)) }
            val paste = @Composable {
                SymbolIcon(Symbol.ContentPaste, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.coach_paste))
            }
            // With no program running a workout can still come back, so Paste reply is always there.
            when {
                replyReady -> {
                    TextButton(onClick = onPaste) { paste() }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = canAsk, onClick = onAsk) { ask() }
                }
                asked -> {
                    TextButton(enabled = canAsk, onClick = onAsk) { ask() }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onPaste, modifier = Modifier.testTag(NextStepTag)) { paste() }
                }
                else -> {
                    TextButton(onClick = onPaste) { paste() }
                    Spacer(Modifier.weight(1f))
                    Button(enabled = canAsk, onClick = onAsk, modifier = Modifier.testTag(NextStepTag)) { ask() }
                }
            }
        }
    }
}

/** The problems of a reply pasted back that Hyperlog can't use, with a button to copy them for Claude. */
@Composable
internal fun PasteProblems(errors: List<String>, onCopy: (List<String>) -> Unit, edge: androidx.compose.ui.unit.Dp = Margin) {
    val cs = MaterialTheme.colorScheme
    val copyProblem = AskClaude.copyProblem(errors)
    // A failed paste scrolls its problems into view.
    val problems = remember { BringIntoViewRequester() }
    LaunchedEffect(errors) { problems.bringIntoView() }
    Column(Modifier.padding(horizontal = edge).bringIntoViewRequester(problems)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.coach_paste_failed),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = cs.error,
            )
            // A copy cut short or without JSON is fixed by copying again, so there's nothing to hand Claude.
            if (!copyProblem) IconButton(onClick = { onCopy(errors) }) {
                SymbolIcon(Symbol.ContentCopy, stringResource(R.string.coach_copy_problems))
            }
        }
        if (!copyProblem) {
            Text(
                pluralStringResource(R.plurals.coach_paste_problems, errors.size, errors.size),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
        errors.forEach { Problem(it) }
    }
}

/** The text of a muscle's sets this week: `0 sets`, `1 set`, `2.5 sets`. */
@Composable
internal fun setsText(sets: Double): String {
    val n = com.hyperlog.app.domain.formatKg(sets)
    return if (sets == 1.0) stringResource(R.string.coach_focus_set, n) else stringResource(R.string.coach_focus_sets, n)
}
