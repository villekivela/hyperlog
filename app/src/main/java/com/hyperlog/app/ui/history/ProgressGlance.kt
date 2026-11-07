package com.hyperlog.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.NumberRange
import com.hyperlog.app.coach.VolumeStatus
import com.hyperlog.app.history.MovingLift
import com.hyperlog.app.history.MuscleWeek
import com.hyperlog.app.history.TrainingGlance
import com.hyperlog.app.ui.session.Direction
import com.hyperlog.app.ui.session.SessionPreview
import com.hyperlog.app.ui.session.color
import com.hyperlog.app.ui.session.onTargetColor
import com.hyperlog.app.ui.session.symbol
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.TextEdge
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val MusclesFoldTag = "muscles-fold"

/** The muscles a glance lists before its toggle: enough to read, and the rest of Progress still fits. */
private const val MusclesShown = 4

/** A section head with a short note ending on the rows' edge, or under the title once they can't share a line. */
@Composable
private fun HeadWithNote(title: String, note: String) {
    val measurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleSmall
    val noteStyle = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val words = measurer.measure(title, titleStyle, maxLines = 1, softWrap = false).size.width +
            measurer.measure(note, noteStyle, maxLines = 1, softWrap = false).size.width
        val room = with(LocalDensity.current) { (maxWidth - TextEdge * 2 - 16.dp).toPx() }
        if (words <= room) {
            SectionHead(title) { Text(note, Modifier.padding(start = 8.dp, end = TextEdge - 8.dp), style = noteStyle, color = color) }
        } else {
            Column(Modifier.padding(horizontal = TextEdge, vertical = 8.dp)) {
                Text(title, style = titleStyle, color = MaterialTheme.colorScheme.primary)
                Text(note, style = noteStyle, color = color)
            }
        }
    }
}

