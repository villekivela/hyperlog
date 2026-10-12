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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.summary
import com.hyperlog.app.session.SessionSummary
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
    Surface(modifier.fillMaxSize(), color = cs.surface) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                Text(stringResource(R.string.done_title), Modifier.padding(horizontal = Margin), style = MaterialTheme.typography.headlineSmall)
                val minutes = ((summary.finishedAt - summary.startedAt) / 60_000.0).roundToInt()
                Text(
                    stringResource(
                        R.string.done_when,
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(summary.startedAt)),
                        minutes,
                    ),
                    Modifier.padding(horizontal = Margin),
                    style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SummaryRows(summary.exercises, onOpen)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Margin, vertical = 16.dp), horizontalArrangement = Arrangement.End) {
                // Finished by mistake: back into the session exactly as it was.
                TextButton(onClick = onResume) { Text(stringResource(R.string.today_resume_session)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onClose) { Text(stringResource(R.string.done_close)) }
            }
        }
    }
}
