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
import com.hyperlog.app.R

/**
 * THEME PROPOSALS (throwaway branch). The THEME environment variable picks the colours
 * (cobalt, lime, plum, navy, indigo, electric, cerulean) and FONT the display face (archivo,
 * barlow, outfit, spacegrotesk, azeret, unbounded, bricolage), so Robolectric renders can show
 * each one. FONT defaults to the palette's own face.
 */
enum class Palette(val light: ColorScheme, val dark: ColorScheme, val face: Display) {
    Cobalt(CobaltLight, CobaltDark, Display.Archivo),
    Lime(LimeLight, LimeDark, Display.Barlow),
    Plum(PlumLight, PlumDark, Display.Bricolage),
    Navy(NavyLight, NavyDark, Display.Archivo),
    Indigo(IndigoLight, IndigoDark, Display.Archivo),
    Electric(ElectricLight, ElectricDark, Display.Archivo),
    Cerulean(CeruleanLight, CeruleanDark, Display.Archivo),
    ;

    companion object {
        val current: Palette
            get() = entries.firstOrNull { it.name.equals(System.getenv("THEME"), ignoreCase = true) } ?: Cobalt
    }
}

/** A display face for headings and big numbers: its SemiBold and Bold, tracking and a size scale to match Roboto's. */
enum class Display(semiBold: Int, bold: Int, val tracking: TextUnit, val scale: Float, val text: FontWeight = FontWeight.SemiBold) {
    Archivo(R.font.archivo_semiexpanded_semibold, R.font.archivo_semiexpanded_bold, (-0.01).em, 1f),
    Barlow(R.font.barlow_condensed_semibold, R.font.barlow_condensed_bold, 0.01.em, 1.12f, FontWeight.Bold),
    Outfit(R.font.outfit_semibold, R.font.outfit_bold, 0.em, 1.04f),
    SpaceGrotesk(R.font.space_grotesk_semibold, R.font.space_grotesk_bold, (-0.01).em, 1.02f),
    Azeret(R.font.azeret_mono_semibold, R.font.azeret_mono_bold, (-0.05).em, 0.92f),
    Unbounded(R.font.unbounded_semibold, R.font.unbounded_bold, (-0.01).em, 0.86f),
    Bricolage(R.font.bricolage_condensed_semibold, R.font.bricolage_condensed_bold, (-0.01).em, 1.04f),
    ;

    val family = FontFamily(Font(semiBold, FontWeight.SemiBold), Font(bold, FontWeight.Bold))

    companion object {
        val current: Display
            get() = entries.firstOrNull { it.name.equals(System.getenv("FONT"), ignoreCase = true) } ?: Palette.current.face
    }
}

/** Tabular figures in every style, system body included, so numbers keep their place as they change. */
private const val Tabular = "tnum"

private fun Display.typography(): Typography {
    val base = Typography()
    fun TextStyle.tnum() = copy(fontFeatureSettings = Tabular)
    fun TextStyle.display(weight: FontWeight = text) = copy(
        fontFamily = family,
        fontWeight = weight,
        letterSpacing = tracking,
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
        fontFeatureSettings = Tabular,
    )
    return Typography(
        displayLarge = base.displayLarge.display(FontWeight.Bold),
        displayMedium = base.displayMedium.display(FontWeight.Bold),
        displaySmall = base.displaySmall.display(FontWeight.Bold),
        headlineLarge = base.headlineLarge.display(),
        headlineMedium = base.headlineMedium.display(),
        headlineSmall = base.headlineSmall.display(),
        titleLarge = base.titleLarge.display(),
        titleMedium = base.titleMedium.tnum(),
        titleSmall = base.titleSmall.tnum(),
        bodyLarge = base.bodyLarge.tnum(),
        bodyMedium = base.bodyMedium.tnum(),
        bodySmall = base.bodySmall.tnum(),
        labelLarge = base.labelLarge.tnum(),
        labelMedium = base.labelMedium.tnum(),
        labelSmall = base.labelSmall.tnum(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HyperlogTheme(content: @Composable () -> Unit) {
    val palette = Palette.current
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) palette.dark else palette.light,
        typography = Display.current.typography(),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
