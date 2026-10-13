package com.hyperlog.app.ui.program

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.hyperlog.app.R
import com.hyperlog.app.program.SetPrescription
import com.hyperlog.app.program.Technique
import com.hyperlog.app.program.TechniqueKind
import com.hyperlog.app.program.planChunks
import com.hyperlog.app.ui.symbols.Symbol
import com.hyperlog.app.ui.symbols.SymbolIcon

/**
 * Each technique kind's symbol and name (docs/design/logging-screen.md, Technique label): the one
 * place the UI maps a kind to how it looks.
 */
enum class TechniqueLook(val kind: TechniqueKind, val symbol: Symbol, @StringRes val label: Int) {
    Dropset(TechniqueKind.Dropset, Symbol.TrendingDown, R.string.technique_dropset_name),
    MyoReps(TechniqueKind.MyoReps, Symbol.Repeat, R.string.technique_myo_name),
    LengthenedPartials(TechniqueKind.LengthenedPartials, Symbol.UnfoldLess, R.string.technique_partials_name),
    IsoHold(TechniqueKind.IsoHold, Symbol.PanTool, R.string.technique_iso_name),
    Failure(TechniqueKind.Failure, Symbol.LocalFireDepartment, R.string.technique_failure_name),
    ;

    companion object {
        fun of(kind: TechniqueKind): TechniqueLook = entries.first { it.kind == kind }
    }
}

/** The symbol a technique is shown with. */
fun Technique.symbol(): Symbol = TechniqueLook.of(kind).symbol

/** The technique's name in the set sheet: `Drop set`, `Myo-reps`… */
@Composable
fun techniqueName(technique: Technique): String = stringResource(TechniqueLook.of(technique.kind).label)

/** `Drop set, 2 drops of 25%`, `Myo-reps, mini-sets of 4`, `To failure`… */
@Composable
fun techniqueText(technique: Technique): String = when (technique) {
    is Technique.Dropset -> if (technique.drops == 1) stringResource(R.string.technique_dropset_one, technique.percent)
    else stringResource(R.string.technique_dropset, technique.drops, technique.percent)
    is Technique.MyoReps -> stringResource(R.string.technique_myo, technique.miniSetReps)
    is Technique.LengthenedPartials -> technique.partials?.let { stringResource(R.string.technique_partials_count, it) }
        ?: stringResource(R.string.technique_partials_failure)
    is Technique.IsoHold -> when (technique.position) {
        com.hyperlog.app.program.HoldPosition.Stretch -> stringResource(R.string.technique_iso_stretch, technique.seconds)
        com.hyperlog.app.program.HoldPosition.Contraction -> stringResource(R.string.technique_iso_contraction, technique.seconds)
    }
    Technique.Failure -> stringResource(R.string.technique_failure)
}

/**
 * Sets summed up in one line: `1 × 4–6 @ 1 · ↘ 2 × 6–8 @ 1`, a back-off run marked with
 * `south_east` and each technique's symbol after its run, in tertiary.
 */
@Composable
fun SetSummary(
    sets: List<SetPrescription>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val chunks = planChunks(sets)
    val symbols = mutableMapOf<String, Symbol>()
    val parts = chunks.map { c ->
        if (c.rir != null) stringResource(R.string.plan_sets_rir, c.count, c.reps.toString(), c.rir)
        else stringResource(R.string.plan_sets, c.count, c.reps.toString())
    }
    val text = buildAnnotatedString {
        chunks.forEachIndexed { i, c ->
            if (i > 0) append("  ·  ")
            if (c.backoff) {
                symbols["backoff"] = Symbol.SouthEast
                appendInlineContent("backoff", "back-off")
                append(" ")
            }
            append(parts[i])
            c.techniques.distinct().forEach { t ->
                val key = t.symbol().name
                symbols[key] = t.symbol()
                append(" ")
                appendInlineContent(key, t.symbol().name)
            }
        }
    }
    val tertiary = MaterialTheme.colorScheme.tertiary
    val inline = symbols.mapValues { (key, symbol) ->
        InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.Center)) {
            SymbolIcon(symbol, null, Modifier.fillMaxSize(), tint = if (key == "backoff") color else tertiary)
        }
    }
    Text(text, modifier, style = style, color = color, inlineContent = inline, maxLines = 2, overflow = TextOverflow.Ellipsis)
}
