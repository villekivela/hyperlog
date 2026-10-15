package com.hyperlog.app.ui.coach

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hyperlog.app.R
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.gym.Gym
import com.hyperlog.app.gym.UsualGym
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.program.ChangeRecord
import com.hyperlog.app.ui.program.Problem
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.GroupGap
import com.hyperlog.app.ui.theme.SectionCard
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.SegmentedRow
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.theme.selectedChipColors
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Margin = 24.dp

/** A starter: the chip's label, and the sentence it puts into the words. */
private data class Starter(val label: Int, val words: Int)

private val Starters = listOf(
    Starter(R.string.coach_starter_hands, R.string.coach_starter_hands_words),
    Starter(R.string.coach_starter_short, R.string.coach_starter_short_words),
    Starter(R.string.coach_starter_home, R.string.coach_starter_home_words),
    Starter(R.string.coach_starter_sleep, R.string.coach_starter_sleep_words),
)

/**
 * The coach, words first (SPEC, Coaching → Words first, through Claude; docs/design/coach.md): the
 * Changes in force, then the gym and your words for the Claude app, and its reply pasted back.
 * Nothing here changes the plan; a reply opens its review.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(
    /** The program running, null with none: then Claude answers in words and nothing comes back. */
    program: ActiveProgram?,
    /** The words for Claude; kept by the caller, so Today's Ease back in can fill them in. */
    words: TextFieldValue,
    onWords: (TextFieldValue) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    gyms: List<Gym> = emptyList(),
    /** The gym the start sheet would pick, preselected. */
    usualGym: UsualGym? = null,
    /** Shares the prompt for the words and the gym picked with the Claude app. */
    onAskClaude: (words: String, gym: Gym?) -> Unit = { _, _ -> },
    /** Reads the clipboard as Claude's reply. */
    onPaste: () -> Unit = {},
    /** The problems of the reply last pasted; null when there are none to show. */
    pasteErrors: List<String>? = null,
    /** The words changed: the paste problems no longer apply. */
    onClosePasteErrors: () -> Unit = {},
    /** Copies the paste problems, to give them back to Claude. */
    onCopyProblems: (List<String>) -> Unit = {},
    /** The clipboard held a valid Change on coming back from Claude. */
    replyReady: Boolean = false,
    /** Coming back after asking: read the clipboard once, quietly. */
    onCheckReply: () -> Unit = {},
    /** Opens the reply found for review. */
    onReview: () -> Unit = {},
    /** Reverts the accepted Change with this id. */
    onRevert: (Long) -> Unit = {},
    onSettings: () -> Unit = {},
) {
    /** Ask Claude was pressed: pasting the reply is the next step. */
    var asked by rememberSaveable { mutableStateOf(false) }
    /** Hyperlog went to the background after asking: on coming back, the clipboard is read once. */
    var away by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf<Long?>(null) }
    val gym = gyms.firstOrNull { it.id == picked } ?: gyms.firstOrNull { it.id == usualGym?.id } ?: gyms.firstOrNull()
    var reverting by rememberSaveable { mutableStateOf<Long?>(null) }
    val field = remember { FocusRequester() }
    val fieldInView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

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
        onWords(value)
    }
    fun focusWords() {
        scope.launch {
            runCatching { field.requestFocus() }
            fieldInView.bringIntoView()
        }
    }
    val inForce = program?.inForce.orEmpty()
    val about = inForce.associate { it.id to stringResource(R.string.coach_about, it.change.summary) }
    val starters = Starters.map { stringResource(it.label) to stringResource(it.words) }
    val canAsk = words.text.isNotBlank()

    BackHandler(onBack = onBack)
    Scaffold(
        // The keyboard shrinks the screen, so the words field scrolls into view above it, with Ask Claude.
        modifier = modifier.imePadding(),
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                // The bar sets titles 16 dp in; the screen's text edge is 24 dp.
                title = { Text(stringResource(R.string.coach_title), Modifier.padding(start = 8.dp)) },
                // A destination of the navigation bar: no back arrow (back goes to Today), Settings at the end.
                actions = { IconButton(onClick = onSettings) { SymbolIcon(Symbol.Settings, stringResource(R.string.settings)) } },
            )
        },
        bottomBar = {
            BottomBar(
                withProgram = program != null,
                asked = asked,
                replyReady = replyReady,
                canAsk = canAsk,
                onAsk = {
                    asked = true
                    onAskClaude(words.text.trim(), gym)
                },
                onPaste = onPaste,
                onReview = onReview,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = GroupGap)) {
            Text(
                stringResource(if (program != null) R.string.coach_intro else R.string.coach_intro_no_program),
                Modifier.padding(horizontal = Margin).padding(top = 8.dp, bottom = GroupGap),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (inForce.isNotEmpty()) {
                inForce.forEachIndexed { i, record ->
                    Segment(i, inForce.size) {
                        InForce(
                            record, program!!,
                            onRevert = { reverting = record.id },
                            onHowItsGoing = {
                                val prefix = about.getValue(record.id)
                                val rest = words.text.removePrefix(prefix)
                                edit(TextFieldValue(prefix + rest, TextRange(prefix.length + rest.length)))
                                focusWords()
                            },
                        )
                    }
                }
                Spacer(Modifier.size(GroupGap))
            }
            SectionCard {
                if (gyms.isNotEmpty()) {
                    Text(
                        stringResource(R.string.coach_gym),
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        gyms.forEach { g ->
                            val selected = g.id == gym?.id
                            FilterChip(
                                colors = selectedChipColors(),
                                selected = selected,
                                onClick = { picked = g.id },
                                label = { Text(g.name) },
                                leadingIcon = if (selected) { { SymbolIcon(Symbol.Check, null, Modifier.size(18.dp)) } } else null,
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    starters.forEach { (label, sentence) ->
                        SuggestionChip(
                            onClick = {
                                // A starter fills empty words; otherwise it goes on a new line.
                                val text = if (words.text.isBlank()) sentence else words.text.trimEnd() + "\n" + sentence
                                edit(TextFieldValue(text, TextRange(text.length)))
                                focusWords()
                            },
                            label = { Text(label) },
                            icon = { SymbolIcon(Symbol.Add, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = words,
                    onValueChange = ::edit,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(field).bringIntoViewRequester(fieldInView),
                    label = { Text(stringResource(R.string.coach_words_label)) },
                    placeholder = { Text(stringResource(R.string.coach_words_hint)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            if (pasteErrors != null) PasteProblems(pasteErrors, onCopyProblems)
        }
    }
    inForce.firstOrNull { it.id == reverting }?.let { record ->
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

/** A Change in force: its summary and how long it runs, with Revert and Tell Claude how it's going. */
@Composable
private fun InForce(record: ChangeRecord, program: ActiveProgram, onRevert: () -> Unit, onHowItsGoing: () -> Unit) {
    SegmentedRow(record.change.summary, runsText(record, program), Symbol.Tune)
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = CardPadding, bottom = CardPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onRevert) { Text(stringResource(R.string.program_change_revert)) }
        FilledTonalButton(onClick = onHowItsGoing) { Text(stringResource(R.string.coach_how_its_going)) }
    }
}

private val WeekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** How long a Change in force runs: `Until Tue 27 Oct`, `Rest of Build`, `Rest of the program`. */
@Composable
private fun runsText(record: ChangeRecord, program: ActiveProgram): String = when (val scope = record.change.scope) {
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
    withProgram: Boolean,
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
        if (replyReady && withProgram) {
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
                Button(onClick = onReview) { Text(stringResource(R.string.coach_review_reply)) }
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
            when {
                // With no program only words come back, so there is nothing to paste.
                !withProgram -> {
                    Spacer(Modifier.weight(1f))
                    Button(enabled = canAsk, onClick = onAsk) { ask() }
                }
                replyReady -> {
                    TextButton(onClick = onPaste) { paste() }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = canAsk, onClick = onAsk) { ask() }
                }
                asked -> {
                    TextButton(enabled = canAsk, onClick = onAsk) { ask() }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onPaste) { paste() }
                }
                else -> {
                    TextButton(onClick = onPaste) { paste() }
                    Spacer(Modifier.weight(1f))
                    Button(enabled = canAsk, onClick = onAsk) { ask() }
                }
            }
        }
    }
}

/** The problems of a reply pasted back that Hyperlog can't use, with a button to copy them for Claude. */
@Composable
private fun PasteProblems(errors: List<String>, onCopy: (List<String>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    // A failed paste scrolls its problems into view.
    val problems = remember { BringIntoViewRequester() }
    LaunchedEffect(errors) { problems.bringIntoView() }
    Column(Modifier.padding(horizontal = Margin).bringIntoViewRequester(problems)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.coach_paste_failed),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = cs.error,
            )
            IconButton(onClick = { onCopy(errors) }) {
                SymbolIcon(Symbol.ContentCopy, stringResource(R.string.coach_copy_problems))
            }
        }
        Text(
            pluralStringResource(R.plurals.coach_paste_problems, errors.size, errors.size),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        errors.forEach { Problem(it) }
    }
}
