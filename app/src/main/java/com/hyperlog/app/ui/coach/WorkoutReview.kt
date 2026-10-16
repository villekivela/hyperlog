package com.hyperlog.app.ui.coach

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.Workout
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.SectionCard
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.today.SlotRows

internal const val WorkoutReviewTag = "workout-review"

/**
 * A workout from Claude, before it goes on Today (SPEC, Coaching → Workout): its name and summary,
 * then its exercises as Today lists a day. Add to Today keeps it, asking first when it would
 * replace the one waiting ([replaces], that one's name); Reject, × and Back drop it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutReviewScreen(
    workout: Workout,
    exerciseNames: Map<String, String>,
    onAdd: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    replaces: String? = null,
    /** Where it's for: `At Itis`, `Elsewhere`; null with no gyms set up. */
    gym: String? = null,
) {
    BackHandler(onBack = onReject)
    var replacing by rememberSaveable { mutableStateOf(false) }
    if (replacing && replaces != null) {
        AlertDialog(
            onDismissRequest = { replacing = false },
            title = { Text(stringResource(R.string.workout_replace_title)) },
            text = { Text(stringResource(R.string.workout_replace_body, replaces)) },
            confirmButton = { TextButton(onClick = { replacing = false; onAdd() }) { Text(stringResource(R.string.workout_replace)) } },
            dismissButton = { TextButton(onClick = { replacing = false }) { Text(stringResource(R.string.program_cancel)) } },
        )
    }
    val cs = MaterialTheme.colorScheme
    val day = remember(workout) { workout.day() }
    val groups = remember(day) { day.slots.groupBy { it.group }.values.toList() }
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(R.string.workout_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onReject) { SymbolIcon(Symbol.Close, stringResource(R.string.change_reject)) }
                },
            )
        },
        bottomBar = {
            BottomActions(padding = PaddingValues(16.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onReject) { Text(stringResource(R.string.change_reject)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (replaces != null) replacing = true else onAdd() }) { Text(stringResource(R.string.workout_add)) }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag(WorkoutReviewTag)) {
            // What Claude built and why, in one card at the top, as a Change's review has.
            item {
                SectionCard(Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                    Text(workout.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(workout.summary, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    gym?.let {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            SymbolIcon(Symbol.LocationOn, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            itemsIndexed(groups, key = { _, g -> g.first().id }) { i, group ->
                Segment(i, groups.size) { SlotRows(group, exerciseNames, rest = true) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
