package com.hyperlog.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyperlog.app.R

// PROTOTYPE, throwaway, never merged (branch prototype/today-settings). Four looks for Today and
// for where Settings lives, cycled from a floating switcher in debug builds:
//   A  Now      today's app: Navy on grey, gear in every top bar.
//   B  Ledger   Slate & cobalt, tight 16 dp shapes, date-led Today; Settings is the last row of Today.
//   C  Week     Ink & violet, big expressive shapes, a week strip and a hero card; Settings is a
//               fifth tab in the navigation bar.
//   D  Up next  Royal on graphite, a full-bleed accent header with the first exercise up front;
//               Settings is a gear in that header, which scrolls away with it.
// B, C and D set numbers and body text in Inter; Space Grotesk keeps titles.

internal class Look(
    val key: String,
    val name: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    val outer: Dp,
    val inner: Dp,
    val gap: Dp,
    val inter: Boolean,
)

internal val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private class Tint(
    val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color,
    val select: Color, val onSelect: Color, val tertiaryContainer: Color,
    val surface: Color, val screen: Color, val card: Color, val low: Color, val high: Color, val highest: Color,
    val on: Color, val onVariant: Color, val outline: Color, val outlineVariant: Color,
)

private fun Tint.dark() = darkColorScheme(
    primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onContainer,
    inversePrimary = container, surfaceTint = Color.Transparent,
    secondary = onVariant, onSecondary = surface, secondaryContainer = select, onSecondaryContainer = onSelect,
    tertiary = primary, onTertiary = onPrimary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = on,
    background = surface, onBackground = on, surface = surface, onSurface = on,
    surfaceVariant = highest, onSurfaceVariant = onVariant, inverseSurface = on, inverseOnSurface = surface,
    outline = outline, outlineVariant = outlineVariant, surfaceDim = surface, surfaceBright = card,
    surfaceContainerLowest = surface, surfaceContainerLow = low, surfaceContainer = screen,
    surfaceContainerHigh = high, surfaceContainerHighest = highest,
)

private fun Tint.light() = lightColorScheme(
    primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onContainer,
    inversePrimary = select, surfaceTint = Color.Transparent,
    secondary = onVariant, onSecondary = Color.White, secondaryContainer = select, onSecondaryContainer = onSelect,
    tertiary = primary, onTertiary = onPrimary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = on,
    background = surface, onBackground = on, surface = surface, onSurface = on,
    surfaceVariant = highest, onSurfaceVariant = onVariant, inverseSurface = on, inverseOnSurface = surface,
    outline = outline, outlineVariant = outlineVariant, surfaceDim = highest, surfaceBright = card,
    surfaceContainerLowest = card, surfaceContainerLow = low, surfaceContainer = screen,
    surfaceContainerHigh = high, surfaceContainerHighest = highest,
)

private val SlateDark = Tint(
    Color(0xFF8FB0FF), Color(0xFF0A1A4D), Color(0xFF2F5BE0), Color.White, Color(0xFF1F3366), Color(0xFFD6E2FF), Color(0xFF26324A),
    Color(0xFF0E131C), Color(0xFF121926), Color(0xFF1C2536), Color(0xFF151C29), Color(0xFF1A2231), Color(0xFF243048),
    Color(0xFFE3E8F2), Color(0xFF9AA6BC), Color(0xFF5A6680), Color(0xFF2C364A),
)
private val SlateLight = Tint(
    Color(0xFF2F5BE0), Color.White, Color(0xFF2F5BE0), Color.White, Color(0xFFD9E3FF), Color(0xFF0E2A7A), Color(0xFFDCE3EE),
    Color(0xFFF6F8FC), Color(0xFFE9EEF6), Color.White, Color(0xFFF2F5FA), Color(0xFFE2E8F1), Color(0xFFD6DEEA),
    Color(0xFF121826), Color(0xFF4F5B70), Color(0xFF7C879B), Color(0xFFCCD4E0),
)
private val VioletDark = Tint(
    Color(0xFFC4B1FF), Color(0xFF2A1670), Color(0xFF6A4CE0), Color.White, Color(0xFF3A2C66), Color(0xFFE6DEFF), Color(0xFF2F2840),
    Color(0xFF14111B), Color(0xFF18141F), Color(0xFF241E2F), Color(0xFF1C1724), Color(0xFF211B2B), Color(0xFF2E2640),
    Color(0xFFECE6F5), Color(0xFFA9A0BA), Color(0xFF6B6280), Color(0xFF352D45),
)
private val VioletLight = Tint(
    Color(0xFF5B3DD0), Color.White, Color(0xFF5B3DD0), Color.White, Color(0xFFE6DEFF), Color(0xFF23145C), Color(0xFFE2DCEC),
    Color(0xFFFBF9FE), Color(0xFFF0ECF7), Color.White, Color(0xFFF7F4FB), Color(0xFFE8E3F1), Color(0xFFDDD6EA),
    Color(0xFF1B1623), Color(0xFF5E5570), Color(0xFF8A8199), Color(0xFFD6CFE2),
)
private val RoyalDark = Tint(
    Color(0xFFA9B4FF), Color(0xFF14207A), Color(0xFF3346D3), Color.White, Color(0xFF24306E), Color(0xFFDDE1FF), Color(0xFF2E2E33),
    Color(0xFF121214), Color(0xFF161618), Color(0xFF222226), Color(0xFF1A1A1D), Color(0xFF1E1E21), Color(0xFF2A2A2F),
    Color(0xFFEDEDF0), Color(0xFFA3A3AB), Color(0xFF6A6A72), Color(0xFF323238),
)
private val RoyalLight = Tint(
    Color(0xFF3346D3), Color.White, Color(0xFF3346D3), Color.White, Color(0xFFDDE1FF), Color(0xFF14207A), Color(0xFFE0E0E4),
    Color(0xFFF8F8FA), Color(0xFFEEEEF0), Color.White, Color(0xFFF4F4F6), Color(0xFFE6E6E9), Color(0xFFDCDCE0),
    Color(0xFF18181B), Color(0xFF5C5C64), Color(0xFF8A8A92), Color(0xFFD2D2D8),
)

