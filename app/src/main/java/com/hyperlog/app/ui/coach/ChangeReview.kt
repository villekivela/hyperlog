package com.hyperlog.app.ui.coach

import androidx.compose.runtime.setValue
import com.hyperlog.app.ui.session.RirText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.ChangeDiff
import com.hyperlog.app.coach.DayDiff
import com.hyperlog.app.coach.Scope
import com.hyperlog.app.coach.SlotDiff
import com.hyperlog.app.program.Position
import com.hyperlog.app.program.Program
import com.hyperlog.app.ui.program.SetSummary
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.CardMargin
import com.hyperlog.app.ui.theme.CardPadding
import com.hyperlog.app.ui.theme.CardShape
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.SectionCard
import com.hyperlog.app.ui.theme.Segment
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.itemsIndexed
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Margin = 24.dp
private val LabelColumn = 40.dp
private val HeadColumn = 56.dp

/** A Change waiting for accept or reject: what it is, and its diff against the plan. */
data class ChangeReview(
    val change: Change,
    val diff: ChangeDiff,
    val program: Program,
    /** The block a `rest_of_block` Change keeps to. */
    val blockName: String?,
    /** The summaries of the Changes in force it ends (its `replaces`). */
    val ends: List<String> = emptyList(),
)

internal const val ChangeDiffTag = "change-diff"