/** The week's muscles against their ranges, the ones furthest short first, on one scale. */
@Composable
internal fun MusclesCard(glance: TrainingGlance, standing: NumberRange, today: LocalDate) {
    val week = glance.week
    if (week == null) {
        SectionHead(stringResource(R.string.history_sets_week))
        Muted(stringResource(R.string.history_sets_nothing, standing.low, standing.high))
        return
    }
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    HeadWithNote(
        when (week) {
            thisWeek -> stringResource(R.string.history_sets_week)
            thisWeek.minusWeeks(1) -> stringResource(R.string.history_sets_last_week)
            else -> weekRange(week)
        },
        stringResource(R.string.history_on_target, glance.onTarget, glance.muscles.size),
    )
    var all by rememberSaveable { mutableStateOf(false) }
    val rows = glance.shortFirst
    if (rows.isNotEmpty()) {
        val most = glance.muscles.maxOf { maxOf(it.sets, it.range.high.toDouble()) }
        // One width for the figures, so every row's track starts and ends in the same place.
        val style = MaterialTheme.typography.labelLarge
        val measurer = rememberTextMeasurer()
        val figure = with(LocalDensity.current) { rows.maxOf { measurer.measure(formatSets(it.sets), style, maxLines = 1, softWrap = false).size.width }.toDp() }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val inner = maxWidth - TextEdge * 2
            // Wide enough for the longest word, so a name wraps only between words.
            val body = MaterialTheme.typography.bodyMedium
            val word = with(LocalDensity.current) {
                rows.flatMap { it.muscle.split(' ') }.maxOf { measurer.measure(it, body, maxLines = 1, softWrap = false).size.width }.toDp()
            }
            val name = maxOf(inner * NameShare, word + 8.dp).coerceAtMost(inner - figure - 8.dp - TrackMinimum)
            Column {
                MuscleScale(glance.muscles.map { it.range }.distinct().singleOrNull(), most, figure, name, inner - name - figure - 8.dp)
                (if (all) rows else rows.take(MusclesShown)).forEach { MuscleRow(it, most, figure, name) }
            }
        }
    }
    if (rows.size > MusclesShown) {
        Row(
            Modifier.fillMaxWidth().clickable { all = !all }.heightIn(min = 48.dp).padding(horizontal = TextEdge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (all) stringResource(R.string.history_muscles_fewer) else stringResource(R.string.history_muscles_all, rows.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            SymbolIcon(if (all) Symbol.ExpandLess else Symbol.ExpandMore, null, Modifier.size(20.dp).testTag(MusclesFoldTag), tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (glance.untrained.isNotEmpty()) {
        Box(Modifier.padding(horizontal = TextEdge, vertical = 4.dp)) {
            SessionPreview(glance.untrained.map { it.muscle }, stringResource(R.string.history_no_sets_yet))
        }
    }
}

/** The rows' scale, over the tracks: zero, the target's ends (or the top, when ranges differ) and the target in words. */
@Composable
private fun MuscleScale(range: NumberRange?, most: Double, figure: Dp, name: Dp, track: Dp) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val target = range?.let { stringResource(R.string.history_target, it.low, it.high) }.orEmpty()
    val measurer = rememberTextMeasurer()
    // Beside the ticks while it fits the name column, over them once large text outgrows it.
    val beside = with(LocalDensity.current) { measurer.measure(target, style, maxLines = 1, softWrap = false).size.width.toDp() } <= name
    if (!beside) Text(target, Modifier.padding(horizontal = TextEdge), style = style, color = color)
    Row(Modifier.fillMaxWidth().padding(horizontal = TextEdge), verticalAlignment = Alignment.CenterVertically) {
        if (beside) Text(target, Modifier.width(name), style = style, color = color) else Spacer(Modifier.width(name))
        // With no room for the target's two ends, the top stands alone: the words above say the rest.
        val density = LocalDensity.current
        val apart = range?.let {
            val low = with(density) { measurer.measure("${it.low}", style).size.width.toDp() }
            val high = with(density) { measurer.measure("${it.high}", style).size.width.toDp() }
            // Where they land: each centred on its value, the top one kept inside the track.
            val lowEnd = track * (it.low / most).toFloat() + low / 2
            val highStart = minOf(track * (it.high / most).toFloat() - high / 2, track - high)
            lowEnd + 4.dp <= highStart
        } == true
        val ticks = when {
            range == null -> listOf(0.0, most)
            apart -> listOf(0.0, range.low.toDouble(), range.high.toDouble())
            else -> listOf(0.0, range.high.toDouble())
        }
        Layout({ ticks.forEach { Text(formatSets(it), style = style, color = color) } }, Modifier.weight(1f)) { items, constraints ->
            val placed = items.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            layout(constraints.maxWidth, placed.maxOf { it.height }) {
                placed.forEachIndexed { i, tick ->
                    // Centred on its place on the track, and kept inside it at the ends.
                    val x = (constraints.maxWidth * ticks[i] / most).toInt() - tick.width / 2
                    tick.place(x.coerceIn(0, (constraints.maxWidth - tick.width).coerceAtLeast(0)), 0)
                }
            }
        }
        Spacer(Modifier.width(figure + 8.dp))
    }
}

/** One muscle: its name, its sets along the track with its range's band on it, and the figure. */
@Composable
private fun MuscleRow(week: MuscleWeek, most: Double, figure: Dp, name: Dp) {
    val status = stringResource(
        when (week.status) {
            VolumeStatus.Under -> R.string.history_status_under
            VolumeStatus.Within -> R.string.history_status_within
            VolumeStatus.Over -> R.string.history_status_over
        },
    )
    val sets = pluralStringResource(R.plurals.history_sets, if (week.sets == 1.0) 1 else 2, formatSets(week.sets))
    val spoken = stringResource(R.string.history_muscle_description, week.muscle, sets, week.range.low, week.range.high, status)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(horizontal = TextEdge).clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(week.muscle, Modifier.width(name), style = MaterialTheme.typography.bodyMedium)
        TargetBar(week.sets, week.range.low, week.range.high, most, Modifier.weight(1f))
        Text(
            formatSets(week.sets),
            Modifier.padding(start = 8.dp).width(figure),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private const val NameShare = 0.55f

/** The least a muscle's track shrinks to, for the name column's sake. */
private val TrackMinimum = 64.dp

/** The work sets of each week, read by touch; a week picked here is the one [MusclesCard] shows. */
@Composable
internal fun TrendCard(glance: TrainingGlance, onPick: (LocalDate) -> Unit) {
    val weeks = glance.weeks
    val selected = weeks.indexOfFirst { it.start == glance.week }.takeIf { it >= 0 } ?: weeks.lastIndex
    val shown = weeks[selected]
    SectionHead(pluralStringResource(R.plurals.history_week_read, shown.workSets, shown.workSets, short(shown.start))) {
        // The chart reads by touch; these step the same weeks for a screen reader.
        IconButton(onClick = { onPick(weeks[selected - 1].start) }, enabled = selected > 0) {
            SymbolIcon(Symbol.ChevronLeft, stringResource(R.string.history_week_earlier))
        }
        IconButton(onClick = { onPick(weeks[selected + 1].start) }, enabled = selected < weeks.lastIndex) {
            SymbolIcon(Symbol.ChevronRight, stringResource(R.string.history_week_later))
        }
    }
    val sets = weeks.map { it.workSets }
    WorkSetsChart(
        weeks, selected, onSelect = { onPick(weeks[it].start) },
        description = stringResource(R.string.history_weekly_chart, weeks.size, sets.min(), sets.max(), sets.last()),
        modifier = Modifier.padding(horizontal = TextEdge),
    )
    Spacer(Modifier.size(4.dp))
}

/** The lifts moving most, or what will show here. */
@Composable
internal fun MovingCard(moving: List<MovingLift>, onOpen: (String) -> Unit) {
    HeadWithNote(stringResource(R.string.history_moving), stringResource(R.string.history_moving_period))
    if (moving.isEmpty()) Muted(stringResource(R.string.history_moving_none))
    moving.forEach { MovingRow(it, onOpen) }
}

@Composable
private fun MovingRow(lift: MovingLift, onOpen: (String) -> Unit) {
    val up = lift.toKg > lift.fromKg
    val direction = if (up) Direction.Up else Direction.Down
    val percent = "${if (up) "+" else "−"}${abs(lift.percent).roundToInt()}%"
    val from = estimateText(lift.fromKg)
    val to = estimateText(lift.toKg)
    val spoken = stringResource(R.string.history_moving_description, lift.exercise.name, from, to, percent)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(lift.exercise.id) }.heightIn(min = 48.dp).padding(horizontal = TextEdge, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(lift.exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.weight_kg, to),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        SymbolIcon(direction.symbol, null, Modifier.size(20.dp), tint = if (up) onTargetColor() else direction.color)
        Text(percent, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge)
    }
}
