package com.hyperlog.app.ui.session

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.Field
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.domain.SetEntry
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.domain.summary
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.gym.EquipmentItem
import com.hyperlog.app.ui.gym.LadderEditor
import com.hyperlog.app.ui.gym.LadderKind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hyperlog.app.library.TechniqueVideo
import com.hyperlog.app.library.search
import com.hyperlog.app.ui.library.SearchField
import com.hyperlog.app.ui.library.openTechniqueVideo
import com.hyperlog.app.session.SessionExercise
import com.hyperlog.app.program.PlannedSet
import com.hyperlog.app.program.plannedWeight
import com.hyperlog.app.progression.Suggestion
import com.hyperlog.app.ui.program.SetSummary
import com.hyperlog.app.ui.program.symbol
import com.hyperlog.app.ui.program.techniqueText
import androidx.compose.material3.Button
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import kotlinx.coroutines.delay

/**
 * The logging screen (docs/design/logging-screen.md): one exercise at a time as set rows, the
 * Next line, and the pad. An ad-hoc session has no targets, so the Target column is left out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    session: ActiveSession,
    exercises: List<Exercise>,
    actions: SessionActions,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLeave by rememberSaveable { mutableStateOf(false) }
    var showList by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showRest by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showFinish by rememberSaveable { mutableStateOf(false) }
    val offered = exercises.filter { !it.hidden }
    val now = rememberNow()
    KeepScreenOn()
    RestEndBuzz(session.restEndsAt)

    BackHandler { showLeave = true }

    val current = session.current
    var addingMachine by rememberSaveable { mutableStateOf(false) }
    if (addingMachine && current != null && current.needsMachine) {
        // Machines get a name for what they are; a cable station serves many exercises.
        val equipment = current.exercise.equipment
        LadderEditor(
            title = stringResource(R.string.machine_new),
            name = if (equipment == Equipment.Machine) current.exercise.name else if (equipment == Equipment.SmithMachine) equipment.label else "",
            spec = null,
            kinds = LadderKind.entries,
            onSave = { name, spec -> actions.addMachine(name, spec); addingMachine = false },
            onDelete = null,
            onClose = { addingMachine = false },
        )
        return
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).windowInsetsPadding(WindowInsets.statusBars)) {
                if (current == null) {
                    ExercisePicker(
                        offered,
                        title = stringResource(R.string.session_add_exercise),
                        onPick = actions::addExercise,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Header(
                        name = current.exercise.name,
                        session = session,
                        now = now,
                        onName = { showDetails = true },
                        onTimer = { showRest = true },
                        onAddExercise = { showPicker = true },
                        onFinish = { showFinish = true },
                    )
                    if (current.needsMachine) {
                        MachinePicker(
                            session.gym?.name.orEmpty(),
                            session.gym?.items.orEmpty(),
                            onPick = actions::chooseMachine,
                            onAdd = { addingMachine = true },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        current.suggestion?.takeIf { it.why != null && !current.done }?.let { s ->
                            SuggestionLine(s, from = current.sets.lastOrNull()?.weightKg)
                        }
                        current.startingHint?.let { StartingHint(it) }
                        SetTable(current, session.entry, session.weightCheck != null, actions, Modifier.weight(1f))
                    }
                    val index = session.exercises.indexOf(current)
                    val next = session.exercises.drop(index + 1).firstOrNull { !it.done }
                    NextLine(
                        label = if (next != null) stringResource(R.string.session_next) else null,
                        name = next?.exercise?.name ?: stringResource(R.string.session_last_exercise),
                        onClick = { showList = true },
                    )
                }
            }
            val entry = session.entry
            if (current != null && entry != null) {
                // A weight check always shows, even with the pad folded away.
                if (session.padVisible || session.weightCheck != null) Pad(entry, current.ladder, session.weightCheck, actions) else HiddenPad(entry, actions)
            } else if (current != null && current.done && session.exercises.all { it.plan == null || it.done }) {
                DayDoneBar(onFinish = { showFinish = true })
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (showLeave) {
        AlertDialog(
            onDismissRequest = { showLeave = false },
            title = { Text(stringResource(R.string.leave_title)) },
            text = { Text(stringResource(R.string.leave_body)) },
            confirmButton = { TextButton(onClick = { showLeave = false }) { Text(stringResource(R.string.leave_stay)) } },
            dismissButton = {
                TextButton(onClick = { showLeave = false; onLeave() }) { Text(stringResource(R.string.leave_leave)) }
            },
        )
    }
    if (showFinish) {
        // Finishing is one menu row away from Add exercise, so it asks first, like leaving does.
        AlertDialog(
            onDismissRequest = { showFinish = false },
            title = { Text(stringResource(R.string.finish_title)) },
            text = { Text(stringResource(R.string.finish_body)) },
            confirmButton = { TextButton(onClick = { showFinish = false }) { Text(stringResource(R.string.finish_keep_going)) } },
            dismissButton = {
                TextButton(onClick = { showFinish = false; actions.finish() }) { Text(stringResource(R.string.finish_finish)) }
            },
        )
    }
    if (showList) {
        Sheet(onDismiss = { showList = false }) {
            SessionList(
                session,
                onStart = { actions.switchTo(it); showList = false },
                onAdd = { showList = false; showPicker = true },
                onFinish = { showList = false; showFinish = true },
            )
        }
    }
    if (showPicker) {
        Sheet(onDismiss = { showPicker = false }) {
            ExercisePicker(
                offered,
                title = stringResource(R.string.session_add_exercise),
                onPick = { actions.addExercise(it); showPicker = false },
            )
        }
    }
    if (showDetails && current != null) {
        Sheet(onDismiss = { showDetails = false }) {
            ExerciseSheet(
                current.exercise,
                plan = current.plan,
                slotNote = current.slot?.note,
                machine = current.machine?.let { m -> stringResource(R.string.machine_at, m.name, session.gym?.name.orEmpty()) },
                // The machine can change until a set is logged on it; after that the sets belong to it.
                onChangeMachine = if (current.machine != null && current.sets.isEmpty()) {
                    { showDetails = false; actions.changeMachine() }
                } else null,
            )
        }
    }
    if (showRest && session.restEndsAt != null) {
        Sheet(onDismiss = { showRest = false }) {
            RestSheet(session.restEndsAt - now, actions, onEnd = { showRest = false })
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun Header(
    name: String,
    session: ActiveSession,
    now: Long,
    onName: () -> Unit,
    onTimer: () -> Unit,
    onAddExercise: () -> Unit,
    onFinish: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = Margin, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val details = stringResource(R.string.session_exercise_details)
        Text(
            name,
            Modifier.weight(1f).clickable(onClickLabel = details, onClick = onName),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        session.restEndsAt?.let {
            // A long name ellipsizes clear of the pill.
            Spacer(Modifier.width(8.dp))
            RestPill(it - now, session.restTotalMs, onTimer)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                SymbolIcon(Symbol.MoreVert, stringResource(R.string.session_menu), tint = cs.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.session_add_exercise)) },
                    leadingIcon = { SymbolIcon(Symbol.Add, null) },
                    onClick = { menu = false; onAddExercise() },
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.session_finish)) },
                    leadingIcon = { SymbolIcon(Symbol.CheckCircle, null) },
                    onClick = { menu = false; onFinish() },
                )
            }
        }
    }
}

/**
 * The one clock. During rest it drains left to right; after rest it counts up in a muted
 * container until the next set is logged.
 */
