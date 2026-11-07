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
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.progression.Record
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.ui.mannequin.RepPlay
import com.hyperlog.app.ui.theme.animationsRemoved
import kotlinx.coroutines.delay

/** How long a record's moment stays: long enough for the rep and a read, gone by the next set. */
private const val RecordMomentMs = 3_000L

/** Everything logged so far: sets, and the drops, mini-sets and holds that extend them. */
private fun ActiveSession.logged() = exercises.sumOf { e -> e.sets.size + e.sets.sumOf { it.extensions.size } }

/** A set logged is felt, by the pad or the check. Opening a session or taking a set back is not. */
@Composable
internal fun SetLoggedHaptic(session: ActiveSession) {
    val haptics = LocalHapticFeedback.current
    val logged = session.logged()
    var seen by remember(session.id) { mutableIntStateOf(logged) }
    LaunchedEffect(logged) {
        if (logged > seen) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        seen = logged
    }
}

/** How a record's moment comes in: not at all with the system's animations off. */
internal fun momentEnter(removed: Boolean): EnterTransition = if (removed) EnterTransition.None else fadeIn() + scaleIn(initialScale = 0.9f)

internal fun momentExit(removed: Boolean): ExitTransition = if (removed) ExitTransition.None else fadeOut()

/** The record to show: which it is, the exercise that made it and the set. */
private class RecordShown(val record: Record, val exercise: Exercise, val set: LoggedSet)

/** The record just made, if the set [session] logged last made one. */
private fun newest(session: ActiveSession): RecordShown? {
    val exercise = session.exercises.firstOrNull { it.id == session.latestSet?.first } ?: session.exercises.lastOrNull { e -> e.records.any { it != null } }
    exercise ?: return null
    // Sets can be logged out of order, so the last place isn't always the set just logged.
    val at = session.latestSet?.second?.let(exercise.setIndices::indexOf)?.takeIf { it >= 0 } ?: exercise.records.indexOfLast { it != null }
    val record = exercise.records.getOrNull(at) ?: return null
    return RecordShown(record, exercise.exercise, exercise.sets[at])
}

/**
 * A record, the moment it is logged: the figure does the rep in a pill that goes by itself, in ink
 * like the record's mark, never the accent. It takes no touches and sits clear of the pad, so the
 * next set goes straight in. It is felt apart from an ordinary set.
 */
@Composable
internal fun RecordMoment(session: ActiveSession, modifier: Modifier = Modifier) {
    val count = session.exercises.sumOf { e -> e.records.count { it != null } }
    val logged = session.logged()
    var seen by remember(session.id) { mutableIntStateOf(count) }
    var seenLogged by remember(session.id) { mutableIntStateOf(logged) }
    var showing by remember(session.id) { mutableStateOf<RecordShown?>(null) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(count, logged) {
        // A correction can make a record too, but logs nothing, and a set taken back takes it away.
        val fresh = count > seen && logged > seenLogged
        if (count < seen) showing = null
        seen = count
        seenLogged = logged
        if (fresh) {
            showing = newest(session)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(showing) {
        if (showing != null) {
            delay(RecordMomentMs)
            showing = null
        }
    }
    val removed = animationsRemoved(LocalContext.current)
    val cs = MaterialTheme.colorScheme
    // Kept through the exit, which fades what was showing.
    var last by remember { mutableStateOf<RecordShown?>(null) }
    showing?.let { last = it }
    AnimatedVisibility(showing != null, modifier, enter = momentEnter(removed), exit = momentExit(removed)) {
        last?.let { shown ->
            Row(
                Modifier.background(cs.surfaceContainerHighest, RoundedCornerShape(24.dp)).padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RepPlay(shown.exercise, size = 56.dp, ground = cs.surfaceContainerHighest)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.record_new), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                    Text(stringResource(R.string.record_detail, shown.record.label(), shown.set.short()), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
