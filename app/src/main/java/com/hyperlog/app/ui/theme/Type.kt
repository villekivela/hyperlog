package com.hyperlog.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.hyperlog.app.R

/**
 * Space Grotesk (SIL OFL 1.1, docs/licences/SpaceGrotesk-OFL.txt), bundled as static SemiBold and
 * Bold. It sets display, headline and titleLarge text: screen titles, the day's name, exercise
 * names, and the kg and reps in the set table. Everything else is the system font.
 */
internal val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** Tabular figures in every style, so weights, reps, dates and timers keep their columns. */
internal const val TabularFigures = "tnum"

/** Space Grotesk runs a touch smaller than Roboto at the same size, so its sizes are scaled to match. */
private const val DisplayScale = 1.02f

private fun TextStyle.tabular() = copy(fontFeatureSettings = TabularFigures)

private fun TextStyle.display(weight: FontWeight = FontWeight.SemiBold) = copy(
    fontFamily = SpaceGrotesk,
    fontWeight = weight,
    letterSpacing = (-0.01).em,
    fontSize = fontSize * DisplayScale,
    lineHeight = lineHeight * DisplayScale,
    fontFeatureSettings = TabularFigures,
)

/**
 * Material's type scale with Space Grotesk on top and tabular figures throughout, the expressive
 * emphasized styles included (Bold where the face is Space Grotesk).
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
        titleMedium = titleMedium.tabular(),
        titleSmall = titleSmall.tabular(),
        bodyLarge = bodyLarge.tabular(),
        bodyMedium = bodyMedium.tabular(),
        bodySmall = bodySmall.tabular(),
        labelLarge = labelLarge.tabular(),
        labelMedium = labelMedium.tabular(),
        labelSmall = labelSmall.tabular(),
        displayLargeEmphasized = displayLargeEmphasized.display(FontWeight.Bold),
        displayMediumEmphasized = displayMediumEmphasized.display(FontWeight.Bold),
        displaySmallEmphasized = displaySmallEmphasized.display(FontWeight.Bold),
        headlineLargeEmphasized = headlineLargeEmphasized.display(FontWeight.Bold),
        headlineMediumEmphasized = headlineMediumEmphasized.display(FontWeight.Bold),
        headlineSmallEmphasized = headlineSmallEmphasized.display(FontWeight.Bold),
        titleLargeEmphasized = titleLargeEmphasized.display(FontWeight.Bold),
        titleMediumEmphasized = titleMediumEmphasized.tabular(),
        titleSmallEmphasized = titleSmallEmphasized.tabular(),
        bodyLargeEmphasized = bodyLargeEmphasized.tabular(),
        bodyMediumEmphasized = bodyMediumEmphasized.tabular(),
        bodySmallEmphasized = bodySmallEmphasized.tabular(),
        labelLargeEmphasized = labelLargeEmphasized.tabular(),
        labelMediumEmphasized = labelMediumEmphasized.tabular(),
        labelSmallEmphasized = labelSmallEmphasized.tabular(),
    )
}
