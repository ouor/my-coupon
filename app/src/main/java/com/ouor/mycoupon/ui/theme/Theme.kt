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

// docs/design/design-system.md §1-§3. Dynamic color is off on purpose (status colours must not collide with primary).

val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F5FD6), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE), onPrimaryContainer = Color(0xFF0B3A8C),
    secondary = Color(0xFF4E5968), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEFF2), onSecondaryContainer = Color(0xFF191F28),
    background = Color.White, onBackground = Color(0xFF191F28),
    surface = Color.White, onSurface = Color(0xFF191F28),
    surfaceVariant = Color(0xFFF2F4F6), onSurfaceVariant = Color(0xFF4E5968),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF2F4F6), surfaceContainerHigh = Color(0xFFECEEF1),
    surfaceContainerHighest = Color(0xFFE5E8EB),
    outline = Color(0xFF7A8491), outlineVariant = Color(0xFFE5E8EB),
    error = Color(0xFFC62828), onError = Color.White,
    errorContainer = Color(0xFFFDECEC), onErrorContainer = Color(0xFF8C1D1D),
    scrim = Color.Black,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8AB2FF), onPrimary = Color(0xFF0A2A66),
    primaryContainer = Color(0xFF1C3566), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFAEB6C2), onSecondary = Color(0xFF121417),
    secondaryContainer = Color(0xFF2A2E35), onSecondaryContainer = Color(0xFFE9ECF1),
    background = Color(0xFF121417), onBackground = Color(0xFFE9ECF1),
    surface = Color(0xFF121417), onSurface = Color(0xFFE9ECF1),
    surfaceVariant = Color(0xFF1E2126), onSurfaceVariant = Color(0xFFAEB6C2),
    surfaceContainerLowest = Color(0xFF0D0F12), surfaceContainerLow = Color(0xFF181B1F),
    surfaceContainer = Color(0xFF1E2126), surfaceContainerHigh = Color(0xFF252931),
    surfaceContainerHighest = Color(0xFF2C3038),
    outline = Color(0xFF6B7480), outlineVariant = Color(0xFF2C3038),
    error = Color(0xFFFF9C94), onError = Color(0xFF2B0606),
    errorContainer = Color(0xFF3D1A1A), onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

/** design-system §1.2: M3 has no success/warning roles. */
@Immutable
data class StatusColors(
    val available: Color, val availableContainer: Color,
    val soon: Color, val soonContainer: Color,
    val ended: Color, val endedContainer: Color,
    val review: Color, val reviewContainer: Color, val reviewBorder: Color,
)

private val LightStatus = StatusColors(
    available = Color(0xFF11744F), availableContainer = Color(0xFFE3F4EC),
    soon = Color(0xFFC62828), soonContainer = Color(0xFFFDECEC),
    ended = Color(0xFF5B6573), endedContainer = Color(0xFFEDEFF2),
    review = Color(0xFFA14800), reviewContainer = Color(0xFFFFF1E2), reviewBorder = Color(0xFFCC6A00),
)
private val DarkStatus = StatusColors(
    available = Color(0xFF6FD3A6), availableContainer = Color(0xFF0F2E22),
    soon = Color(0xFFFF9C94), soonContainer = Color(0xFF3D1A1A),
    ended = Color(0xFFA3ACB8), endedContainer = Color(0xFF262A30),
    review = Color(0xFFFFB86B), reviewContainer = Color(0xFF3A2508), reviewBorder = Color(0xFFE08A2E),
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

val AppTypography = Typography(
    displaySmall = s(36, 44, FontWeight.Bold, balanced),
    headlineMedium = s(28, 36, FontWeight.Bold, balanced),
    headlineSmall = s(24, 32, FontWeight.Bold, balanced),
    titleLarge = s(22, 30, FontWeight.SemiBold, balanced),
    titleMedium = s(18, 26, FontWeight.SemiBold, balanced),
    titleSmall = s(15, 22, FontWeight.SemiBold, balanced),
    bodyLarge = s(17, 26, FontWeight.Normal),
    bodyMedium = s(15, 22, FontWeight.Normal),
    bodySmall = s(13, 18, FontWeight.Normal),
    labelLarge = s(16, 22, FontWeight.SemiBold),
    labelMedium = s(13, 18, FontWeight.SemiBold),
    labelSmall = s(12, 16, FontWeight.Medium),
)

/** §2.2 extension styles; numbers use tabular figures. */
object CodeTypography {
    val codeLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.04.em, fontFeatureSettings = "tnum")
    val codePin = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum")
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** [forceLight]: S6/S7 are always light (design D6, P5). */
@Composable
fun MyCouponTheme(forceLight: Boolean = false, content: @Composable () -> Unit) {
    val dark = !forceLight && isSystemInDarkTheme()
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
