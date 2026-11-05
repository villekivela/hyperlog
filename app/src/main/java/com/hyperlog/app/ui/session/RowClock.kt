package com.hyperlog.app.ui.session

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.hyperlog.app.session.ActiveSession
import com.hyperlog.app.session.FollowUp

// The Next row's clock (docs/design/logging-screen.md): rest, a myo breathing pause and an iso hold.

/** What the Next row's clock is showing. */
internal enum class ClockKind { Rest, Over, Breath, Hold, HoldReached }

/**
 * The session's one clock, drawn on the Next row: [time] in large digits and [fraction] of the
 * row's width filled. Rest and the breathing pause drain, the hold fills toward its target, and
 * after rest it counts up on a muted row with nothing filled.
 */
internal data class RowClock(val kind: ClockKind, val time: String, val fraction: Float)

/** The clock to show at [now]: the hold, the breathing pause before a mini-set, rest or the count-up after it; null when none runs. */
internal fun rowClock(session: ActiveSession, now: Long): RowClock? {
    val hold = session.followUp as? FollowUp.Hold
    val holdStarted = session.holdStartedAt
    val restEnds = session.restEndsAt
    return when {
        hold != null && holdStarted != null -> {
            val heldMs = now - holdStarted
            val targetMs = hold.seconds * 1000L
            val reached = heldMs >= targetMs
            RowClock(
                if (reached) ClockKind.HoldReached else ClockKind.Hold,
                clock(-heldMs).removePrefix("+"),
                if (reached || targetMs <= 0) 1f else heldMs.toFloat() / targetMs,
            )
        }
        restEnds != null && session.breathing ->
            if (restEnds > now) RowClock(ClockKind.Breath, clock(restEnds - now), drained(restEnds - now, session.restTotalMs)) else null
        restEnds != null -> {
            val left = restEnds - now
            RowClock(if (left > 0) ClockKind.Rest else ClockKind.Over, clock(left), if (left > 0) drained(left, session.restTotalMs) else 0f)
        }
        else -> null
    }
}

private fun drained(leftMs: Long, totalMs: Long) = if (totalMs <= 0) 0f else (leftMs.toFloat() / totalMs).coerceIn(0f, 1f)

/**
 * The row's fill and the colour of what is written on it, for a clock of [kind]. The fill is a
 * tint of the role's colour, so the ink is readable on it and on the bare row the fill drains away
 * from; only a reached hold fills the row solid.
 */
@Composable
internal fun ClockKind.colours(): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (this) {
        ClockKind.Rest -> cs.primary.copy(alpha = 0.24f) to cs.onSurface
        ClockKind.Over -> Color.Transparent to cs.onSurfaceVariant
        ClockKind.Breath -> cs.tertiary.copy(alpha = 0.28f) to cs.onSurface
        ClockKind.Hold -> cs.secondary.copy(alpha = 0.24f) to cs.onSurface
        ClockKind.HoldReached -> cs.primary to cs.onPrimary
    }
}

/** Buzzes once when the hold reaches its target. */
@Composable
internal fun HoldReachedBuzz(reached: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(reached) {
        if (reached) context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/** `1:42` while counting down, `+0:12` after. */
internal fun clock(leftMs: Long): String {
    val over = leftMs <= 0
    val ms = if (over) -leftMs else leftMs
    val secs = if (over) ms / 1000 else (ms + 999) / 1000
    return (if (over) "+" else "") + "%d:%02d".format(secs / 60, secs % 60)
}