@Composable
private fun RestPill(leftMs: Long, totalMs: Long, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val over = leftMs <= 0
    val fraction = if (over || totalMs <= 0) 0f else (leftMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val fill = cs.primary.copy(alpha = 0.24f)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (over) cs.surfaceContainerHigh else cs.secondaryContainer,
        contentColor = if (over) cs.onSurfaceVariant else cs.onSecondaryContainer,
    ) {
        Row(
            Modifier
                .drawBehind { drawRect(fill, size = Size(size.width * fraction, size.height)) }
                .height(40.dp)
                .padding(start = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolIcon(Symbol.Timer, stringResource(R.string.rest_title), Modifier.width(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(clock(leftMs), style = MaterialTheme.typography.titleMedium.merge(Tnum))
        }
    }
}

/** `1:42` while counting down, `+0:12` after. */
private fun clock(leftMs: Long): String {
    val over = leftMs <= 0
    val ms = if (over) -leftMs else leftMs
    val secs = if (over) ms / 1000 else (ms + 999) / 1000
    return (if (over) "+" else "") + "%d:%02d".format(secs / 60, secs % 60)
}

@Composable
private fun RestSheet(leftMs: Long, actions: SessionActions, onEnd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Margin).padding(bottom = 24.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.rest_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { actions.adjustRest(-15_000) }, Modifier.width(96.dp)) { Text("−15") }
            Text(
                clock(leftMs),
                Modifier.width(160.dp),
                style = MaterialTheme.typography.displayMedium.merge(Tnum),
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = { actions.adjustRest(15_000) }, Modifier.width(96.dp)) { Text("+15") }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { actions.endRest(); onEnd() }) { Text(stringResource(R.string.rest_end)) }
    }
}