internal val Looks = listOf(
    Look("A", "Now", NavyLight, NavyDark, 24.dp, 4.dp, 2.dp, inter = false),
    Look("B", "Ledger", SlateLight.light(), SlateDark.dark(), 16.dp, 0.dp, 1.dp, inter = true),
    Look("C", "Week", VioletLight.light(), VioletDark.dark(), 32.dp, 8.dp, 4.dp, inter = true),
    Look("D", "Up next", RoyalLight.light(), RoyalDark.dark(), 20.dp, 4.dp, 2.dp, inter = true),
)

object PrototypeLook {
    internal var index by mutableIntStateOf(2)
    internal var forcedDark by mutableStateOf<Boolean?>(null)
    internal val look get() = Looks[index]
    val key: String get() = look.key
    /** Variant A keeps the gear in every destination's top bar. */
    val gearInTopBar get() = key == "A"
    /** Variant C puts Settings in the navigation bar. */
    val settingsTab get() = key == "C"
}

private fun TextStyle.inter() = copy(fontFamily = Inter, fontFeatureSettings = TabularFigures)

/** Inter for body, labels and the smaller titles, where the numbers are; Space Grotesk keeps the big titles. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val InterTypography: Typography = HyperlogTypography.run {
    copy(
        titleMedium = titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.inter(), bodyMedium = bodyMedium.inter(), bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter(), labelMedium = labelMedium.inter(), labelSmall = labelSmall.inter(),
        titleMediumEmphasized = titleMediumEmphasized.inter(), titleSmallEmphasized = titleSmallEmphasized.inter(),
        bodyLargeEmphasized = bodyLargeEmphasized.inter(), bodyMediumEmphasized = bodyMediumEmphasized.inter(),
        bodySmallEmphasized = bodySmallEmphasized.inter(), labelLargeEmphasized = labelLargeEmphasized.inter(),
        labelMediumEmphasized = labelMediumEmphasized.inter(), labelSmallEmphasized = labelSmallEmphasized.inter(),
    )
}

/** The floating switcher: ‹ variant › and Auto/Light/Dark, top centre, clear of Start and the tabs. */
@Composable
internal fun LookSwitcher(modifier: Modifier) {
    val n = Looks.size
    val look = PrototypeLook.look
    Row(
        modifier
            .statusBarsPadding()
            .padding(top = 4.dp)
            .shadow(8.dp, RoundedCornerShape(50))
            .background(Color(0xEE000000), RoundedCornerShape(50))
            .padding(horizontal = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        val style = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        Text("‹", Modifier.clickable { PrototypeLook.index = (PrototypeLook.index - 1 + n) % n }.then(style), color = Color.White, fontSize = 20.sp)
        Text("${look.key}  ${look.name}", Modifier.padding(horizontal = 4.dp), color = Color.White, fontSize = 14.sp)
        Text("›", Modifier.clickable { PrototypeLook.index = (PrototypeLook.index + 1) % n }.then(style), color = Color.White, fontSize = 20.sp)
        Text(
            when (PrototypeLook.forcedDark) { null -> "Auto"; true -> "Dark"; false -> "Light" },
            Modifier.clickable {
                PrototypeLook.forcedDark = when (PrototypeLook.forcedDark) { null -> false; false -> true; true -> null }
            }.then(style),
            color = Color(0xFFFFD54F),
            fontSize = 13.sp,
        )
    }
}
