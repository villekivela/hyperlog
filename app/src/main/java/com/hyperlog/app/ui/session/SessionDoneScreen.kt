package com.hyperlog.app.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.hyperlog.app.ui.mannequin.RepPlay
import com.hyperlog.app.ui.mannequin.LocalExercises
import com.hyperlog.app.session.SessionRating
import com.hyperlog.app.session.Heaviest
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateColorAsState
import kotlinx.coroutines.delay
import com.hyperlog.app.ui.theme.animationsRemoved
import com.hyperlog.app.ui.mannequin.RepMark
import com.hyperlog.app.session.WeekDone
import com.hyperlog.app.session.ExerciseSummary
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.summary
import com.hyperlog.app.session.SessionSummary
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.ShapedSymbol
import com.hyperlog.app.ui.theme.Tones
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import com.hyperlog.app.ui.theme.TextEdge

/**
 * The end of a session: every exercise with its sets, `25 × 10 @ 2`. With [onOpen] an exercise
 * opens to look at or correct (by its place in [summary]).
 */
@Composable
fun SessionDoneScreen(summary: SessionSummary, firstShown: Boolean = true, onShown: (Long) -> Unit = {}, onResume: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, onOpen: ((Int) -> Unit)? = null) {
    BackHandler(onBack = onClose)
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(summary.sessionId) {
        if (!firstShown) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (summary.week != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onShown(summary.sessionId)
    }
    Surface(modifier.fillMaxSize(), color = Tones.screen) {
        Column(Modifier.statusBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                // The end of a session is worth a flourish: its best lift does the rep, or the shaped check.
                val lead = (summary.bestLift ?: summary.exercises.firstOrNull())?.exerciseId?.let { LocalExercises.current[it] }
                if (lead != null) RepPlay(lead, Modifier.padding(horizontal = TextEdge).padding(bottom = 8.dp), size = 128.dp, ground = Tones.screen)
                else ShapedSymbol(Symbol.CheckCircle, Modifier.padding(horizontal = TextEdge).padding(top = 8.dp, bottom = 16.dp), size = 64.dp)
                Text(stringResource(R.string.done_title), Modifier.padding(horizontal = TextEdge), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Text(
                    stringResource(
                        when (summary.rating) {
                            SessionRating.Strong -> R.string.done_rating_strong
                            SessionRating.Solid -> R.string.done_rating_solid
                            SessionRating.Banked -> R.string.done_rating_banked
                        },
                    ),
                    Modifier.padding(horizontal = TextEdge),
                    style = MaterialTheme.typography.headlineMedium,
                )
                val minutes = ((summary.finishedAt - summary.startedAt) / 60_000.0).roundToInt()
                Text(
                    stringResource(
                        R.string.done_when,
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(summary.startedAt)),
                        minutes,
                    ),
                    Modifier.padding(horizontal = TextEdge),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                val records = recordsOf(summary.exercises)
                DoneStats(summary, records.size)
                Spacer(Modifier.height(12.dp))
                summary.bestLift?.let { best ->
                    Text(
                        stringResource(R.string.done_best, best.name, best.lastTop!!.short().unbroken(), best.sets.maxWith(Heaviest).short().unbroken()),
                        Modifier.padding(horizontal = TextEdge),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                if (summary.exercises.size > 1) {
                    DoneMarks(summary.exercises)
                    Spacer(Modifier.height(16.dp))
                }
                summary.week?.let {
                    WeekCard(it)
                    Spacer(Modifier.height(16.dp))
                }
                if (records.isNotEmpty()) {
                    Segment(0, 1) {
                        Column(Modifier.fillMaxWidth().padding(CardPadding).testTag(RecordListTag)) {
                            Text(stringResource(R.string.records_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            RecordLines(records)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                SummaryRows(summary.exercises, onOpen)
            }
            BottomActions(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Spacer(Modifier.weight(1f))
                // Finished by mistake: back into the session exactly as it was.
                TextButton(onClick = onResume) { Text(stringResource(R.string.today_resume_session)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onClose) { Text(stringResource(R.string.done_close)) }
            }
        }
    }
}

internal const val RecordListTag = "record-list"
internal const val DoneMarkTag = "done-mark"
internal const val WeekDotTag = "week-dot"

/** How many of [count] marks show before the reveal: all of them with the system's animations off. */
internal fun doneMarksAtFirst(count: Int, removed: Boolean): Int = if (removed) count else 0

/** The session's reps together, coming in one by one. Nothing waits for them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneMarks(exercises: List<ExerciseSummary>) {
    val ids = exercises.mapNotNull { it.exerciseId }
    val removed = animationsRemoved(LocalContext.current)
    var shown by remember { mutableIntStateOf(doneMarksAtFirst(ids.size, removed)) }
    LaunchedEffect(Unit) {
        while (shown < ids.size) {
            delay(MarkStepMs)
            shown++
        }
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = TextEdge),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ids.forEachIndexed { i, id ->
            val alpha by animateFloatAsState(if (i < shown) 1f else 0f)
            RepMark(id, Modifier.graphicsLayer { this.alpha = alpha }.testTag(DoneMarkTag))
        }
    }
}

private const val MarkStepMs = 90L

/** A set's parts joined by no-break spaces, so a line never ends on `50 ×`. */
private fun String.unbroken() = replace(' ', '\u00A0')

/** What the session came to, as figures: its sets, the lifts up on last time, and its records. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneStats(summary: SessionSummary, records: Int) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = TextEdge),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val stats = listOfNotNull(
            summary.planned?.let { pluralStringResource(R.plurals.done_sets_of, it, summary.plannedDone, it) } ?: pluralStringResource(R.plurals.done_sets, summary.workSets, summary.workSets),
            summary.aimed.takeIf { it > 0 }?.let { stringResource(R.string.done_on_effort, summary.onEffort, it) },
            summary.compared.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.done_lifts_up, it, summary.up, it) },
            records.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.done_records, it, it) },
        )
        stats.forEach {
            Text(
                it,
                Modifier.background(Tones.card, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurface,
            )
        }
    }
}

/** A program week just finished: a dot for each day trained, filling in, then how it went. */
@Composable
private fun WeekCard(week: WeekDone) {
    val cs = MaterialTheme.colorScheme
    val removed = animationsRemoved(LocalContext.current)
    var filled by remember { mutableIntStateOf(if (removed) week.days else 0) }
    LaunchedEffect(Unit) {
        while (filled < week.days) {
            delay(WeekDotMs)
            filled++
        }
    }
    Segment(0, 1) {
        Column(Modifier.fillMaxWidth().padding(CardPadding)) {
            Text(stringResource(R.string.done_week, week.week), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(week.days) { i ->
                    val ink by animateColorAsState(if (i < filled) cs.onSurface else cs.outlineVariant)
                    Box(Modifier.size(12.dp).background(ink, CircleShape).testTag(WeekDotTag))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.done_week_line,
                    pluralStringResource(R.plurals.done_days, week.days, week.days),
                    pluralStringResource(R.plurals.done_sets, week.workSets, week.workSets),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOfNotNull(
                week.lastWeekSets?.let { last ->
                    val change = week.workSets - last
                    when {
                        change > 0 -> stringResource(R.string.done_week_more, change)
                        change < 0 -> stringResource(R.string.done_week_fewer, -change)
                        else -> stringResource(R.string.done_week_same)
                    }
                },
                week.streak.takeIf { it > 1 }?.let { stringResource(R.string.done_week_streak, it) },
            ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
        }
    }
}

private const val WeekDotMs = 120L
