package com.ouor.mycoupon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// docs/design/design-system.md §1-§3, direction "잉크와 종이" (docs/design/redesign). Dynamic color is off on purpose:
// colour means time (빨강 = 7일 안에, 주황 = 확인 필요, 회색 = 지남); everything else is ink and grey, no blue.

val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF18181B), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E2DE), onPrimaryContainer = Color(0xFF18181B),
    secondary = Color(0xFF6E6E73), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E2DE), onSecondaryContainer = Color(0xFF18181B),
    tertiary = Color(0xFF18181B), onTertiary = Color.White,
    background = Color(0xFFF3F3F1), onBackground = Color(0xFF18181B),
    surface = Color.White, onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFECECEA), onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAF9),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFECECEA), // "paper": cards, menus, history on the grey background
    surfaceContainerHighest = Color(0xFFE3E2DE),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF8A8A8F), outlineVariant = Color(0xFFE7E6E2),
    error = Color(0xFFC62828), onError = Color.White,
    errorContainer = Color(0xFFFDECEC), onErrorContainer = Color(0xFF8C1D1D),
    inverseSurface = Color(0xFF18181B), inverseOnSurface = Color(0xFFF4F4F5), inversePrimary = Color(0xFFF4F4F5),
    scrim = Color.Black,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFF4F4F5), onPrimary = Color(0xFF18181B),
    primaryContainer = Color(0xFF2A2A2F), onPrimaryContainer = Color(0xFFF4F4F5),
    secondary = Color(0xFFA1A1A8), onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF2E2E33), onSecondaryContainer = Color(0xFFEDEDEF),
    tertiary = Color(0xFFF4F4F5), onTertiary = Color(0xFF18181B),
    background = Color(0xFF0C0C0E), onBackground = Color(0xFFEDEDEF),
    surface = Color(0xFF18181B), onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF1F1F23), onSurfaceVariant = Color(0xFFA1A1A8),
    surfaceContainerLowest = Color(0xFF0C0C0E), surfaceContainerLow = Color(0xFF141417),
    surfaceContainer = Color(0xFF18181B), surfaceContainerHigh = Color(0xFF232327),
    surfaceContainerHighest = Color(0xFF2E2E33),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF75757D), outlineVariant = Color(0xFF26262A),
    error = Color(0xFFFF9C94), onError = Color(0xFF2B0606),
    errorContainer = Color(0xFF3D1A1A), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF4F4F5), inverseOnSurface = Color(0xFF18181B), inversePrimary = Color(0xFF18181B),
    scrim = Color.Black,
)

/** design-system §1.2: M3 has no success/warning roles, nor the paper tokens (line, border, track, faint). */
@Immutable
data class StatusColors(
    val available: Color, val availableContainer: Color,
    val soon: Color, val soonContainer: Color,
    val ended: Color, val endedContainer: Color,
    val review: Color, val reviewContainer: Color, val reviewBorder: Color,
    /** the one main button per screen: ink in light; in dark a raised grey, never white (it would outshine the barcode) */
    val cta: Color, val onCta: Color,
    /** 1px divider inside a group */
    val line: Color,
    /** 1px group edge: none on light paper, needed in dark where surface and background are close */
    val border: Color,
    /** segmented control track / selected thumb */
    val track: Color, val thumb: Color,
    /** dates and counts that sit beside a label */
    val faint: Color,
)

/** Shared with the widget (code review U-25), which cannot read MaterialTheme. */
val LightStatus = StatusColors(
    available = Color(0xFF11744F), availableContainer = Color(0xFFE3F4EC),
    soon = Color(0xFFC62828), soonContainer = Color(0xFFFDECEC),
    ended = Color(0xFF5B6573), endedContainer = Color(0xFFECEDEF),
    review = Color(0xFFA14800), reviewContainer = Color(0xFFFFF1E2), reviewBorder = Color(0xFFCC6A00),
    cta = Color(0xFF18181B), onCta = Color.White,
    line = Color(0xFFE7E6E2), border = Color.Transparent,
    track = Color(0xFFE3E2DE), thumb = Color.White,
    faint = Color(0xFF85858A),
)
val DarkStatus = StatusColors(
    available = Color(0xFF6FD3A6), availableContainer = Color(0xFF0F2E22),
    soon = Color(0xFFFF9C94), soonContainer = Color(0xFF3D1A1A),
    ended = Color(0xFFA3ACB8), endedContainer = Color(0xFF262A30),
    review = Color(0xFFFFB86B), reviewContainer = Color(0xFF3A2508), reviewBorder = Color(0xFFE08A2E),
    cta = Color(0xFF2A2A2F), onCta = Color(0xFFF4F4F5),
    line = Color(0xFF26262A), border = Color(0xFF26262A),
    track = Color(0xFF1F1F23), thumb = Color(0xFF2E2E33),
    faint = Color(0xFF85858C),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }

val MaterialTheme.statusColors: StatusColors
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current

/** §1.3 fixed regardless of theme. */
object BarcodeColors {
    val background = Color.White
    val bar = Color.Black
}

/** §3.1 (M3 has no spacing tokens). */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val touch = 48.dp
    val cta = 56.dp
}

// §2.3-6: Korean breaks by phrase (Android 13+), never inside "사용 가/능".
private val phrase = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
// review U10: titles use balanced lines so a long name never leaves one syllable alone ("…세 / 트")
private val balanced = phrase.copy(strategy = LineBreak.Strategy.Balanced)
private fun s(size: Int, line: Int, w: FontWeight, b: LineBreak = phrase) = TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = w, lineBreak = b)

// redesign: two weights (Regular, SemiBold); Bold only for screen titles and D-day numbers. Hierarchy from size and grey.
val AppTypography = Typography(
    displaySmall = s(34, 42, FontWeight.Bold, balanced),
    headlineMedium = s(26, 34, FontWeight.Bold, balanced),
    headlineSmall = s(22, 30, FontWeight.SemiBold, balanced),
    titleLarge = s(22, 30, FontWeight.Bold, balanced),
    titleMedium = s(16, 23, FontWeight.Medium, balanced),
    titleSmall = s(14, 20, FontWeight.Medium, balanced),
    bodyLarge = s(16, 24, FontWeight.Normal),
    bodyMedium = s(14, 21, FontWeight.Normal),
    bodySmall = s(13, 18, FontWeight.Normal),
    labelLarge = s(15, 21, FontWeight.SemiBold),
    labelMedium = s(13, 18, FontWeight.Medium),
    labelSmall = s(12, 16, FontWeight.Medium),
)

/** D-day number in a list row (Bold, tabular). */
val DdayStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

/** §2.2 extension styles; numbers use tabular figures. */
object CodeTypography {
    val codeLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.04.em, fontFeatureSettings = "tnum")
    val codePin = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum")
}

// redesign: three radii — status chip 6, control 10, group 14 (sheets keep a 20 top).
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** [forceLight]: the barcode panel and S7 are always light (design D6, P5); the rest of S6 follows the system. */
@Composable
fun MyCouponTheme(forceLight: Boolean = false, content: @Composable () -> Unit) {
    val dark = !forceLight && isSystemInDarkTheme()
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