// ---------------------------------------------------------------- suggestion

internal const val SuggestionTag = "suggestion"

/**
 * Why the active set's weight is what it is (SPEC, Every suggestion says why): one muted line
 * under the name, led by the direction. An estimated weight is tertiary, to be checked on the set.
 */
@Composable
private fun SuggestionLine(suggestion: Suggestion, from: Double?) {
    val cs = MaterialTheme.colorScheme
    val muted = cs.onSurfaceVariant
    val up = from == null || suggestion.kg > from
    val (symbol, tint) = when (suggestion.kind) {
        Suggestion.Kind.Up -> Symbol.TrendingUp to cs.primary
        Suggestion.Kind.Down -> Symbol.TrendingDown to muted
        Suggestion.Kind.Adjust -> if (up) Symbol.TrendingUp to cs.primary else Symbol.TrendingDown to muted
        Suggestion.Kind.Estimated -> Symbol.Calculate to cs.tertiary
        Suggestion.Kind.Stuck -> Symbol.Block to cs.tertiary
        // From another app's log, so checked on the first set like an estimate.
        Suggestion.Kind.Starting -> Symbol.History to cs.tertiary
        else -> Symbol.TrendingFlat to muted
    }
    val checkIt = suggestion.estimated || suggestion.kind == Suggestion.Kind.Stuck || suggestion.kind == Suggestion.Kind.Starting
    WhyLine(symbol, tint, suggestion.why.orEmpty(), if (checkIt) cs.tertiary else muted, SuggestionTag)
}

internal const val StartingHintTag = "starting-hint"

/**
 * A machine's weight from the old app (SPEC, Fresh start): muted and not prefilled, since the old
 * log doesn't say which machine it was. It sits where a suggestion would, above the sets.
 */
