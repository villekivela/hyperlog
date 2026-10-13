package com.hyperlog.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.history.Bodyweight
import com.hyperlog.app.history.ExerciseTrend
import com.hyperlog.app.history.WeekSets
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.ui.session.Margin
import com.hyperlog.app.ui.session.SessionList
import com.hyperlog.app.ui.session.Tnum
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// History (SPEC, Screens → History; docs/design/history.md): Sessions and Progress.

/** `28 Sep`. */
internal fun short(date: LocalDate): String = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(date)

/** `Mon 28 Sep`. */
internal fun long(date: LocalDate): String = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(date)

/** `12–18 Oct`, or `28 Sep – 4 Oct` across months. */
internal fun weekRange(start: LocalDate): String {
    val end = start.plusDays(6)
    return if (start.month == end.month) "${start.dayOfMonth}–${short(end)}" else "${short(start)} – ${short(end)}"
}

/** An estimate as History shows it: whole kg from 20 kg, else one decimal (`99`, `8.4`). */
internal fun estimateText(kg: Double): String = if (kg >= 20) formatKg(kg.roundToInt().toDouble()) else formatKg(Math.round(kg * 10) / 10.0)

/** What an exercise's trend plots for a session: reps for a plain bodyweight exercise, else the estimate. */
internal val ExerciseTrend.byReps: Boolean get() = exercise.equipment == Equipment.Bodyweight

/** History: the session list, and Progress (bodyweight, sets per muscle, exercises). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessions: List<PastSessionItem>?,
    weeks: List<WeekSets>?,
    exercises: List<ExerciseTrend>?,
    bodyweights: List<Bodyweight>,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenExercise: (String) -> Unit,
    onBodyweight: () -> Unit,
    onLogBodyweight: (Double) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { onTab(0) }, text = { Text(stringResource(R.string.history_sessions)) })
                    Tab(selected = tab == 1, onClick = { onTab(1) }, text = { Text(stringResource(R.string.history_progress)) })
                }
            }
        },
    ) { padding ->
        if (tab == 0) {
            SessionList(sessions, onOpenSession, Modifier.padding(padding))
        } else {
            Progress(weeks, exercises, bodyweights, onOpenExercise, onBodyweight, onLogBodyweight, Modifier.padding(padding))
        }
    }
}

@Composable
private fun Progress(
    weeks: List<WeekSets>?,
    exercises: List<ExerciseTrend>?,
    bodyweights: List<Bodyweight>,
    onOpenExercise: (String) -> Unit,
    onBodyweight: () -> Unit,
    onLogBodyweight: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var logging by rememberSaveable { mutableStateOf(false) }
    var week by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(modifier.fillMaxSize()) {
        item {
            SectionHead(stringResource(R.string.history_bodyweight)) {
                IconButton(onClick = { logging = true }) { SymbolIcon(Symbol.Add, stringResource(R.string.history_log_bodyweight)) }
            }
            if (logging) {
                LogBodyweight(bodyweights.firstOrNull()?.kg, onSave = { logging = false; onLogBodyweight(it) }, onDismiss = { logging = false })
            }
            BodyweightSummary(bodyweights, onBodyweight, chartHeight = 64.dp)
        }
        item {
            val shown = weeks?.getOrNull(week)
            SectionHead(if (week == 0 || shown == null) stringResource(R.string.history_sets_week) else weekRange(shown.start)) {
                if (weeks != null) {
                    IconButton(onClick = { week++ }, enabled = week < weeks.size - 1) {
                        SymbolIcon(Symbol.ChevronLeft, stringResource(R.string.history_week_earlier))
                    }
                    IconButton(onClick = { week-- }, enabled = week > 0) {
                        SymbolIcon(Symbol.ChevronRight, stringResource(R.string.history_week_later))
                    }
                }
            }
            if (shown != null) {
                if (shown.sets.isEmpty()) {
                    Muted(stringResource(R.string.history_sets_none))
                } else {
                    WeekSets(shown.sets)
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionHead(stringResource(R.string.history_exercises))
            if (exercises != null && exercises.isEmpty()) Muted(stringResource(R.string.history_exercises_none))
        }
        items(exercises.orEmpty(), key = { it.exercise.id }) { trend -> ExerciseRow(trend) { onOpenExercise(trend.exercise.id) } }
        item { Spacer(Modifier.navigationBarsPadding().heightIn(min = 16.dp)) }
    }
}

/** The latest bodyweight as the figure, with its date, over a small line of the last 90 days. */
@Composable
internal fun BodyweightSummary(bodyweights: List<Bodyweight>, onClick: (() -> Unit)?, chartHeight: androidx.compose.ui.unit.Dp) {
    val cs = MaterialTheme.colorScheme
    val latest = bodyweights.firstOrNull()
    if (latest == null) {
        Muted(stringResource(R.string.history_bodyweight_none))
        return
    }
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.padding(horizontal = Margin), verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.weight_kg, formatKg(latest.kg)), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Text(short(latest.date), style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = cs.onSurfaceVariant)
        }
        val recent = bodyweights.filter { it.date >= latest.date.minusDays(90) }.reversed()
        if (recent.size > 1) {
            Spacer(Modifier.height(8.dp))
            TrendChart(
                dates = recent.map { it.date },
                line = recent.map { it.kg },
                dots = recent.map { null },
                selected = recent.size - 1,
                onSelect = { onClick?.invoke() },
                modifier = Modifier.padding(start = Margin, end = 8.dp),
                height = chartHeight,
                axis = false,
                ticks = false,
                description = stringResource(R.string.history_trend_chart, stringResource(R.string.history_bodyweight)),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** An exercise on Progress: its name, a sparkline of its estimate and the latest value. */
@Composable
private fun ExerciseRow(trend: ExerciseTrend, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(start = Margin, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(trend.exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(16.dp))
        val line = trend.lines.singleOrNull()
        if (line == null) {
            Text(stringResource(R.string.history_machines, trend.lines.size), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        } else {
            val values = line.points.mapNotNull { if (trend.byReps) it.top.progressionReps.toDouble() else it.estimateKg }
            Sparkline(values.takeLast(12))
            Spacer(Modifier.width(16.dp))
            Text(
                values.lastOrNull()?.let { if (trend.byReps) "${it.roundToInt()}" else stringResource(R.string.weight_kg, estimateText(it)) } ?: "",
                Modifier.width(64.dp),
                style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                color = cs.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        Spacer(Modifier.width(8.dp))
        SymbolIcon(Symbol.ChevronRight, null, tint = cs.onSurfaceVariant)
    }
}

@Composable
internal fun SectionHead(text: String, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = Margin, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actions()
    }
}

@Composable
internal fun Muted(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = Margin, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Today's bodyweight, opened in place under its head and typed on the system keyboard (as setup
 * is): the field, Save, and × to close it.
 */
@Composable
internal fun LogBodyweight(latest: Double?, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val kg = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..400.0 }
    Row(Modifier.fillMaxWidth().padding(start = Margin, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.history_bodyweight_today)) },
            placeholder = { Text(latest?.let(::formatKg).orEmpty()) },
            suffix = { Text(stringResource(R.string.col_kg)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { kg?.let(onSave) }, enabled = kg != null) { Text(stringResource(R.string.history_save)) }
        IconButton(onClick = onDismiss) { SymbolIcon(Symbol.Close, stringResource(R.string.history_cancel)) }
    }
}
