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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hyperlog.app.R
import com.hyperlog.app.coach.AskClaude
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.coach.WorkoutOptions
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

/** The filled button of the bottom bar: the next step. */
internal const val NextStepTag = "coach-next-step"

/** The gym chip for somewhere not set up in Hyperlog. */
internal const val Elsewhere = -1L

/** Changes in force shown in full; any more fold under "N more in force". */
private const val ShownChanges = 2

/** A starter: the chip's label, and the sentence it puts into the words. */
private data class Starter(val label: Int, val words: Int)

private val Starters = listOf(
    Starter(R.string.coach_starter_pain, R.string.coach_starter_pain_words),
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
    /**
     * Shares the prompt for the words, the gym picked (null: [elsewhere] or no gyms) and Build me a
     * workout's options (null while closed) with the Claude app.
     */
    onAskClaude: (words: String, gym: Gym?, elsewhere: Boolean, options: WorkoutOptions?) -> Unit = { _, _, _, _ -> },
    /**
     * The gym chip picked ([Elsewhere] for somewhere not set up; null: the usual one), and Build me a
     * workout's options (null while closed). Kept by the caller with the words, so a tab switch keeps them.
     */
    picked: Long? = null,
    onPick: (Long?) -> Unit = {},
    options: WorkoutOptions? = null,
    onOptions: (WorkoutOptions?) -> Unit = {},
    /** The gym picked now (null: Elsewhere or no gyms), which a workout added to Today starts at. */
    onGym: (Long?) -> Unit = {},
    /** The focus's marks: the groups trained in the last 4 weeks with the fewest sets this week, with those sets. */
    lowest: List<Pair<String, Double>> = emptyList(),
    /** Ask Claude was pressed for these words: pasting the reply is the next step. Kept by the caller, which ends it. */
    asked: Boolean = false,
    onAskedChange: (Boolean) -> Unit = {},
    /** Opens a Change in force, read-only. */
    onOpenChange: (Long) -> Unit = {},
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
    /** Hyperlog went to the background after asking: on coming back, the clipboard is read once. */
    var away by rememberSaveable { mutableStateOf(false) }
    val elsewhere = picked == Elsewhere && gyms.isNotEmpty()
    val gym = if (elsewhere) null else gyms.firstOrNull { it.id == picked } ?: gyms.firstOrNull { it.id == usualGym?.id } ?: gyms.firstOrNull()
    LaunchedEffect(gym?.id) { onGym(gym?.id) }
    /** The Changes after the first [ShownChanges] are folded under "N more in force" until opened. */
    var allChanges by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // A reply found on return is the one thing to do: the keyboard makes way for Review.
    LaunchedEffect(replyReady) {
        if (replyReady) {
            keyboard?.hide()
            focus.clearFocus()
        }
    }
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
        // Cleared words end the request: Ask Claude is the next step again.
        if (value.text.isBlank() && asked) onAskedChange(false)
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
    // Gym, effort, time and focus are a whole request: words are optional with the options open.
    // Elsewhere's equipment is only in the words, so it needs some.
    val canAsk = (words.text.isNotBlank() || options != null) && !(elsewhere && words.text.isBlank())
    // A new gym or new options make a new request: Ask Claude is the next step again.
    fun pick(id: Long?) { if (asked) onAskedChange(false); onPick(id) }
    fun choose(o: WorkoutOptions?) { if (asked) onAskedChange(false); onOptions(o) }

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
                actions = { if (com.hyperlog.app.ui.theme.PrototypeLook.gearInTopBar) IconButton(onClick = onSettings) { SymbolIcon(Symbol.Settings, stringResource(R.string.settings)) } },
            )
        },
        bottomBar = {
            BottomBar(
                asked = asked,
                replyReady = replyReady,
                canAsk = canAsk,
                onAsk = {
                    onAskedChange(true)
                    onAskClaude(words.text.trim(), gym, elsewhere, options)
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
                val folded = inForce.size > ShownChanges && !allChanges
                val shown = if (folded) inForce.take(ShownChanges) else inForce
                val count = shown.size + if (folded) 1 else 0
                shown.forEachIndexed { i, record ->
                    Segment(i, count, onClick = { onOpenChange(record.id) }, onClickLabel = stringResource(R.string.coach_open_change)) {
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
                if (folded) {
                    Segment(count - 1, count, onClick = { allChanges = true }) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                pluralStringResource(R.plurals.coach_more_in_force, inForce.size - ShownChanges, inForce.size - ShownChanges),
                                Modifier.weight(1f).padding(start = 56.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SymbolIcon(Symbol.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.size(GroupGap))
            }
            // The composer: one card, its chips and field on the 24 dp edge, the starters scrolling to its edges.
            SectionCard(padding = 0.dp) {
                if (gyms.isNotEmpty()) {
                    Row(Modifier.padding(start = CardPadding, end = CardPadding, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OptionHead(stringResource(R.string.coach_gym))
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            gyms.forEach { g ->
                                val selected = g.id == gym?.id
                                FilterChip(
                                    colors = selectedChipColors(),
                                    selected = selected,
                                    onClick = { pick(g.id) },
                                    label = { Text(g.name) },
                                    leadingIcon = if (selected) { { SymbolIcon(Symbol.Check, null, Modifier.size(18.dp)) } } else null,
                                )
                            }
                            // Somewhere not set up: the words say what's there.
                            FilterChip(
                                colors = selectedChipColors(),
                                selected = elsewhere,
                                onClick = { pick(Elsewhere) },
                                label = { Text(stringResource(R.string.coach_elsewhere)) },
                                leadingIcon = if (elsewhere) { { SymbolIcon(Symbol.Check, null, Modifier.size(18.dp)) } } else null,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = CardPadding).padding(top = if (gyms.isEmpty()) 8.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // First, so it shows without scrolling: it opens the options rather than typing a sentence.
                    val building = options != null
                    FilterChip(
                        colors = selectedChipColors(),
                        selected = building,
                        onClick = {
                            choose(if (building) null else WorkoutOptions())
                            if (!building) {
                                // The options are what to fill in: the keyboard makes way for them.
                                keyboard?.hide()
                                focus.clearFocus()
                            }
                        },
                        label = { Text(stringResource(R.string.coach_starter_workout)) },
                        leadingIcon = { SymbolIcon(if (building) Symbol.Check else Symbol.Add, null, Modifier.size(18.dp)) },
                    )
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
                options?.let { picked ->
                    WorkoutOptionsRows(
                        picked, lowest,
                        onChange = { choose(it) },
                    )
                }
                OutlinedTextField(
                    value = words,
                    onValueChange = ::edit,
                    modifier = Modifier.fillMaxWidth().padding(start = CardPadding, end = CardPadding, top = 4.dp, bottom = CardPadding)
                        .focusRequester(field).bringIntoViewRequester(fieldInView),
                    label = { Text(stringResource(R.string.coach_words_label)) },
                    placeholder = { Text(stringResource(R.string.coach_words_hint)) },
                    // At Elsewhere the words are the only equipment Claude gets, so Ask Claude waits for them.
                    supportingText = if (elsewhere && words.text.isBlank()) { { Text(stringResource(R.string.coach_words_hint_elsewhere)) } } else null,
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
                Button(onClick = onReview, modifier = Modifier.testTag(NextStepTag)) { Text(stringResource(R.string.coach_review_reply)) }
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
private fun PasteProblems(errors: List<String>, onCopy: (List<String>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val copyProblem = AskClaude.copyProblem(errors)
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

/** Saves Build me a workout's options: closed, or the effort, the minutes and the focus. */
internal val OptionsSaver = androidx.compose.runtime.saveable.Saver<WorkoutOptions?, List<Any>>(
    save = { o -> if (o == null) emptyList() else listOf(o.effort.name, o.minutes, o.focus.joinToString("\n")) },
    restore = { l ->
        if (l.isEmpty()) null
        else WorkoutOptions(WorkoutOptions.Effort.valueOf(l[0] as String), l[1] as Int, (l[2] as String).split("\n").filter { it.isNotEmpty() })
    },
)

/** The head of a row in the composer: Gym, Effort, Time, Focus, in one column so the choices line up. */
@Composable
private fun OptionHead(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.width(OptionHeadWidth),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val OptionHeadWidth = 56.dp

/** The text of a muscle's sets this week: `0 sets`, `1 set`, `2.5 sets`. */
@Composable
private fun setsText(sets: Double): String {
    val n = com.hyperlog.app.domain.formatKg(sets)
    return if (sets == 1.0) stringResource(R.string.coach_focus_set, n) else stringResource(R.string.coach_focus_sets, n)
}

/**
 * Build me a workout's options: Effort and Time as connected button groups with their defaults
 * picked, and Focus as the seven muscle groups, the three trained in the last 4 weeks with the
 * fewest sets this week marked with them (`Back · 4 sets`). Nothing is preselected: no focus is a
 * balanced full-body day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutOptionsRows(
    options: WorkoutOptions,
    lowest: List<Pair<String, Double>>,
    onChange: (WorkoutOptions) -> Unit,
) {
    val efforts = WorkoutOptions.Effort.entries
    Column(
        Modifier.fillMaxWidth().padding(start = CardPadding, end = CardPadding, top = 12.dp).testTag(WorkoutOptionsTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OptionHead(stringResource(R.string.coach_effort))
            com.hyperlog.app.ui.theme.ConnectedChoice(
                efforts.map {
                    stringResource(
                        when (it) {
                            WorkoutOptions.Effort.Easy -> R.string.coach_effort_easy
                            WorkoutOptions.Effort.Normal -> R.string.coach_effort_normal
                            WorkoutOptions.Effort.Hard -> R.string.coach_effort_hard
                        },
                    )
                },
                selected = efforts.indexOf(options.effort),
                onSelect = { onChange(options.copy(effort = efforts[it])) },
                modifier = Modifier.weight(1f),
                color = Tones.screen,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OptionHead(stringResource(R.string.coach_time))
            com.hyperlog.app.ui.theme.ConnectedChoice(
                WorkoutOptions.Times.map { WorkoutOptions.timeText(it) },
                selected = WorkoutOptions.Times.indexOf(options.minutes),
                onSelect = { onChange(options.copy(minutes = WorkoutOptions.Times[it])) },
                modifier = Modifier.weight(1f),
                color = Tones.screen,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            )
        }
        Row {
            OptionHead(stringResource(R.string.coach_focus), Modifier.padding(top = 14.dp))
            val marks = lowest.toMap()
            // The seven groups in body order; the three behind this week carry their sets.
            val shown = com.hyperlog.app.library.MuscleGroup.entries.map { it.label }
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { muscle ->
                    val selected = muscle in options.focus
                    FilterChip(
                        colors = selectedChipColors(),
                        selected = selected,
                        onClick = { onChange(options.copy(focus = if (selected) options.focus - muscle else options.focus + muscle)) },
                        label = { Text(marks[muscle]?.let { stringResource(R.string.coach_focus_marked, muscle, setsText(it)) } ?: muscle) },
                        leadingIcon = if (selected) { { SymbolIcon(Symbol.Check, null, Modifier.size(18.dp)) } } else null,
                    )
                }
            }
        }
    }
}

/** Build me a workout's options, for tests. */
internal const val WorkoutOptionsTag = "coach-workout-options"
