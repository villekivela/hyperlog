package com.hyperlog.app.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.progression.Record
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.ui.theme.animationsRemoved
import kotlinx.coroutines.delay

/** How long a record's moment stays: long enough to read, gone by the next set. */
private const val RecordMomentMs = 2_500L

/** A set logged is felt, by the pad or the check. Opening a session or taking a set back is not. */
@Composable
internal fun SetLoggedHaptic(session: ActiveSession) {
    val haptics = LocalHapticFeedback.current
    val logged = session.exercises.sumOf { it.sets.size }
    var seen by remember(session.id) { mutableIntStateOf(logged) }
    LaunchedEffect(logged) {
        if (logged > seen) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        seen = logged
    }
}

/** How a record's moment comes in: not at all with the system's animations off. */
internal fun momentEnter(removed: Boolean): EnterTransition = if (removed) EnterTransition.None else fadeIn() + scaleIn(initialScale = 0.9f)

internal fun momentExit(removed: Boolean): ExitTransition = if (removed) ExitTransition.None else fadeOut()

/**
 * A record, the moment it is logged: a pill that comes and goes by itself, in ink like the record's
 * mark, never the accent. It takes no touches and sits clear of the pad, so the next set goes
 * straight in.
 */
@Composable
internal fun RecordMoment(session: ActiveSession, modifier: Modifier = Modifier) {
    val records = session.exercises.flatMap { it.records }.filterNotNull()
    var seen by remember(session.id) { mutableIntStateOf(records.size) }
    var showing by remember(session.id) { mutableStateOf<Record?>(null) }
    LaunchedEffect(records.size) {
        val fresh = records.size > seen
        seen = records.size
        if (fresh) {
            showing = records.last()
            delay(RecordMomentMs)
            showing = null
        }
    }
    val removed = animationsRemoved(LocalContext.current)
    val cs = MaterialTheme.colorScheme
    // Kept through the exit, which fades what was showing.
    var last by remember { mutableStateOf<Record?>(null) }
    showing?.let { last = it }
    AnimatedVisibility(showing != null, modifier, enter = momentEnter(removed), exit = momentExit(removed)) {
        last?.let { record ->
            Row(
                Modifier.background(cs.surfaceContainerHighest, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecordMark(record, Modifier.size(24.dp), tint = cs.onSurface)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.record_new), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                    Text(record.label(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
