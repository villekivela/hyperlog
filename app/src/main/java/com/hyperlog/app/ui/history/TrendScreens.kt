package com.hyperlog.app.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.domain.summary
import com.hyperlog.app.history.Bodyweight
import com.hyperlog.app.history.ExerciseTrend
import com.hyperlog.app.history.TrendLine
import com.hyperlog.app.history.TrendPoint
import com.hyperlog.app.library.Equipment
import com.hyperlog.app.ui.session.Margin
import com.hyperlog.app.ui.session.Tnum
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import kotlin.math.roundToInt

/**
 * An exercise's top sets and estimate over time (docs/design/history.md): the figure, the chart
 * and its table, once per machine for machines (ADR 0002). A table row opens its session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseTrendScreen(
    trend: ExerciseTrend,
    hasBodyweight: Boolean,
    onOpenSession: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(trend.exercise.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val perMachine = trend.exercise.equipment.perMachine
        val weighted = trend.exercise.equipment == Equipment.WeightedBodyweight
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            trend.lines.forEachIndexed { n, line ->
                item(key = "chart-$n") {
                    if (perMachine) {
                        if (n > 0) Spacer(Modifier.height(16.dp))
                        Text(
                            line.machine ?: stringResource(R.string.history_machine_unknown),
                            Modifier.padding(horizontal = Margin, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    LineChart(trend, line, note = if (weighted && !hasBodyweight) stringResource(R.string.history_needs_bodyweight) else null)
                }
                items(line.points.reversed(), key = { "row-$n-${it.sessionId}" }) { p -> PointRow(trend, p) { onOpenSession(p.sessionId) } }
            }
            item { Spacer(Modifier.navigationBarsPadding().heightIn(min = 16.dp)) }
        }
    }
}

/** One line's figure, readout, chart and legend. */
@Composable
private fun LineChart(trend: ExerciseTrend, line: TrendLine, note: String?) {
    val cs = MaterialTheme.colorScheme
    val points = line.points
    if (points.isEmpty()) return
    var selected by rememberSaveable(line.itemId) { mutableStateOf(points.size - 1) }
    val index = selected.coerceIn(0, points.size - 1)
    val byReps = trend.byReps
    // A weighted bodyweight estimate counts the body, so the belt's top sets are left off its scale.
    val dotsShown = !byReps && trend.exercise.equipment != Equipment.WeightedBodyweight
    val value: (TrendPoint) -> Double? = { if (byReps) it.top.progressionReps.toDouble() else it.estimateKg }
    Text(
        stringResource(
            when {
                byReps -> R.string.history_most_reps
                trend.exercise.equipment == Equipment.WeightedBodyweight -> R.string.history_estimate_with_body
                else -> R.string.history_estimate
            },
        ),
        Modifier.padding(horizontal = Margin),
        style = MaterialTheme.typography.labelLarge,
        color = cs.onSurfaceVariant,
    )
    Text(
        points.last().let(value)?.let { figure(it, byReps) } ?: "–",
        Modifier.padding(horizontal = Margin),
        style = MaterialTheme.typography.headlineMedium,
    )
    val p = points[index]
    Text(
        listOfNotNull(long(p.date), "${stringResource(R.string.history_top_set).lowercase()} ${p.top.summary()}", value(p)?.takeIf { !byReps }?.let { "${estimateText(it)} kg" })
            .joinToString(" · "),
        Modifier.padding(horizontal = Margin),
        style = MaterialTheme.typography.bodyMedium.merge(Tnum),
        color = cs.onSurfaceVariant,
    )
    note?.let { Text(it, Modifier.padding(horizontal = Margin), style = MaterialTheme.typography.bodyMedium, color = cs.tertiary) }
    Spacer(Modifier.height(8.dp))
    TrendChart(
        dates = points.map { it.date },
        line = points.map(value),
        dots = points.map { if (dotsShown) it.top.weightKg else null },
        selected = index,
        onSelect = { selected = it },
        modifier = Modifier.padding(start = Margin, end = 8.dp),
        description = stringResource(R.string.history_trend_chart, trend.exercise.name),
    )
    if (dotsShown) Legend()
    Spacer(Modifier.height(8.dp))
}

