package com.hyperlog.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
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

/** The muscles a glance lists before its toggle: enough to read, and the rest of Progress still fits. */
private const val MusclesShown = 4

/** The week's muscles against their ranges, the ones furthest short first. */
@Composable
internal fun MusclesCard(glance: TrainingGlance, standing: NumberRange, today: LocalDate) {
    val week = glance.week
    if (week == null) {
        SectionHead(stringResource(R.string.history_sets_week))
        Muted(stringResource(R.string.history_sets_nothing, standing.low, standing.high))
        return
    }
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    SectionHead(
        when (week) {
            thisWeek -> stringResource(R.string.history_sets_week)
            thisWeek.minusWeeks(1) -> stringResource(R.string.history_sets_last_week)
            else -> weekRange(week)
        },
    ) {
        Text(
            stringResource(R.string.history_on_target, glance.onTarget, glance.muscles.size),
            Modifier.padding(end = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    var all by rememberSaveable { mutableStateOf(false) }
    val rows = glance.shortFirst
    val most = glance.muscles.maxOf { maxOf(it.sets, it.range.high.toDouble()) }
    (if (all) rows else rows.take(MusclesShown)).forEach { MuscleRow(it, most) }
    if (rows.size > MusclesShown) {
        Text(
            if (all) stringResource(R.string.history_muscles_fewer) else stringResource(R.string.history_muscles_all, rows.size),
            Modifier.fillMaxWidth().clickable { all = !all }.heightIn(min = 48.dp).padding(horizontal = TextEdge, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (glance.untrained.isNotEmpty()) {
        Box(Modifier.padding(horizontal = TextEdge, vertical = 4.dp)) {
            SessionPreview(glance.untrained.map { it.muscle }, stringResource(R.string.history_no_sets_yet))
        }
    }
}

/** One muscle: its name, its sets as a bar over the band of its range, and the figure. */
@Composable
private fun MuscleRow(week: MuscleWeek, most: Double) {
    val status = stringResource(
        when (week.status) {
            VolumeStatus.Under -> R.string.history_status_under
            VolumeStatus.Within -> R.string.history_status_within
            VolumeStatus.Over -> R.string.history_status_over
        },
    )
    val spoken = stringResource(R.string.history_muscle_description, week.muscle, formatSets(week.sets), week.range.low, week.range.high, status)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(horizontal = TextEdge).clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(week.muscle, Modifier.weight(0.55f), style = MaterialTheme.typography.bodyMedium)
        TargetBar(week.sets, week.range.low, week.range.high, most, Modifier.weight(0.45f))
        Text(
            formatSets(week.sets),
            Modifier.padding(start = 8.dp).widthIn(min = 32.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The work sets of each week, read by touch; a week picked here is the one [MusclesCard] shows. */
@Composable
internal fun TrendCard(glance: TrainingGlance, onPick: (LocalDate) -> Unit) {
    val weeks = glance.weeks
    val selected = weeks.indexOfFirst { it.start == glance.week }.takeIf { it >= 0 } ?: weeks.lastIndex
    val shown = weeks[selected]
    SectionHead(stringResource(R.string.history_weekly_sets)) {
        Text(
            stringResource(R.string.history_week_read, shown.workSets, short(shown.start)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The chart reads by touch; these step the same weeks for a screen reader.
        IconButton(onClick = { onPick(weeks[selected - 1].start) }, enabled = selected > 0) {
            SymbolIcon(Symbol.ChevronLeft, stringResource(R.string.history_week_earlier))
        }
        IconButton(onClick = { onPick(weeks[selected + 1].start) }, enabled = selected < weeks.lastIndex) {
            SymbolIcon(Symbol.ChevronRight, stringResource(R.string.history_week_later))
        }
    }
    val sets = weeks.map { it.workSets }
    WeeklySetsChart(
        weeks, selected, onSelect = { onPick(weeks[it].start) },
        description = stringResource(R.string.history_weekly_chart, weeks.size, sets.min(), sets.max(), sets.last()),
        modifier = Modifier.padding(horizontal = TextEdge),
    )
    Spacer(Modifier.size(8.dp))
}

/** The lifts moving most, or what will show here. */
@Composable
internal fun MovingCard(moving: List<MovingLift>, onOpen: (String) -> Unit) {
    SectionHead(stringResource(R.string.history_moving))
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
        SymbolIcon(direction.symbol, null, Modifier.size(20.dp), tint = if (up) onTargetColor() else direction.color)
        Spacer(Modifier.width(12.dp))
        Text(lift.exercise.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.history_moving_value, to),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(percent, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}
