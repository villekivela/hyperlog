package com.hyperlog.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.hyperlog.app.R
import com.hyperlog.app.domain.LoggedSet
import com.hyperlog.app.progression.Record
import com.hyperlog.app.session.ExerciseSummary
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

// Records (SPEC, Records): a record set's mark wherever the set shows, and the session's records
// listed on Day done and Session done. Set text carries a record as one private-use character,
// which RirText draws as the mark and TalkBack reads as its name.

private const val EstimateMark = ''
private const val RepsMark = ''

private val Record.mark: Char get() = if (this == Record.Estimate) EstimateMark else RepsMark

internal fun Char.record(): Record? = when (this) {
    EstimateMark -> Record.Estimate
    RepsMark -> Record.Reps
    else -> null
}

internal fun Record.inlineId() = "record-$name"

/** A logged set as the screen writes it ([setText]), followed by its record's mark. */
internal fun LoggedSet.setText(record: Record?): String = setText() + (record?.let { " ${it.mark}" } ?: "")

/** `Estimate record`, `Rep record`. */
@Composable
internal fun Record.label(): String = stringResource(if (this == Record.Estimate) R.string.record_estimate else R.string.record_reps)

/**
 * The record's mark: a trophy in ink. A record is neither something to act on nor a selection, so
 * it never takes the accent, and green, amber, red and blue belong to RIR, progression and plates.
 */
@Composable
internal fun RecordMark(record: Record, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    SymbolIcon(Symbol.Trophy, record.label(), modifier, tint = tint)
}

/** The inline marks for set text, sized to its text and drawn in its [tint]. */
@Composable
internal fun recordMarkContent(tint: Color): Map<String, InlineTextContent> = Record.entries.associate { record ->
    record.inlineId() to InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
        // Drawing only: the line's alternate text already reads the record's name.
        RecordMark(record, Modifier.fillMaxSize().clearAndSetSemantics { }, tint = tint)
    }
}

/** One record of a session: `Barbell bench press · 92.5 × 5 [1] · Estimate record`. */
internal data class SessionRecord(val exercise: String, val set: LoggedSet, val record: Record)

/** The records [exercises] made, in session order. */
internal fun recordsOf(exercises: List<ExerciseSummary>): List<SessionRecord> = exercises.flatMap { e ->
    e.sets.mapIndexedNotNull { i, set -> e.records.getOrNull(i)?.let { SessionRecord(e.name, set, it) } }
}

/**
 * The session's records, one a row each, as the exercise summaries read: the trophy, the exercise,
 * and under it the set and the record's name. TalkBack reads a row as one line,
 * `Barbell Bench Press · 92.5 × 5 1 RIR · Estimate record`.
 */
@Composable
internal fun RecordLines(
    records: List<SessionRecord>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    muted: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tint: Color = muted,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        records.forEach { r ->
            val detail = "${r.set.setText()} · ${r.record.label()}"
            // Read as one line, with each chip as "N RIR" as set text reads it.
            val line = buildAnnotatedString { appendRir("${r.exercise} · $detail", target = false, chips = mutableSetOf()) }.text
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { text = AnnotatedString(line) }, verticalAlignment = Alignment.Top) {
                // 24 dp on the exercise's 24 sp line, in the column Day done's check uses.
                RecordMark(r.record, Modifier.size(24.dp), tint = tint)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(r.exercise, style = MaterialTheme.typography.bodyLarge, color = color)
                    RirText(detail, style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
        }
    }
}
