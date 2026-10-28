package com.hyperlog.app.ui.program

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.domain.formatKg
import com.hyperlog.app.program.BlockReviews
import com.hyperlog.app.program.GoalPace
import com.hyperlog.app.program.GoalStanding
import com.hyperlog.app.program.LiftChange
import com.hyperlog.app.program.Review
import com.hyperlog.app.program.ReviewPage
import com.hyperlog.app.program.TurningUp
import com.hyperlog.app.progression.Record
import com.hyperlog.app.ui.coach.shortDate
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import com.hyperlog.app.ui.theme.BottomActions
import com.hyperlog.app.ui.theme.GroupHead
import com.hyperlog.app.ui.theme.SectionCard
import com.hyperlog.app.ui.theme.SegmentedList
import com.hyperlog.app.ui.theme.SegmentedListScope
import com.hyperlog.app.ui.theme.Tones
import com.hyperlog.app.ui.theme.hyperlogTopBarColors
import com.hyperlog.app.ui.theme.interFigures

/**
 * A finished block's review (SPEC, Program → Block review): its weeks and dates, then Lifts,
 * Turning up, Volume, Records, Bodyweight, Goal and Next, each a plain list, and Check in about
 * the next block. The program review ([ReviewPage.whole]) adds a column for the whole program and
 * New program. Empty sections are left out, Lifts aside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockReviewScreen(
    page: ReviewPage,
    names: Map<String, String>,
    onBack: () -> Unit,
    /** Check in, with its first answer and the review for the check-in's context. */
    onCheckIn: (opening: String, context: String) -> Unit,
    /** New program on the program review, with its first answer and the review for the check-in's context. */
    onNewProgram: (opening: String, context: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val block = page.block
    val review = page.review
    val whole = page.whole
    val name = { id: String -> names[id] ?: id }
    val context = { BlockReviews.context(page.program, block, review, whole, names) }
    Scaffold(
        modifier = modifier,
        containerColor = Tones.screen,
        topBar = {
            TopAppBar(
                colors = hyperlogTopBarColors(),
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = {
            BottomActions {
                Spacer(Modifier.weight(1f))
                if (whole != null) {
                    OutlinedButton(onClick = { onNewProgram(BlockReviews.opening(page.program, block, newProgram = true), context()) }) {
                        Text(stringResource(R.string.review_new_program), maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = { onCheckIn(BlockReviews.opening(page.program, block), context()) }) {
                    Text(stringResource(if (page.next != null) R.string.review_check_in_next else R.string.review_check_in), maxLines = 1)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    interFigures(stringResource(R.string.review_title, if (whole != null) page.program.title else block.name)),
                    style = MaterialTheme.typography.headlineMedium,
                )
                val weeks = if (block.weekStart == block.weekEnd) stringResource(R.string.review_week, block.weekStart)
                else stringResource(R.string.review_weeks, block.weekStart, block.weekEnd)
                val dates = review.dates?.let { stringResource(R.string.review_dates, shortDate(it.start), shortDate(it.endInclusive)) }
                Text(
                    interFigures(listOfNotNull(block.name.takeIf { whole != null }, weeks, dates).joinToString(" · ")),
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (whole != null) Columns(block.name, stringResource(R.string.review_program))
            Lifts(review, whole, name)
            TurningUpSection(review.turningUp, whole?.turningUp, whole != null)
            Volume(review, whole)
            Records(review, whole, name)
            Bodyweight(review.bodyweight, whole?.bodyweight)
            Goals(review.goals, whole?.goals, name)
            page.next?.let { next ->
                val comparison = remember(page.program, next) { ProgramWeek.comparison(page.program, next) }
                if (comparison != null) {
                    GroupHead(stringResource(R.string.review_next))
                    SectionCard { ComparisonLines(next, comparison, names) }
                }
            }
        }
    }
}

/** The two columns' heads, over the values on the right: the block and the program. */
@Composable
private fun Columns(block: String, program: String) {
    // Over the values: the card's margin and its row's padding in from the edge.
    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 16.dp)) {
        Spacer(Modifier.weight(1f))
        listOf(block, program).forEach {
            Text(it, Modifier.width(ColumnWidth), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1)
        }
    }
}

/**
 * A row: [title] with [supporting] under it, then [value] on the right, or on the program review
 * [value] and [program] in their columns.
 */
private fun SegmentedListScope.valueRow(title: String, supporting: String?, value: String?, program: String? = null, columns: Boolean = false, valueColor: Color? = null, programColor: Color? = null) =
    item {
        val cs = MaterialTheme.colorScheme
        Row(Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                supporting?.let { Text(interFigures(it), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
            }
            if (columns) {
                Text(interFigures(value ?: "–"), Modifier.width(ColumnWidth), style = MaterialTheme.typography.bodyLarge, color = valueColor ?: cs.onSurface, textAlign = TextAlign.End)
                Text(interFigures(program ?: "–"), Modifier.width(ColumnWidth), style = MaterialTheme.typography.bodyLarge, color = programColor ?: cs.onSurface, textAlign = TextAlign.End)
            } else if (value != null) {
                Spacer(Modifier.width(16.dp))
                Text(interFigures(value), style = MaterialTheme.typography.bodyLarge, color = valueColor ?: cs.onSurface, textAlign = TextAlign.End)
            }
        }
    }

/** `+6`, `±0`, `−2.5`. */
private fun gain(lift: LiftChange): String = BlockReviews.signedKg(lift.gainKg)

/** A gain in the accent; a stall and a drop muted. */
@Composable
private fun gainColor(lift: LiftChange?): Color? =
    lift?.let { if (it.gainKg >= BlockReviews.SAME_KG) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant }

/**
 * Each lift: its name, its machine under it, and its estimate before → after with the gain. On
 * the program review the gains sit in the two columns, the block's estimates under the name.
 */
@Composable
private fun Lifts(review: Review, whole: Review?, name: (String) -> String) {
    GroupHead(stringResource(R.string.review_lifts))
    if (review.lifts.isEmpty() && whole?.lifts.isNullOrEmpty()) {
        val none = stringResource(R.string.review_no_lifts)
        SegmentedList { row(none) }
        return
    }
    val context = LocalContext.current
    val estimates = { l: LiftChange -> context.getString(R.string.review_estimate_kg, formatKg(l.startKg), formatKg(l.endKg)) }
    if (whole == null) {
        // Read before the list: its builder isn't composable.
        val rows = review.lifts.map { l -> Triple(l, estimates(l), gainColor(l)) }
        SegmentedList {
            rows.forEach { (l, kg, color) ->
                item {
                    val cs = MaterialTheme.colorScheme
                    Row(Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name(l.exercise), style = MaterialTheme.typography.bodyLarge)
                            l.machine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(interFigures(kg), style = MaterialTheme.typography.bodyLarge)
                            Text(interFigures(gain(l)), style = MaterialTheme.typography.titleSmall, color = color ?: cs.onSurface)
                        }
                    }
                }
            }
        }
        return
    }
    // One row a lift, the block's first in its order, then those only the program has; each column
    // reads its own machine used most.
    val rows = (review.lifts + whole.lifts).map { it.exercise }.distinct().map { exercise ->
        val b = review.lifts.firstOrNull { it.exercise == exercise }
        val p = whole.lifts.firstOrNull { it.exercise == exercise }
        ColumnLift(name(exercise), listOfNotNull((b ?: p)?.machine, b?.let(estimates)).joinToString(" · "), b, p, gainColor(b), gainColor(p))
    }
    SegmentedList {
        rows.forEach { r -> valueRow(r.name, r.under.ifEmpty { null }, r.block?.let(::gain), r.program?.let(::gain), columns = true, valueColor = r.blockColor, programColor = r.programColor) }
    }
}

/** A lift's row on the program review: its [name], what's [under] it, and its change in the [block] and the [program]. */
private data class ColumnLift(val name: String, val under: String, val block: LiftChange?, val program: LiftChange?, val blockColor: Color?, val programColor: Color?)

/** The training days trained and the weeks taken, or without training days the days done and their dates. */
@Composable
private fun TurningUpSection(block: TurningUp?, program: TurningUp?, columns: Boolean) {
    block ?: return
    GroupHead(stringResource(R.string.review_turning_up))
    when (block) {
        is TurningUp.OnTrainingDays -> {
            val p = program as? TurningUp.OnTrainingDays
            val missed = block.missed.map { (day, n) ->
                when (n) {
                    1 -> stringResource(R.string.review_missed_once, day)
                    2 -> stringResource(R.string.review_missed_twice, day)
                    else -> stringResource(R.string.review_missed_times, day, n)
                }
            }.joinToString(" · ").ifEmpty { null }
            val extra = (block.again.map { stringResource(R.string.review_week_again, it) } + List(block.easyWeeks) { stringResource(R.string.review_easy_week) })
                .joinToString(" · ").ifEmpty { null }
            val showWeeks = block.weeks != block.written || extra != null || (p != null && p.weeks != p.written)
            if (columns) {
                val days = stringResource(R.string.review_training_days)
                val done = stringResource(R.string.review_n_of, block.done, block.of) to p?.let { stringResource(R.string.review_n_of, it.done, it.of) }
                val weeksTitle = stringResource(R.string.review_weeks_taken)
                val weeks = stringResource(R.string.review_n_planned, block.weeks, block.written) to p?.let { stringResource(R.string.review_n_planned, it.weeks, it.written) }
                SegmentedList {
                    valueRow(days, missed, done.first, done.second, columns = true)
                    if (showWeeks) valueRow(weeksTitle, extra, weeks.first, weeks.second, columns = true)
                }
            } else {
                val done = stringResource(R.string.review_done_of, block.done, block.of)
                val weeks = stringResource(R.string.review_took, block.weeks, block.written)
                SegmentedList {
                    row(done, missed)
                    if (showWeeks) row(weeks, extra)
                }
            }
        }
        is TurningUp.InSequence -> {
            val days = stringResource(R.string.review_days, block.days)
            val dates = stringResource(R.string.review_dates, shortDate(block.from), shortDate(block.to))
            val p = program as? TurningUp.InSequence
            SegmentedList {
                if (columns) valueRow(days, dates, "${block.days}", p?.let { "${it.days}" }, columns = true)
                else row(days, dates)
            }
        }
    }
}

/** Average weekly sets per muscle, done → as written, then the Changes accepted in the block. */
@Composable
private fun Volume(review: Review, whole: Review?) {
    if (review.volume.isEmpty() && review.changes.isEmpty() && whole?.volume.isNullOrEmpty()) return
    GroupHead(stringResource(R.string.review_volume))
    val context = LocalContext.current
    val sets = { d: Double, w: Double -> context.getString(R.string.review_sets, BlockReviews.formatSets(d), BlockReviews.formatSets(w)) }
    // On the program review a muscle only the program worked has its row too.
    val muscles = (review.volume.map { it.muscle } + whole?.volume.orEmpty().map { it.muscle }).distinct()
    val rows = muscles.map { m ->
        val b = review.volume.firstOrNull { it.muscle == m }
        val p = whole?.volume?.firstOrNull { it.muscle == m }
        Triple(
            m,
            b?.let { sets(it.done, it.written) } to b?.let { context.getString(R.string.review_sets_a_week, BlockReviews.formatSets(it.done), BlockReviews.formatSets(it.written)) },
            p?.let { sets(it.done, it.written) },
        )
    }
    val changes = review.changes.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.review_changes, it.size, it.size, it.joinToString(", ")) }
    SegmentedList {
        rows.forEach { (muscle, sets, program) ->
            if (whole != null) valueRow(muscle, null, sets.first, program, columns = true)
            else valueRow(muscle, null, sets.second)
        }
        changes?.let { row(it, supportingLines = 3) }
    }
}

/** The records set in the block, each its top set and date; on the program review, the count of each first. */
@Composable
private fun Records(review: Review, whole: Review?, name: (String) -> String) {
    if (review.records.isEmpty() && whole?.records.isNullOrEmpty()) return
    GroupHead(stringResource(R.string.review_records))
    val rows = review.records.map { r ->
        Triple(
            name(r.exercise),
            listOfNotNull(r.machine, stringResource(R.string.review_record_set, formatKg(r.record.weightKg), r.record.reps, shortDate(r.date))).joinToString(" · "),
            stringResource(if (r.record.kind == Record.Estimate) R.string.review_record_estimate else R.string.review_record_reps),
        )
    }
    val count = stringResource(R.string.review_records_set)
    SegmentedList {
        if (whole != null) valueRow(count, null, "${review.records.size}", "${whole.records.size}", columns = true)
        rows.forEach { (title, set, kind) -> item { RecordRow(title, set, kind) } }
    }
}

@Composable
private fun ColumnScope.RecordRow(title: String, set: String, kind: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(interFigures(set), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Text(kind, style = MaterialTheme.typography.labelLarge, color = cs.primary)
    }
}

/** Bodyweight start → end, with the program's under it on the program review. */
@Composable
private fun Bodyweight(block: Pair<Double, Double>?, program: Pair<Double, Double>?) {
    if (block == null && program == null) return
    GroupHead(stringResource(R.string.review_bodyweight))
    val context = LocalContext.current
    val kg = { (from, to): Pair<Double, Double> -> context.getString(R.string.review_estimate_kg, formatKg(from), formatKg(to)) }
    val programLine = program?.let { stringResource(R.string.review_program_line, kg(it)) }
    // Logged only in earlier blocks, the program's line is all there is.
    val title = block?.let(kg) ?: programLine!!
    val under = programLine.takeIf { block != null }
    SegmentedList { row(title, under) }
}

/** Each lift goal: the estimate against it, and with a date the pace. */
@Composable
private fun Goals(goals: List<GoalStanding>, program: List<GoalStanding>?, name: (String) -> String) {
    if (goals.isEmpty()) return
    GroupHead(stringResource(R.string.review_goal))
    val rows = goals.map { g ->
        val of = g.goal.by?.let { stringResource(R.string.review_goal_of_by, formatKg(g.estimateKg), formatKg(g.goal.kg), shortDate(it)) }
            ?: stringResource(R.string.review_goal_of, formatKg(g.estimateKg), formatKg(g.goal.kg))
        val lines = listOfNotNull(of, pace(g)) +
            listOfNotNull(program?.firstOrNull { it.goal == g.goal }?.let { p -> pace(p)?.let { stringResource(R.string.review_program_line, it) } })
        name(g.goal.exerciseId) to lines
    }
    SegmentedList {
        rows.forEach { (title, lines) ->
            item {
                Column(Modifier.fillMaxWidth().heightIn(min = RowHeight).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    lines.forEach { Text(interFigures(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** `On pace for 2 Sep`, `4 kg short at this pace`, `Reached`; null without a date. */
@Composable
private fun pace(g: GoalStanding): String? = when (val p = g.pace) {
    is GoalPace.OnPace -> stringResource(R.string.review_goal_on_pace, shortDate(p.date))
    is GoalPace.Short -> stringResource(R.string.review_goal_short, formatKg(BlockReviews.roundHalf(p.kg)))
    null -> if (g.estimateKg >= g.goal.kg) stringResource(R.string.review_goal_reached) else null
}

private val ColumnWidth = 80.dp

/** A row's height at least, as the standard row's. */
private val RowHeight = 72.dp
