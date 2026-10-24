package com.hyperlog.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
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

/** The record's mark: a trophy in the accent. */
@Composable
internal fun RecordMark(record: Record, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    SymbolIcon(Symbol.Trophy, record.label(), modifier, tint = tint)
}

/** The inline marks for set text, sized to its text. */
@Composable
internal fun recordMarkContent(): Map<String, InlineTextContent> = Record.entries.associate { record ->
    record.inlineId() to InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
        // Drawing only: the line's alternate text already reads the record's name.
        RecordMark(record, Modifier.fillMaxSize().clearAndSetSemantics { })
    }
}

/** One record of a session: `Barbell bench press · 92.5 × 5 [1] · Estimate record`. */
internal data class SessionRecord(val exercise: String, val set: LoggedSet, val record: Record)

/** The records [exercises] made, in session order. */
internal fun recordsOf(exercises: List<ExerciseSummary>): List<SessionRecord> = exercises.flatMap { e ->
    e.sets.mapIndexedNotNull { i, set -> e.records.getOrNull(i)?.let { SessionRecord(e.name, set, it) } }
}

/** The session's records, one a line, each after its mark. */
@Composable
internal fun RecordLines(records: List<SessionRecord>, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified, tint: Color = MaterialTheme.colorScheme.primary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        records.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordMark(r.record, Modifier.size(20.dp).clearAndSetSemantics { }, tint = tint)
                Spacer(Modifier.width(12.dp))
                RirText("${r.exercise} · ${r.set.setText()} · ${r.record.label()}", style = style, color = color)
            }
        }
    }
}
