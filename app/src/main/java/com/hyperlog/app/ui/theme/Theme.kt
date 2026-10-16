package com.hyperlog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hyperlog.app.R

/**
 * THEME PROPOSALS (throwaway branch): the palette and display face are picked by the THEME
 * environment variable (cobalt, lime, plum), so Robolectric renders can show each one.
 */
enum class Proposal(val light: ColorScheme, val dark: ColorScheme, val display: FontFamily, val tracking: TextUnit, val scale: Float) {
    Cobalt(
        CobaltLight, CobaltDark,
        FontFamily(Font(R.font.archivo_semiexpanded_semibold, FontWeight.SemiBold), Font(R.font.archivo_semiexpanded_bold, FontWeight.Bold)),
        (-0.01).em, 1f,
    ),
    Lime(
        LimeLight, LimeDark,
        FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold), Font(R.font.barlow_condensed_bold, FontWeight.Bold)),
        0.em, 1.12f,
    ),
    Plum(
        PlumLight, PlumDark,
        FontFamily(Font(R.font.bricolage_condensed_semibold, FontWeight.SemiBold), Font(R.font.bricolage_condensed_bold, FontWeight.Bold)),
        (-0.01).em, 1.04f,
    ),
    ;

    companion object {
        val current: Proposal
            get() = when (System.getenv("THEME")?.lowercase()) {
                "lime" -> Lime
                "plum" -> Plum
                else -> Cobalt
            }
    }
}

private fun Proposal.typography(): Typography {
    val base = Typography()
    fun TextStyle.display(weight: FontWeight = FontWeight.SemiBold) = copy(
        fontFamily = display,
        fontWeight = weight,
        letterSpacing = tracking,
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )
    return base.copy(
        displayLarge = base.displayLarge.display(FontWeight.Bold),
        displayMedium = base.displayMedium.display(FontWeight.Bold),
        displaySmall = base.displaySmall.display(FontWeight.Bold),
        headlineLarge = base.headlineLarge.display(),
        headlineMedium = base.headlineMedium.display(),
        headlineSmall = base.headlineSmall.display(),
        titleLarge = base.titleLarge.display(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HyperlogTheme(content: @Composable () -> Unit) {
    val proposal = Proposal.current
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) proposal.dark else proposal.light,
        typography = proposal.typography(),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
