package com.hyperlog.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.summary
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.session.PastSession
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.ShapedSymbol
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.cardBehind
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.height
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Past sessions (SPEC, Past sessions are editable): the list, a session's summary, and an
// exercise's table where any logged row can be corrected. History (#19) hosts the list.

/** `Tue 13 Oct`. */
internal fun day(at: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

/**
 * Every finished session, newest first (History → Sessions): the day trained and the date, then
 * the gym and its exercises. A row opens the session.
 */
@Composable
fun SessionList(sessions: List<PastSessionItem>?, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(modifier.fillMaxSize()) {
        if (sessions != null && sessions.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.past_empty),
                    Modifier.padding(horizontal = Margin, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        val list = sessions.orEmpty()
        item { Spacer(Modifier.height(8.dp)) }
        itemsIndexed(list, key = { _, it -> it.id }) { i, s ->
            Segment(i, list.size, onClick = { onOpen(s.id) }) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .padding(horizontal = CardPadding, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.title ?: stringResource(R.string.past_ad_hoc),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(day(s.startedAt), style = MaterialTheme.typography.bodyMedium.merge(Tnum), color = cs.onSurfaceVariant)
                }
                Text(
                    (listOfNotNull(s.gym) + s.exercises.map { it.name }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().heightIn(min = 16.dp)) }
    }
}

/**
 * Each exercise with its sets, `25 × 10 @ 2`. With [onOpen] a row opens the exercise's table to
 * correct it, and ends in a chevron.
 */
@Composable
internal fun SummaryRows(exercises: List<ExerciseSummary>, onOpen: ((Int) -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    exercises.forEachIndexed { i, e ->
        Segment(i, exercises.size, onClick = onOpen?.let { { it(i) } }) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = CardPadding, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.bodyLarge)
                // Spaces inside a set never break, so a set is never split across lines.
                Text(
                    e.sets.joinToString("   ") { it.summary().replace(' ', ' ') },
                    style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                    color = cs.onSurfaceVariant,
                )
            }
            if (onOpen != null) {
                Spacer(Modifier.width(8.dp))
                SymbolIcon(Symbol.ChevronRight, null, tint = cs.onSurfaceVariant)
            }
        }
        }
    }
}

/** A past session's summary: the day, when and where, then its exercises to open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastSessionScreen(session: PastSession, onOpen: (Long) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val cs = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(session.title ?: stringResource(R.string.past_ad_hoc), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    listOfNotNull(
                        day(session.startedAt),
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.startedAt)),
                        stringResource(R.string.past_minutes, ((session.finishedAt - session.startedAt) / 60_000.0).roundToInt()),
                        session.gym,
                    ).joinToString(" · "),
                    Modifier.padding(horizontal = Margin),
                    style = MaterialTheme.typography.bodyMedium.merge(Tnum),
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.heightIn(min = 16.dp))
                SummaryRows(session.summary.exercises) { i -> onOpen(session.exercises[i].id) }
                Spacer(Modifier.navigationBarsPadding().heightIn(min = 16.dp))
            }
        }
    }
}

/**
 * A past exercise's table (docs/design/logging-screen.md, Session done): what was done, in the
 * logging screen's columns. Tapping a logged set, drop or mini-set loads it into the pad; ✓ saves
 * it, the cancel key and back drop it. The line at the bottom goes back to the summary.
 */
@Composable
fun PastExerciseScreen(session: PastSession, actions: PastSessionActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val shown = session.shown ?: return
    val correction = session.correction
    BackHandler { if (correction != null) actions.cancelCorrection() else onBack() }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = Margin, end = Margin, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(shown.exercise.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SetTable(
                    TableState(
                        shown, entry = null, followUp = null, weightFlagged = false, holdSeconds = null,
                        actions = actions, onSetOptions = {},
                        correction = correction,
                        onCorrect = { set, ext -> actions.correct(shown.id, set, ext) },
                    ),
                    Modifier.weight(1f),
                )
            }
            if (correction != null) {
                val fixing = correction.entry
                val canConfirm = if (correction.kind == null || fixing.field == com.hyperlog.app.domain.Field.Weight) fixing.canConfirm
                else fixing.toExtension(correction.kind) != null
                Pad(fixing, shown.ladder, null, actions, canConfirm = canConfirm, correction = correction, plates = shown.plates, platesOnMachine = shown.platesOnMachine)
            } else {
                BottomRow { NextLine(label = null, name = stringResource(R.string.past_summary), onClick = onBack, symbol = Symbol.ChevronRight, description = stringResource(R.string.past_summary)) }
            }
        }
    }
}