@Composable
private fun StartingHint(set: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().testTag(StartingHintTag).padding(start = Margin, end = Margin, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { SymbolIcon(Symbol.History, null, Modifier.size(18.dp), tint = muted) }
        Spacer(Modifier.width(8.dp))
        // The set on a line of its own, so it never breaks inside "@ 2 RIR".
        Column {
            Text(stringResource(R.string.starting_hint), style = MaterialTheme.typography.bodyMedium, color = muted)
            Text(set, style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One line under the name, led by a symbol in the 20 dp column the set numbers use. */
@Composable
private fun WhyLine(symbol: Symbol, tint: Color, text: String, color: Color, tag: String) {
    Row(
        Modifier.fillMaxWidth().testTag(tag).padding(start = Margin, end = Margin, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { SymbolIcon(symbol, null, Modifier.size(18.dp), tint = tint) }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- table

@Composable
private fun SetTable(current: SessionExercise, entry: SetEntry?, weightFlagged: Boolean, actions: SessionActions, modifier: Modifier) {
    val listState = rememberLazyListState()
    // Keep the active row in view as sets pile up.
    LaunchedEffect(current.id, current.sets.size) {
        if (current.sets.isNotEmpty()) listState.animateScrollToItem(current.sets.size)
    }
    val plan = current.plan
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = RowInset), state = listState) {
        item { ColumnHeads(showLast = current.lastTime.isNotEmpty(), showTarget = plan != null) }
        if (plan != null) {
            plannedRows(current, plan, entry, weightFlagged, actions)
            return@LazyColumn
        }
        items(current.sets.size) { i ->
            DoneRow(i + 1, current.lastTime.getOrNull(i), current.sets[i])
        }
        if (entry != null) {
            item {
                ActiveRow(current.sets.size + 1, current.lastTime.getOrNull(current.sets.size), entry, weightFlagged, actions)
            }
            // An ad-hoc session has no plan, so last time's set count shows what is likely to come,
            // muted and prefilled the way the next set will be.
            val upcoming = (current.sets.size + 1 until current.lastTime.size).toList()
            items(upcoming.size) { k ->
                val i = upcoming[k]
                UpcomingRow(i + 1, current.lastTime[i], entry.weight, entry.full)
            }
        }
    }
}

/**
 * A program exercise: a row for every planned set from the start, done, active or to come. A run
 * of back-offs is headed by its rule, and a technique is named under its set.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.plannedRows(
    current: SessionExercise,
    plan: List<PlannedSet>,
    entry: SetEntry?,
    weightFlagged: Boolean,
    actions: SessionActions,
) {
    val logged = current.sets
    val rows = maxOf(plan.size, logged.size)
    for (i in 0 until rows) {
        val target = plan.getOrNull(i)
        val backoff = target?.backoff
        if (backoff != null && plan.getOrNull(i - 1)?.backoff == null) {
            item(key = "backoff-$i") { Label(Symbol.SouthEast, stringResource(R.string.technique_backoff, backoff.percent), done = i < logged.size) }
        }
        val last = current.lastTime.getOrNull(i)
        item(key = "set-$i") {
            when {
                i < logged.size -> DoneRow(i + 1, last, logged[i], target)
                i == logged.size && entry != null -> ActiveRow(i + 1, last, entry, weightFlagged, actions, target)
                else -> {
                    val kg = plannedWeight(i, plan, logged, current.lastTime, current.ladder, entry?.weightKg)
                    val activeRange = plan.getOrNull(logged.size)?.reps
                    val reps = if (entry != null && target?.reps == activeRange) entry.full else target?.reps?.max?.toString().orEmpty()
                    UpcomingRow(i + 1, last, kg?.let(::formatKg).orEmpty(), reps, target)
                }
            }
        }
        target?.technique?.let { t ->
            item(key = "technique-$i") { Label(t.symbol(), techniqueText(t), done = i < logged.size) }
        }
    }
}

/** A technique or back-off rule: one 32 dp line aligned with the Target column, muted once done. */
@Composable
private fun Label(symbol: Symbol, text: String, done: Boolean) {
    val color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
    Row(
        Modifier.fillMaxWidth().height(LabelHeight).padding(start = RowPad + ColSet, end = RowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(symbol, null, Modifier.size(18.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The set's target: reps and the target RIR as a ring. */
@Composable
private fun Target(target: PlannedSet?) {
    Row(Modifier.width(ColTarget), verticalAlignment = Alignment.CenterVertically) {
        if (target != null) {
            Text(
                target.reps.toString(),
                style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            target.rir?.let {
                Spacer(Modifier.width(8.dp))
                RirMark(it, filled = false)
            }
        }
    }
}

@Composable
private fun UpcomingRow(n: Int, last: LoggedSet?, weight: String, reps: String, target: PlannedSet? = null) {
    val value = MaterialTheme.typography.titleLarge.merge(Tnum)
    val color = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = RowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetNumber(n, active = false)
        if (target != null) Target(target)
        Last(last)
        Text(weight, Modifier.width(ColKg), color = color, textAlign = TextAlign.Center, style = value)
        Text(reps, Modifier.width(ColReps), color = color, textAlign = TextAlign.Center, style = value, maxLines = 1)
        Spacer(Modifier.width(ColRir))
    }
}

/** The column heads. "Last" only shows once there is a last time to compare with. */
@Composable
private fun ColumnHeads(showLast: Boolean, showTarget: Boolean = false) {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    val st = MaterialTheme.typography.labelMedium
    Row(Modifier.fillMaxWidth().height(LabelHeight).padding(horizontal = RowPad), verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.width(ColSet))
        if (showTarget) Text(stringResource(R.string.col_target), Modifier.width(ColTarget), style = st, color = c)
        Text(if (showLast) stringResource(R.string.col_last) else "", Modifier.weight(1f), style = st, color = c)
        Text(stringResource(R.string.col_kg), Modifier.width(ColKg), style = st, color = c, textAlign = TextAlign.Center)
        Text(stringResource(R.string.col_reps), Modifier.width(ColReps), style = st, color = c, textAlign = TextAlign.Center)
        Text(stringResource(R.string.col_rir), Modifier.width(ColRir), style = st, color = c, textAlign = TextAlign.Center)
    }
}

/** Every planned set is logged: the pad gives way to Finish session. */
@Composable
private fun DayDoneBar(onFinish: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding()
            .padding(start = Margin, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(Symbol.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.session_day_done), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onFinish) { Text(stringResource(R.string.session_finish)) }
    }
}

/** `25 × 10`, `25 × 10 + 6`: last time's set, without its RIR. */
private fun LoggedSet.short() = "${formatKg(weightKg)} × $fullReps" + if (partialReps > 0) " + $partialReps" else ""

private fun LoggedSet.repsText() = if (partialReps > 0) "$fullReps + $partialReps" else "$fullReps"

@Composable
private fun RowScope.SetNumber(n: Int, active: Boolean) {
    val cs = MaterialTheme.colorScheme
    Text(
        "$n",
        Modifier.width(ColSet),
        style = MaterialTheme.typography.titleMedium.merge(Tnum),
        color = if (active) cs.primary else cs.onSurfaceVariant,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
private fun RowScope.Last(last: LoggedSet?) {
    Text(
        last?.short() ?: "",
        Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium.merge(Tnum),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DoneRow(n: Int, last: LoggedSet?, set: LoggedSet, target: PlannedSet? = null) {
    val value = MaterialTheme.typography.titleLarge.merge(Tnum)
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = RowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetNumber(n, active = false)
        if (target != null) Target(target)
        Last(last)
        Text(formatKg(set.weightKg), Modifier.width(ColKg), color = color, textAlign = TextAlign.Center, style = value)
        val reps = set.repsText()
        val repsStyle = if (reps.length > 6) MaterialTheme.typography.titleMedium.merge(Tnum) else value
        Text(reps, Modifier.width(ColReps), color = color, textAlign = TextAlign.Center, style = repsStyle, maxLines = 1)
        Box(Modifier.width(ColRir), contentAlignment = Alignment.Center) { RirMark(set.rir, filled = true) }
    }
}

@Composable
private fun ActiveRow(n: Int, last: LoggedSet?, entry: SetEntry, weightFlagged: Boolean, actions: SessionActions, target: PlannedSet? = null) {
    fun tap(field: Field) {
        actions.focus(field)
        actions.setPadVisible(true)
    }
    Row(
        Modifier.fillMaxWidth().height(RowHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RowShape)
            .padding(horizontal = RowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetNumber(n, active = true)
        if (target != null) Target(target)
        Last(last)
        Cell(
            AnnotatedString(entry.weight),
            focused = entry.field == Field.Weight,
            description = stringResource(R.string.col_kg),
            flagged = weightFlagged,
            modifier = Modifier.width(ColKg),
        ) { tap(Field.Weight) }
        Cell(
            repsAnnotated(entry),
            focused = entry.field == Field.Reps,
            description = stringResource(R.string.col_reps),
            modifier = Modifier.width(ColReps),
        ) { tap(Field.Reps) }
        Box(Modifier.width(ColRir), contentAlignment = Alignment.Center) {
            entry.rir?.let { RirMark(it, filled = true) }
        }
    }
}

/** While partials are being typed, the full reps step back so the number being typed stands out. */
@Composable
private fun repsAnnotated(entry: SetEntry): AnnotatedString {
    val text = entry.repsText
    val split = text.indexOf(" +")
    if (split < 0 || !entry.partialMode || entry.field != Field.Reps) return AnnotatedString(text)
    val muted = MaterialTheme.colorScheme.outline
    val end = (split + 3).coerceAtMost(text.length)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal)) { append(text.substring(0, end)) }
        append(text.substring(end))
    }
}

/** A value in the active row. The focused one is filled and outlined; tap any to type into it. */
@Composable
private fun Cell(
    text: AnnotatedString,
    focused: Boolean,
    description: String,
    modifier: Modifier,
    /** The weight check is on this value: it reads in the error colour, like the check's reason. */
    flagged: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .height(CellHeight)
            .clip(CellShape)
            .then(if (focused) Modifier.background(cs.surfaceContainerHighest).border(2.dp, cs.primary, CellShape) else Modifier)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            // Long values (`100 + 20`) step down a size so they stay inside the cell.
            style = (if (text.length > 6) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).merge(Tnum),
            color = if (flagged) cs.error else cs.onSurface,
            fontWeight = if (focused || flagged) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- next line and sheets

@Composable
private fun NextLine(label: String?, name: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).height(56.dp).padding(start = Margin, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        label?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        SymbolIcon(Symbol.ExpandLess, stringResource(R.string.session_list), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { content() }
}

/** The session's exercises with what has been logged, plus adding one and finishing. */
@Composable
private fun SessionList(session: ActiveSession, onStart: (Long) -> Unit, onAdd: () -> Unit, onFinish: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(bottom = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.session_title), Modifier.padding(horizontal = Margin), style = MaterialTheme.typography.titleLarge)
        session.gym?.let {
            Text(it.name, Modifier.padding(horizontal = Margin), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        session.exercises.forEach { e ->
            val now = e.id == session.currentId
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = Margin, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(40.dp)) {
                    when {
                        now -> SymbolIcon(Symbol.RadioButtonChecked, null, tint = cs.primary)
                        e.sets.isNotEmpty() -> SymbolIcon(Symbol.CheckCircle, null, tint = cs.onSurfaceVariant)
                        else -> SymbolIcon(Symbol.RadioButtonUnchecked, null, tint = cs.outline)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(e.exercise.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.sets.isNotEmpty()) {
                        Text(
                            e.sets.joinToString("   ") { it.summary().replace(' ', ' ') },
                            style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        e.plan?.let { SetSummary(it) }
                    }
                }
                if (!now && !e.done) FilledTonalButton(onClick = { onStart(e.id) }) { Text(stringResource(R.string.session_start_exercise)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = Margin - 12.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onAdd) {
                SymbolIcon(Symbol.Add, null, Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.session_add_exercise))
            }
            TextButton(onClick = onFinish) { Text(stringResource(R.string.session_finish)) }
        }
    }
}

/**
 * "Which machine?": the gym's equipment with the weights each gives, and Add machine. It stands
 * in for the set table until answered, so no set can start on an unknown machine.
 */
@Composable
private fun MachinePicker(
    gym: String,
    items: List<EquipmentItem>,
    onPick: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.machine_title),
            Modifier.padding(start = Margin, end = Margin, top = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.machine_body, gym),
            Modifier.padding(start = Margin, end = Margin, top = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        items.forEach { item ->
            Column(
                Modifier.fillMaxWidth().clickable { onPick(item.id) }.heightIn(min = 72.dp)
                    .padding(horizontal = Margin, vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.ladder.ladder().preview(),
                    style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onAdd, Modifier.padding(start = Margin - 12.dp, top = 8.dp)) {
            SymbolIcon(Symbol.Add, null, Modifier.width(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.machine_add))
        }
    }
}

/** The library's exercises, hidden ones left out, with a search field on top. */
@Composable
private fun ExercisePicker(exercises: List<Exercise>, title: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            Modifier.padding(start = Margin, end = Margin, top = 16.dp, bottom = 16.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        SearchField(
            query,
            onChange = { query = it },
            placeholder = stringResource(R.string.session_search),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
        val listState = rememberLazyListState()
        LaunchedEffect(query) { listState.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).imePadding(), state = listState) {
            items(exercises.search(query), key = { it.id }) { e ->
                Text(
                    e.name,
                    Modifier.fillMaxWidth().clickable { onPick(e.id) }.heightIn(min = 56.dp).padding(horizontal = Margin, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
        }
    }
}

/**
 * The exercise's prescription sheet. An ad-hoc session has no prescription, so it shows what
 * the library knows: equipment and muscles, the technique note and the technique video.
 */
@Composable
internal fun ExerciseSheet(
    exercise: Exercise,
    machine: String?,
    onChangeMachine: (() -> Unit)?,
    plan: List<PlannedSet>? = null,
    slotNote: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Margin).padding(bottom = 24.dp).navigationBarsPadding()) {
        Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(exercise.summary, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        machine?.let {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(Symbol.LocationOn, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                onChangeMachine?.let { change ->
                    val label = stringResource(R.string.machine_change_description)
                    TextButton(onClick = change, Modifier.semantics { contentDescription = label }) {
                        Text(stringResource(R.string.machine_change))
                    }
                }
            }
        }
        plan?.let { Prescription(it) }
        slotNote?.let { note ->
            Spacer(Modifier.height(24.dp))
            val label = stringResource(R.string.sheet_program_note)
            Row(Modifier.semantics { contentDescription = label }) {
                SymbolIcon(Symbol.ViewTimeline, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(note, style = MaterialTheme.typography.bodyLarge)
            }
        }
        exercise.note?.let { note ->
            Spacer(Modifier.height(24.dp))
            Row {
                SymbolIcon(Symbol.StickyNote2, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(note, style = MaterialTheme.typography.bodyLarge)
            }
        }
        exercise.video?.let { link ->
            Spacer(Modifier.height(24.dp))
            val at = TechniqueVideo.parse(link)?.startLabel
            FilledTonalButton(onClick = { context.openTechniqueVideo(link) }) {
                SymbolIcon(Symbol.SmartDisplay, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (at != null) stringResource(R.string.sheet_video_at, at) else stringResource(R.string.sheet_video))
            }
        }
    }
}

/**
 * Every planned set: number, target reps and RIR ring, and its technique or back-off rule. Rest
 * is one line when every set shares it, else at the end of each row.
 */
@Composable
private fun Prescription(plan: List<PlannedSet>) {
    val cs = MaterialTheme.colorScheme
    val sameRest = plan.map { it.rest }.distinct().size == 1
    Spacer(Modifier.height(16.dp))
    plan.forEachIndexed { i, set ->
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${i + 1}",
                Modifier.width(ColSet),
                style = MaterialTheme.typography.titleMedium.merge(Tnum),
                color = cs.onSurfaceVariant,
            )
            Text(set.reps.toString(), Modifier.width(56.dp), style = MaterialTheme.typography.bodyLarge.merge(Tnum), maxLines = 1)
            Box(Modifier.width(32.dp)) { set.rir?.let { RirMark(it, filled = false) } }
            val technique = set.technique
            val backoff = set.backoff
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                when {
                    technique != null -> {
                        SymbolIcon(technique.symbol(), null, Modifier.size(18.dp), tint = cs.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(techniqueText(technique), style = MaterialTheme.typography.bodyMedium, color = cs.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    backoff != null -> {
                        SymbolIcon(Symbol.SouthEast, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.technique_backoff, backoff.percent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!sameRest) Text(clock(set.rest * 1000L), style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = cs.onSurfaceVariant)
        }
    }
    if (sameRest) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            SymbolIcon(Symbol.Timer, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.sheet_rest, clock(plan.first().rest * 1000L)), style = MaterialTheme.typography.bodyLarge.merge(Tnum))
        }
    }
}

// ---------------------------------------------------------------- effects

@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }
    return now
}

/** The phone stays awake on the session screen, so rest can be read at a glance. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Buzzes once when rest runs out with the app open. The lock-screen timer is ticket #13. */
@Composable
private fun RestEndBuzz(restEndsAt: Long?) {
    val context = LocalContext.current
    LaunchedEffect(restEndsAt) {
        val ends = restEndsAt ?: return@LaunchedEffect
        val left = ends - System.currentTimeMillis()
        if (left <= 0) return@LaunchedEffect
        delay(left)
        context.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
