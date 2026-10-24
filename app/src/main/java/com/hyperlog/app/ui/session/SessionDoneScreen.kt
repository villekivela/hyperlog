package com.hyperlog.app.ui.session

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
                ShapedSymbol(Symbol.CheckCircle, Modifier.padding(horizontal = Margin).padding(top = 8.dp, bottom = 16.dp), size = 64.dp)
                Text(stringResource(R.string.done_title), Modifier.padding(horizontal = Margin), style = MaterialTheme.typography.headlineMedium)
                val minutes = ((summary.finishedAt - summary.startedAt) / 60_000.0).roundToInt()
                Text(
                    stringResource(
                        R.string.done_when,
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(summary.startedAt)),
                        minutes,
                    ),
                    Modifier.padding(horizontal = Margin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                val records = recordsOf(summary.exercises)
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