/**
 * A Change shown as a diff against the current plan (docs/design/changes.md): the summary and
 * its period, where the next day moves, then each day it edits, slot by slot. Nothing changes
 * until Accept; Reject, × and Back drop it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeReviewScreen(
    review: ChangeReview,
    exerciseNames: Map<String, String>,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * A Change in force, opened from its card on Coach: read-only, headed "Change in force", with
     * Revert (asking first) in place of Accept; × and Back close it ([onReject]).
     */
    onRevert: (() -> Unit)? = null,
) {
    BackHandler(onBack = onReject)
    var reverting by rememberSaveable { mutableStateOf(false) }
    if (reverting && onRevert != null) {
        AlertDialog(
            onDismissRequest = { reverting = false },
            title = { Text(stringResource(R.string.program_revert_title, review.change.summary)) },
            text = { Text(stringResource(R.string.program_revert_body)) },
            confirmButton = { TextButton(onClick = { reverting = false; onRevert() }) { Text(stringResource(R.string.program_change_revert)) } },
            dismissButton = { TextButton(onClick = { reverting = false }) { Text(stringResource(R.string.program_cancel)) } },
        )
    }
    val cs = MaterialTheme.colorScheme
    val name = { id: String -> exerciseNames[id] ?: id }
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = { Text(stringResource(if (onRevert != null) R.string.change_in_force_title else R.string.change_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onReject) {
                        SymbolIcon(Symbol.Close, stringResource(if (onRevert != null) R.string.change_close else R.string.change_reject))
                    }
                },
            )
        },
        bottomBar = {
            BottomActions(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Spacer(Modifier.weight(1f))
                if (onRevert != null) {
                    OutlinedButton(onClick = { reverting = true }) { Text(stringResource(R.string.program_change_revert)) }
                } else {
                    TextButton(onClick = onReject) { Text(stringResource(R.string.change_reject)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onAccept) { Text(stringResource(R.string.change_accept)) }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag(ChangeDiffTag)) {
            // What it ends goes first: those stop before the new one applies.
            if (review.ends.isNotEmpty()) item {
                SectionCard(Modifier.padding(top = 8.dp), color = cs.tertiaryContainer) {
                    review.ends.forEach {
                        Text(
                            stringResource(R.string.change_ends, it),
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onTertiaryContainer,
                        )
                    }
                }
            }
            // What the Change is, and where it moves the program, in one card at the top.
            item {
                SectionCard(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                    RirText(review.change.summary, target = true, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(scopeText(review.change.scope, review.blockName), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    review.diff.next?.let { (from, to) ->
                        Spacer(Modifier.height(8.dp))
                        PositionRows(review.program, from, to, review.diff.daysLeft)
                    }
                }
            }
            if (review.diff.next == null && review.diff.days.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.change_nothing),
                        Modifier.padding(horizontal = Margin, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            review.diff.days.forEach { day ->
                item(key = "day-${day.day.position.week}-${day.day.position.day}") { DayHead(day) }
                itemsIndexed(day.slots, key = { _, it -> "slot-${day.day.position.week}-${day.day.position.day}-${(it.after ?: it.before)!!.id}" }) { i, slot ->
                    Segment(i, day.slots.size) { SlotRow(slot, name) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** `Rest of the program`, `Rest of Build`, `13 Oct – 19 Oct`. */
@Composable
fun scopeText(scope: Scope, blockName: String?): String = when (scope) {
    Scope.RestOfProgram -> stringResource(R.string.change_scope_program)
    Scope.RestOfBlock -> stringResource(R.string.change_scope_block, blockName.orEmpty())
    is Scope.Dates -> if (scope.from == scope.to) shortDate(scope.from)
    else stringResource(R.string.change_scope_dates, shortDate(scope.from), shortDate(scope.to))
}

private val DayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun shortDate(date: LocalDate): String = DayMonth.format(date)

/** Where the next day moves, and how many days are left, before → after. */
@Composable
private fun PositionRows(program: Program, from: Position, to: Position, left: Pair<Int, Int>?) {
    Column {
        HeadRow(stringResource(R.string.change_next), dayName(program, from), dayName(program, to))
        left?.let { (a, b) ->
            HeadRow(
                stringResource(R.string.change_left),
                pluralStringResource(R.plurals.change_days_left, a, a),
                pluralStringResource(R.plurals.change_days_left, b, b),
            )
        }
    }
}

@Composable
private fun dayName(program: Program, p: Position): String {
    val day = if (p.week > program.weeks) null else program.plannedDay(p)
    // The short name: "Day B", not its title after the dash, so before → after fits a line.
    return day?.let { stringResource(R.string.change_day_head, p.week, it.day.name.substringBefore(" — ")) } ?: stringResource(R.string.change_finished)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeadRow(label: String, before: String, after: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 8.dp)) {
        Text(label, Modifier.width(HeadColumn).alignByBaseline(), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        FlowRow(Modifier.weight(1f).alignByBaseline(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(before, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            Arrow()
            Text(after, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Arrow() {
    SymbolIcon(
        Symbol.ArrowForward, null,
        Modifier.padding(horizontal = 8.dp).size(20.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun DayHead(day: DayDiff) {
    val cs = MaterialTheme.colorScheme
    val head = stringResource(R.string.change_day_head, day.day.position.week, day.day.day.name)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Margin).padding(top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            head,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = cs.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (day.day.deload) {
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.change_deload), style = MaterialTheme.typography.labelLarge, color = cs.tertiary, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * One slot: its label, then the exercise (in place of the one before, or struck through when
 * removed) and its sets, before → after when they change, and the load when it moves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotRow(slot: SlotDiff, name: (String) -> String) {
    val cs = MaterialTheme.colorScheme
    val before = slot.before
    val after = slot.after
    val shown = after ?: before!!
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = CardPadding, vertical = 12.dp)) {
        Text(
            shown.label,
            Modifier.width(LabelColumn).alignByBaseline(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (before == null) cs.primary else cs.onSurfaceVariant,
            fontWeight = if (before == null) FontWeight.Bold else FontWeight.Normal,
        )
        Column(Modifier.weight(1f).alignByBaseline()) {
            val swapped = before != null && after != null && before.exercise != after.exercise
            if (swapped) {
                Text(
                    stringResource(R.string.edit_swapped_from, name(before!!.exercise)),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Text(
                name(shown.exercise),
                style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (after == null) TextDecoration.LineThrough else null),
                color = if (after == null) cs.onSurfaceVariant else cs.onSurface,
            )
            when {
                after == null -> Text(stringResource(R.string.change_removed), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                before == null -> SetSummary(after.sets)
                before.sets != after.sets -> FlowRow {
                    SetSummary(
                        before.sets,
                        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                    )
                    Arrow()
                    SetSummary(after.sets, color = cs.onSurface)
                }
                else -> SetSummary(after.sets)
            }
            if (after != null && after.loadPercent != (before?.loadPercent ?: 0)) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SymbolIcon(
                        if (after.loadPercent < 0) Symbol.TrendingDown else Symbol.TrendingUp, null,
                        Modifier.size(20.dp), tint = cs.tertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.change_load, percent(after.loadPercent)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.tertiary,
                    )
                }
            }
        }
    }
}

/** `−10%`, `+5%`, with a real minus sign. */
fun percent(p: Int): String = if (p < 0) "−${-p}%" else "+$p%"