/** Estimate as a short line, top set as a dot: the two marks, in their own shapes. */
@Composable
private fun Legend() {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = Margin, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(16.dp, 8.dp)) {
            drawLine(cs.primary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.history_estimate), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        val context = contextInk()
        Canvas(Modifier.size(8.dp)) { drawCircle(context) }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.history_top_set), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
}

/** A session on the trend, as a table row: date, top set, estimate. */
@Composable
private fun PointRow(trend: ExerciseTrend, p: TrendPoint, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = Margin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(long(p.date), Modifier.width(112.dp), style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = cs.onSurfaceVariant)
        Text(p.top.summary(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.merge(Tnum), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!trend.byReps) {
            Text(
                p.estimateKg?.let { estimateText(it) } ?: "",
                Modifier.width(56.dp),
                style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun figure(v: Double, byReps: Boolean) =
    if (byReps) stringResource(R.string.history_reps, v.roundToInt()) else "${estimateText(v)} kg"

/** The bodyweight log: the figure and its line, then every day logged, newest first, each deletable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyweightScreen(
    bodyweights: List<Bodyweight>,
    onLog: (Double) -> Unit,
    onDelete: (java.time.LocalDate) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var logging by rememberSaveable { mutableStateOf(false) }
    // The day read, oldest first as the chart has them; the latest until a day is picked.
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val days = remember(bodyweights) { bodyweights.reversed() }
    val index = days.indexOfFirst { it.date.toEpochDay() == selected }.takeIf { it >= 0 } ?: (days.size - 1)
    val cs = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_bodyweight)) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
                actions = { IconButton(onClick = { logging = true }) { SymbolIcon(Symbol.Add, stringResource(R.string.history_log_bodyweight)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (logging) {
                item { LogBodyweight(bodyweights.firstOrNull()?.kg, onSave = { logging = false; onLog(it) }, onDismiss = { logging = false }) }
            }
            item { BodyweightChart(days, index, onSelect = { selected = days[it].date.toEpochDay() }, onDelete = { onDelete(it); selected = null }) }
            items(bodyweights, key = { it.date.toEpochDay() }) { b ->
                val picked = days.getOrNull(index)?.date == b.date
                Row(
                    Modifier.fillMaxWidth().clickable { selected = b.date.toEpochDay() }.heightIn(min = 48.dp).padding(horizontal = Margin),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(long(b.date), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = if (picked) cs.primary else cs.onSurfaceVariant)
                    Text("${formatKg(b.kg)} kg", style = MaterialTheme.typography.bodyLarge.merge(Tnum), color = if (picked) cs.primary else cs.onSurface)
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().heightIn(min = 16.dp)) }
        }
    }
}

/**
 * The log's figure, the day read (a tap on the chart or a row picks it) with × to delete it, and
 * the full chart.
 */
@Composable
private fun BodyweightChart(days: List<Bodyweight>, index: Int, onSelect: (Int) -> Unit, onDelete: (java.time.LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (days.isEmpty()) {
        Muted(stringResource(R.string.history_bodyweight_none))
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Text("${formatKg(days.last().kg)} kg", Modifier.padding(horizontal = Margin), style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth().padding(start = Margin, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${long(days[index].date)} · ${formatKg(days[index].kg)} kg",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                color = cs.onSurfaceVariant,
            )
            IconButton(onClick = { onDelete(days[index].date) }) {
                SymbolIcon(Symbol.Delete, stringResource(R.string.history_delete_bodyweight, long(days[index].date)), tint = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        TrendChart(
            dates = days.map { it.date },
            line = days.map { it.kg },
            dots = days.map { null },
            selected = index,
            onSelect = onSelect,
            modifier = Modifier.padding(start = Margin, end = 8.dp),
            description = stringResource(R.string.history_trend_chart, stringResource(R.string.history_bodyweight)),
        )
        Spacer(Modifier.height(16.dp))
    }
}
