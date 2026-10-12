package com.hyperlog.app.ui.coach

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyperlog.app.R
import com.hyperlog.app.coach.Advice
import com.hyperlog.app.coach.Area
import com.hyperlog.app.coach.Change
import com.hyperlog.app.coach.Constraints
import com.hyperlog.app.coach.RuleCoach
import com.hyperlog.app.coach.Situation
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.ui.session.Tnum
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon
import java.time.LocalDate

private val Margin = 24.dp

/** The text column of a situation row: past the 48 dp radio and its 12 dp inset (docs/design/coach.md). */
private val TextIndent = 64.dp

/** The situations, in the order the screen lists them. */
enum class SituationKind { TimeOff, Pain, NoGym, ShortOnTime, PoorRecovery }

/** The minutes Short on time starts at, and its range in 5-minute steps. */
private const val DEFAULT_MINUTES = 45
private val MinutesRange = 20..120

/**
 * The offline coach (SPEC, Coaching → Rules): pick what's in the way and the limits, then
 * Review change opens the Change's diff. When the plan can stand, one line says so.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(
    program: ActiveProgram,
    library: List<Exercise>,
    /** The day of the last session finished; null with none yet. */
    lastTrained: LocalDate?,
    onPropose: (Change) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val daysOff = RuleCoach.daysOff(lastTrained, program.today)
    var kind by rememberSaveable { mutableStateOf(if (RuleCoach.timeOff(daysOff)) SituationKind.TimeOff else null) }
    var area by rememberSaveable { mutableStateOf(Area.Hands) }
    var minutes by rememberSaveable { mutableIntStateOf(DEFAULT_MINUTES) }
    var noGrip by rememberSaveable { mutableStateOf(false) }
    var noAxial by rememberSaveable { mutableStateOf(false) }
    var noOverhead by rememberSaveable { mutableStateOf(false) }
    var home by rememberSaveable { mutableStateOf(false) }
    var capped by rememberSaveable { mutableStateOf(false) }
    /** What the last Review said when it had no Change; cleared by any edit. */
    var said by rememberSaveable { mutableStateOf<String?>(null) }

    fun situation(k: SituationKind) = when (k) {
        SituationKind.TimeOff -> Situation.TimeOff
        SituationKind.Pain -> Situation.Pain(area)
        SituationKind.NoGym -> Situation.NoGym
        SituationKind.ShortOnTime -> Situation.ShortOnTime(minutes)
        SituationKind.PoorRecovery -> Situation.PoorRecovery
    }
    // A situation brings its own limits; they stay editable.
    fun limitsFor(s: Situation) {
        val c = s.constraints()
        noGrip = c.noGrip; noAxial = c.noAxialLoad; noOverhead = c.noOverhead; home = c.home; capped = c.maxMinutes != null
        said = null
    }
    fun pick(k: SituationKind) { kind = k; limitsFor(situation(k)) }

    val resume = stringResource(R.string.coach_resume)
    val nothing = stringResource(R.string.coach_nothing)
    val daysText = daysText(daysOff)
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.coach_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { SymbolIcon(Symbol.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(start = Margin, end = 16.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        said.orEmpty(),
                        Modifier.weight(1f).padding(end = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        enabled = kind != null,
                        onClick = {
                            val k = kind ?: return@Button
                            val constraints = Constraints(noGrip, noAxial, noOverhead, home, minutes.takeIf { capped })
                            when (val advice = RuleCoach.advise(situation(k), constraints, program, library, lastTrained)) {
                                is Advice.Propose -> { said = null; onPropose(advice.change) }
                                is Advice.Resume -> said = listOfNotNull(daysText.takeIf { advice.daysOff != null }, resume).joinToString(". ")
                                Advice.NothingToChange -> said = nothing
                            }
                        },
                    ) { Text(stringResource(R.string.coach_review)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.coach_question),
                Modifier.padding(horizontal = Margin).padding(top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            Column(Modifier.selectableGroup()) {
                SituationRow(stringResource(R.string.coach_time_off), daysText, kind == SituationKind.TimeOff) { pick(SituationKind.TimeOff) }
                SituationRow(stringResource(R.string.coach_pain), null, kind == SituationKind.Pain) { pick(SituationKind.Pain) }
                if (kind == SituationKind.Pain) {
                    FlowRow(
                        Modifier.padding(start = TextIndent, end = Margin),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Area.entries.forEach { a ->
                            FilterChip(
                                selected = a == area,
                                onClick = { area = a; limitsFor(Situation.Pain(a)) },
                                label = { Text(a.label) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.coach_pain_body),
                        Modifier.padding(start = TextIndent, end = Margin, top = 4.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SituationRow(stringResource(R.string.coach_no_gym), stringResource(R.string.coach_no_gym_body), kind == SituationKind.NoGym) { pick(SituationKind.NoGym) }
                SituationRow(stringResource(R.string.coach_short), null, kind == SituationKind.ShortOnTime) { pick(SituationKind.ShortOnTime) }
                if (kind == SituationKind.ShortOnTime || capped) {
                    MinutesStepper(minutes, onChange = { minutes = it; said = null }, Modifier.padding(start = TextIndent - 12.dp))
                }
                SituationRow(stringResource(R.string.coach_recovery), stringResource(R.string.coach_recovery_body), kind == SituationKind.PoorRecovery) { pick(SituationKind.PoorRecovery) }
            }
            Text(
                stringResource(R.string.coach_limits),
                Modifier.padding(horizontal = Margin).padding(top = 24.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(Modifier.padding(horizontal = Margin), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Limit(stringResource(R.string.coach_no_grip), noGrip) { noGrip = it; said = null }
                Limit(stringResource(R.string.coach_no_axial), noAxial) { noAxial = it; said = null }
                Limit(stringResource(R.string.coach_no_overhead), noOverhead) { noOverhead = it; said = null }
                Limit(stringResource(R.string.coach_home), home) { home = it; said = null }
                Limit(stringResource(R.string.coach_max_minutes, minutes), capped) { capped = it; said = null }
            }
        }
    }
}

@Composable
private fun daysText(days: Int?): String = when (days) {
    null -> stringResource(R.string.coach_no_sessions)
    1 -> stringResource(R.string.coach_day_off)
    else -> stringResource(R.string.coach_days_off, days)
}

/** One situation: a radio row, as the gym sheet's, with a line under the name when it has one. */
@Composable
private fun SituationRow(name: String, body: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = 12.dp, end = Margin, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, Modifier.size(48.dp))
        Spacer(Modifier.width(4.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Limit(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = on,
        onClick = { onChange(!on) },
        label = { Text(label) },
        leadingIcon = if (on) { { SymbolIcon(Symbol.Check, null, Modifier.size(18.dp)) } } else null,
    )
}

/** `−  45 min  +`, in 5-minute steps. */
@Composable
private fun MinutesStepper(minutes: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((minutes - 5).coerceIn(MinutesRange)) }, enabled = minutes > MinutesRange.first) {
            SymbolIcon(Symbol.Remove, stringResource(R.string.coach_less_time))
        }
        Text(
            stringResource(R.string.coach_minutes, minutes),
            Modifier.widthIn(min = 64.dp),
            style = MaterialTheme.typography.titleMedium.merge(Tnum),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onChange((minutes + 5).coerceIn(MinutesRange)) }, enabled = minutes < MinutesRange.last) {
            SymbolIcon(Symbol.Add, stringResource(R.string.coach_more_time))
        }
    }
}
