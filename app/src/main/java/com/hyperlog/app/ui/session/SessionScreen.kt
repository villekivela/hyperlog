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
import com.hyperlog.app.session.Exercise
import com.hyperlog.app.session.SessionExercise
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
    starterExercises: List<Exercise>,
    actions: SessionActions,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLeave by rememberSaveable { mutableStateOf(false) }
    var showList by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showRest by rememberSaveable { mutableStateOf(false) }
    val now = rememberNow()
    KeepScreenOn()
    RestEndBuzz(session.restEndsAt)

    BackHandler { showLeave = true }

    val current = session.current
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).windowInsetsPadding(WindowInsets.statusBars)) {
                if (current == null) {
                    ExercisePicker(
                        starterExercises,
                        title = stringResource(R.string.session_add_exercise),
                        onPick = actions::addExercise,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Header(
                        name = current.exercise.name,
                        session = session,
                        now = now,
                        onTimer = { showRest = true },
                        onAddExercise = { showPicker = true },
                        onFinish = actions::finish,
                    )
                    SetTable(current, session.entry, actions, Modifier.weight(1f))
                    val index = session.exercises.indexOf(current)
                    val next = session.exercises.getOrNull(index + 1)
                    NextLine(
                        label = if (next != null) stringResource(R.string.session_next) else null,
                        name = next?.exercise?.name ?: stringResource(R.string.session_last_exercise),
                        onClick = { showList = true },
                    )
                }
            }
            val entry = session.entry
            if (current != null && entry != null) {
                if (session.padVisible) Pad(entry, current.exercise.ladder, actions) else HiddenPad(entry, actions)
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
    if (showList) {
        Sheet(onDismiss = { showList = false }) {
            SessionList(
                session,
                onStart = { actions.switchTo(it); showList = false },
                onAdd = { showList = false; showPicker = true },
                onFinish = { showList = false; actions.finish() },
            )
        }
    }
    if (showPicker) {
        Sheet(onDismiss = { showPicker = false }) {
            ExercisePicker(
                starterExercises,
                title = stringResource(R.string.session_add_exercise),
                onPick = { actions.addExercise(it); showPicker = false },
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
        Text(
            name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        session.restEndsAt?.let { RestPill(it - now, session.restTotalMs, onTimer) }
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

// ---------------------------------------------------------------- table

@Composable
private fun SetTable(current: SessionExercise, entry: SetEntry?, actions: SessionActions, modifier: Modifier) {
    val listState = rememberLazyListState()
    // Keep the active row in view as sets pile up.
    LaunchedEffect(current.id, current.sets.size) {
        if (current.sets.isNotEmpty()) listState.animateScrollToItem(current.sets.size)
    }
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = RowInset), state = listState) {
        item { ColumnHeads(showLast = current.lastTime.isNotEmpty()) }
        items(current.sets.size) { i ->
            DoneRow(i + 1, current.lastTime.getOrNull(i), current.sets[i])
        }
        if (entry != null) {
            item {
                ActiveRow(current.sets.size + 1, current.lastTime.getOrNull(current.sets.size), entry, actions)
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

@Composable
private fun UpcomingRow(n: Int, last: LoggedSet, weight: String, reps: String) {
    val value = MaterialTheme.typography.titleLarge.merge(Tnum)
    val color = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = RowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetNumber(n, active = false)
        Last(last)
        Text(weight, Modifier.width(ColKg), color = color, textAlign = TextAlign.Center, style = value)
        Text(reps, Modifier.width(ColReps), color = color, textAlign = TextAlign.Center, style = value, maxLines = 1)
        Spacer(Modifier.width(ColRir))
    }
}

/** The column heads. "Last" only shows once there is a last time to compare with. */
@Composable
private fun ColumnHeads(showLast: Boolean) {
    val c = MaterialTheme.colorScheme.onSurfaceVariant
    val st = MaterialTheme.typography.labelMedium
    Row(Modifier.fillMaxWidth().height(LabelHeight).padding(horizontal = RowPad), verticalAlignment = Alignment.Bottom) {
        Spacer(Modifier.width(ColSet))
        Text(if (showLast) stringResource(R.string.col_last) else "", Modifier.weight(1f), style = st, color = c)
        Text(stringResource(R.string.col_kg), Modifier.width(ColKg), style = st, color = c, textAlign = TextAlign.Center)
        Text(stringResource(R.string.col_reps), Modifier.width(ColReps), style = st, color = c, textAlign = TextAlign.Center)
        Text(stringResource(R.string.col_rir), Modifier.width(ColRir), style = st, color = c, textAlign = TextAlign.Center)
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
private fun DoneRow(n: Int, last: LoggedSet?, set: LoggedSet) {
    val value = MaterialTheme.typography.titleLarge.merge(Tnum)
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = RowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetNumber(n, active = false)
        Last(last)
        Text(formatKg(set.weightKg), Modifier.width(ColKg), color = color, textAlign = TextAlign.Center, style = value)
        val reps = set.repsText()
        val repsStyle = if (reps.length > 6) MaterialTheme.typography.titleMedium.merge(Tnum) else value
        Text(reps, Modifier.width(ColReps), color = color, textAlign = TextAlign.Center, style = repsStyle, maxLines = 1)
        Box(Modifier.width(ColRir), contentAlignment = Alignment.Center) { RirMark(set.rir, filled = true) }
    }
}

@Composable
private fun ActiveRow(n: Int, last: LoggedSet?, entry: SetEntry, actions: SessionActions) {
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
        Last(last)
        Cell(
            AnnotatedString(entry.weight),
            focused = entry.field == Field.Weight,
            description = stringResource(R.string.col_kg),
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
            color = cs.onSurface,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
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
                    }
                }
                if (!now) FilledTonalButton(onClick = { onStart(e.id) }) { Text(stringResource(R.string.session_start_exercise)) }
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

/** The starter list (until the exercise library, #3). */
@Composable
private fun ExercisePicker(exercises: List<Exercise>, title: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth()) {
        item {
            Text(
                title,
                Modifier.padding(start = Margin, end = Margin, top = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        items(exercises, key = { it.id }) { e ->
            Text(
                e.name,
                Modifier.fillMaxWidth().clickable { onPick(e.id) }.heightIn(min = 56.dp).padding(horizontal = Margin, vertical = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
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
