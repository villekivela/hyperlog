package com.hyperlog.app.ui.session

import kotlinx.coroutines.delay
import com.hyperlog.app.ui.theme.animationsRemoved
import com.hyperlog.app.ui.mannequin.RepMark
import com.hyperlog.app.session.WeekDone
import com.hyperlog.app.session.ExerciseSummary
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.hyperlog.app.ui.theme.cardBehind
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import com.hyperlog.app.ui.theme.TextEdge

/**
 * The end of a session: every exercise with its sets, `25 × 10 @ 2`. With [onOpen] an exercise
 * opens to look at or correct (by its place in [summary]).
 */
@Composable
fun SessionDoneScreen(summary: SessionSummary, onResume: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, onOpen: ((Int) -> Unit)? = null) {
    BackHandler(onBack = onClose)
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxSize(), color = Tones.screen) {
        Column(Modifier.statusBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                // The end of a session is the one moment worth a flourish: the shaped check.
                ShapedSymbol(Symbol.CheckCircle, Modifier.padding(horizontal = TextEdge).padding(top = 8.dp, bottom = 16.dp), size = 64.dp)
                Text(stringResource(R.string.done_title), Modifier.padding(horizontal = TextEdge), style = MaterialTheme.typography.headlineMedium)
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
                DoneMarks(summary.exercises)
                Spacer(Modifier.height(16.dp))
                val records = recordsOf(summary.exercises)
                DoneStats(summary, records.size)
                Spacer(Modifier.height(16.dp))
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
            pluralStringResource(R.plurals.done_sets, summary.workSets, summary.workSets),
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

/** A program week just finished: the session's moment, and a calm one. */
@Composable
private fun WeekCard(week: WeekDone) {
    Segment(0, 1) {
        Column(Modifier.fillMaxWidth().padding(CardPadding)) {
            Text(stringResource(R.string.done_week, week.week), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.done_week_line,
                    pluralStringResource(R.plurals.done_days, week.days, week.days),
                    pluralStringResource(R.plurals.done_sets, week.workSets, week.workSets),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
