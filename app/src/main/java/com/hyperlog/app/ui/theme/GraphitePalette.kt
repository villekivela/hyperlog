package com.hyperlog.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Hyperlog's own colours, Graphite: neutral surfaces, text and dividers with only a faint cool
// bias, and an accent with no hue at all, near-black in light and near-white in dark, the same on
// every phone. So RIR, plates, the trend and errors are the only colour on screen. The accent fills
// primary and primaryContainer (the Start button, the hero) and secondaryContainer is a grey a step
// off the cards (the selected tab, chips that are on); tertiary follows primary. tertiaryContainer
// (advice, technique pills) is its own grey step, apart from the plain sets' tone and the cards'.
// Light cards are white, dark is charcoal. Errors keep Material's defaults. surfaceTint is
// transparent so elevation never tints a surface.

internal val GraphiteLight = lightColorScheme(
    primary = Color(0xFF1C1C1F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1C1C1F),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFE9E9EC),
    surfaceTint = Color.Transparent,
    secondary = Color(0xFF5C5C62),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E2E6),
    onSecondaryContainer = Color(0xFF19191B),
    tertiary = Color(0xFF1C1C1F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDADADF),
    onTertiaryContainer = Color(0xFF19191B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFAFB),
    onBackground = Color(0xFF19191B),
    surface = Color(0xFFFAFAFB),
    onSurface = Color(0xFF19191B),
    surfaceVariant = Color(0xFFE4E4E8),
    onSurfaceVariant = Color(0xFF5C5C62),
    inverseSurface = Color(0xFF2E2E32),
    inverseOnSurface = Color(0xFFF2F2F4),
    outline = Color(0xFF85858C),
    outlineVariant = Color(0xFFD4D4D9),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFE4E4E8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F7),
    surfaceContainer = Color(0xFFEEEEF0),
    surfaceContainerHigh = Color(0xFFE9E9EC),
    surfaceContainerHighest = Color(0xFFE4E4E8),
)

internal val GraphiteDark = darkColorScheme(
    primary = Color(0xFFF2F2F4),
    onPrimary = Color(0xFF141416),
    primaryContainer = Color(0xFFE9E9EC),
    onPrimaryContainer = Color(0xFF141416),
    inversePrimary = Color(0xFF1C1C1F),
    surfaceTint = Color.Transparent,
    secondary = Color(0xFFA3A3A8),
    onSecondary = Color(0xFF121214),
    secondaryContainer = Color(0xFF44444A),
    onSecondaryContainer = Color(0xFFF2F2F4),
    tertiary = Color(0xFFF2F2F4),
    onTertiary = Color(0xFF141416),
    tertiaryContainer = Color(0xFF38383E),
    onTertiaryContainer = Color(0xFFEDEDEF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121214),
    onBackground = Color(0xFFEDEDEF),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF2E2E33),
    onSurfaceVariant = Color(0xFFA3A3A8),
    inverseSurface = Color(0xFFEDEDEF),
    inverseOnSurface = Color(0xFF2C2C30),
    outline = Color(0xFF6E6E75),
    outlineVariant = Color(0xFF38383D),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF121214),
    surfaceBright = Color(0xFF232326),
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF1B1B1E),
    surfaceContainer = Color(0xFF151517),
    surfaceContainerHigh = Color(0xFF1F1F22),
    surfaceContainerHighest = Color(0xFF2E2E33),
)

/**
 * The clay mannequin's greys, Graphite's own (SPEC, Technique videos → The mannequin;
 * docs/design/exercise-library.md, Mannequin). The body is the subject, so it holds the most
 * contrast with the stage, and the joint rings are a step darker than it. The kits are in five
 * tones by part, with no hue (owner, #127): the [frame] and uprights, the vinyl pads and grips
 * ([vinyl]), the stack's plates and every other weight ([plate]), the rods, bars, cables and pins
 * ([metal]), and the pulleys and their housings ([pulley]). [shadow] is the floor shadow's strength
 * and [rim] a faint light from behind that gives a dark frame an edge against a dark stage.
 */
internal data class ClayColours(
    val body: Color,
    val joint: Color,
    val frame: Color,
    val vinyl: Color,
    val plate: Color,
    val metal: Color,
    val pulley: Color,
    val shadow: Float,
    val rim: Float,
) {
    /** The colour of a clip's material by its name, or null for one the theme doesn't colour. */
    fun colourOf(material: String): Color? = when (material) {
        "body" -> body
        "joint" -> joint
        "frame" -> frame
        "vinyl" -> vinyl
        "plate" -> plate
        "metal" -> metal
        "pulley" -> pulley
        else -> null
    }

    companion object {
        val Light = ClayColours(
            body = Color(0xFFBABAC1), joint = Color(0xFF9A9AA1),
            frame = Color(0xFF2E2E33), vinyl = Color(0xFF1B1B1E), plate = Color(0xFF8A8A90), metal = Color(0xFFC8C8CC), pulley = Color(0xFF5C5C62),
            shadow = 0.65f, rim = 0f,
        )
        val Dark = ClayColours(
            body = Color(0xFFC4C4CA), joint = Color(0xFFA8A8AF),
            frame = Color(0xFF4A4A50), vinyl = Color(0xFF2E2E33), plate = Color(0xFF8A8A90), metal = Color(0xFFC8C8CC), pulley = Color(0xFF6E6E75),
            shadow = 1f, rim = 1.3f,
        )
    }
}
