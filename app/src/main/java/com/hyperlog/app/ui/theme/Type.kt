package com.hyperlog.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hyperlog.app.R

/**
 * Space Grotesk (SIL OFL 1.1, docs/licences/SpaceGrotesk-OFL.txt), bundled as static SemiBold and
 * Bold. It sets display, headline and titleLarge text: screen titles, the day's name and exercise
 * names.
 */
internal val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/**
 * Inter (SIL OFL 1.1, docs/licences/Inter-OFL.txt), bundled as static Regular, Medium, SemiBold and
 * Bold. It sets everything Space Grotesk doesn't: body, labels, the smaller titles, and the numbers,
 * the set table's kg and reps included.
 */
internal val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Tabular figures in every style, so weights, reps, dates and timers keep their columns. */
internal const val TabularFigures = "tnum"

/** Space Grotesk runs a touch smaller than Roboto at the same size, so its sizes are scaled to match. */
private const val DisplayScale = 1.02f

private fun TextStyle.inter(weight: FontWeight? = fontWeight) =
    copy(fontFamily = Inter, fontWeight = weight, fontFeatureSettings = TabularFigures)

private fun TextStyle.display(weight: FontWeight = FontWeight.SemiBold) = copy(
    fontFamily = SpaceGrotesk,
    fontWeight = weight,
    letterSpacing = (-0.01).em,
    fontSize = fontSize * DisplayScale,
    lineHeight = lineHeight * DisplayScale,
    fontFeatureSettings = TabularFigures,
)

/**
 * Material's type scale with Space Grotesk for the large titles, Inter for the rest (titleMedium
 * and titleSmall SemiBold, Bold emphasized) and tabular figures throughout, the expressive emphasized styles
 * included (Bold where the face is Space Grotesk).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val HyperlogTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.display(FontWeight.Bold),
        displayMedium = displayMedium.display(FontWeight.Bold),
        displaySmall = displaySmall.display(FontWeight.Bold),
        headlineLarge = headlineLarge.display(),
        headlineMedium = headlineMedium.display(),
        headlineSmall = headlineSmall.display(),
        titleLarge = titleLarge.display(),
        titleMedium = titleMedium.inter(FontWeight.SemiBold),
        titleSmall = titleSmall.inter(FontWeight.SemiBold),
        bodyLarge = bodyLarge.inter(),
        bodyMedium = bodyMedium.inter(),
        bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter(),
        labelMedium = labelMedium.inter(),
        labelSmall = labelSmall.inter(),
        displayLargeEmphasized = displayLargeEmphasized.display(FontWeight.Bold),
        displayMediumEmphasized = displayMediumEmphasized.display(FontWeight.Bold),
        displaySmallEmphasized = displaySmallEmphasized.display(FontWeight.Bold),
        headlineLargeEmphasized = headlineLargeEmphasized.display(FontWeight.Bold),
        headlineMediumEmphasized = headlineMediumEmphasized.display(FontWeight.Bold),
        headlineSmallEmphasized = headlineSmallEmphasized.display(FontWeight.Bold),
        titleLargeEmphasized = titleLargeEmphasized.display(FontWeight.Bold),
        titleMediumEmphasized = titleMediumEmphasized.inter(FontWeight.Bold),
        titleSmallEmphasized = titleSmallEmphasized.inter(FontWeight.Bold),
        bodyLargeEmphasized = bodyLargeEmphasized.inter(),
        bodyMediumEmphasized = bodyMediumEmphasized.inter(),
        bodySmallEmphasized = bodySmallEmphasized.inter(),
        labelLargeEmphasized = labelLargeEmphasized.inter(),
        labelMediumEmphasized = labelMediumEmphasized.inter(),
        labelSmallEmphasized = labelSmallEmphasized.inter(),
    )
}

/**
 * A set table's kg or reps: Inter Medium at titleLarge's size. Untracked, since titleLarge's
 * tracking is Space Grotesk's and titleMedium's would loosen the digits.
 */
internal val Typography.figureLarge: TextStyle get() = titleLarge.figure()

/** A long kg or reps value (`100 + 20`), a step down: Inter Medium at titleMedium's size. */
internal val Typography.figureMedium: TextStyle get() = titleMedium.figure()

private fun TextStyle.figure() = inter(FontWeight.Medium).copy(letterSpacing = 0.sp)
