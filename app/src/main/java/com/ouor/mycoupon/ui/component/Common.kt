package com.ouor.mycoupon.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.lifecycle.compose.dropUnlessResumed
import com.ouor.mycoupon.ui.theme.statusColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ouor.mycoupon.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** §2.3-3: at 150 %+ font scale, rows become columns and thumbnails hide. */
@Composable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale >= 1.5f

/** App-wide Snackbar that outlives the screen that asked for it (S8 → S1 "되돌리기"). */
class AppSnackbar(val host: SnackbarHostState, private val scope: CoroutineScope, private val undoMs: Long = 8_000) {
    private var undo: kotlinx.coroutines.Job? = null
    private var plain: kotlinx.coroutines.Job? = null

    /**
     * With an [action] (undo) it stays [undoMs] (C2-04: 8 s or the accessibility "time to take action"); M3 Long varies by device.
     * Code review U-13: a message without an action waits until a showing undo has gone (only the latest one waits);
     * a new undo replaces everything.
     */
    fun show(message: String, action: String? = null, onAction: () -> Unit = {}) {
        plain?.cancel()
        if (action == null) {
            val showing = undo?.takeIf { it.isActive }
            plain = scope.launch {
                showing?.join()
                host.currentSnackbarData?.dismiss()
                host.showSnackbar(message, null, withDismissAction = false, duration = SnackbarDuration.Short)
            }
            return
        }
        undo?.cancel()
        undo = scope.launch {
            host.currentSnackbarData?.dismiss()
            val r = kotlinx.coroutines.withTimeoutOrNull(undoMs) {
                host.showSnackbar(message, action, withDismissAction = false, duration = SnackbarDuration.Indefinite)
            }
            if (r == null) host.currentSnackbarData?.dismiss()
            if (r == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

val LocalSnackbar = staticCompositionLocalOf<AppSnackbar> { error("no snackbar") }

/**
 * App-internal thumbnail. Real coupon images are loud (brand colours, white tickets, a barcode at the bottom), so in lists
 * they stay small and calm: the top of the image (the product photo, never the barcode), a little desaturated,
 * a 1px edge so a white ticket does not melt into white paper. [past] goes grey.
 */
@Composable
fun CouponThumb(path: String?, modifier: Modifier = Modifier, size: Dp = 40.dp, alpha: Float = 1f, past: Boolean = false) {
    val shape = if (size <= 48.dp) RoundedCornerShape(8.dp) else MaterialTheme.shapes.medium
    val small = size <= 48.dp
    // only list thumbnails are toned down; S4/S9 previews are for checking the photo, so they keep full colour
    val filter = remember(past, small) { if (past || small) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(if (past) 0f else 0.75f) }) else null }
    Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .border(1.dp, MaterialTheme.statusColors.line, shape)) {
        if (path != null) AsyncImage(File(path), null, Modifier.size(size), contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter, alpha = alpha, colorFilter = filter)
    }
}

/**
 * One group = one surface + hairlines (redesign principle 5). Lazy lists draw a group item by item: [first]/[last]
 * round the matching corners, the dark-theme 1px edge is drawn only on the outer sides, and every item after the
 * first gets the inset divider on top.
 */
fun Modifier.groupItem(first: Boolean, last: Boolean, surface: Color, border: Color, line: Color, radius: Dp = 14.dp, inset: Dp = 14.dp): Modifier =
    this.clip(RoundedCornerShape(if (first) radius else 0.dp, if (first) radius else 0.dp, if (last) radius else 0.dp, if (last) radius else 0.dp))
        .drawBehind {
            drawRect(surface)
            val r = radius.toPx()
            val top = if (first) 0f else -2 * r
            val bottom = if (last) size.height else size.height + 2 * r
            if (border.alpha > 0f) drawRoundRect(border, Offset(0.5f, top + 0.5f), Size(size.width - 1f, bottom - top - 1f),
                CornerRadius(r, r), style = Stroke(1.dp.toPx()))
            if (!first) drawRect(line, Offset(inset.toPx(), 0f), Size(size.width - inset.toPx(), 1.dp.toPx()))
        }

/** Non-lazy group: surface, 14 radius, dark 1px edge; rows inside are separated with [GroupDivider]. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val s = MaterialTheme.statusColors
    val shape = MaterialTheme.shapes.large
    Column(modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, s.border, shape), content = content)
}

@Composable
fun GroupDivider(inset: Dp = 14.dp) = HorizontalDivider(Modifier.padding(start = inset), thickness = 1.dp, color = MaterialTheme.statusColors.line)

/** Small grey label above a group ("7일 안에 2", "알림"). [count] in the fainter grey. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, count: Int? = null) {
    Row(modifier.padding(start = Spacing.xs, top = 14.dp, bottom = 6.dp).semantics(mergeDescendants = true) { heading() },
    ) {
        // designer 11-04: one Text so the count shares size and baseline; only its colour differs
        val faint = MaterialTheme.statusColors.faint
        Text(androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            count?.let { append(" "); withStyle(androidx.compose.ui.text.SpanStyle(color = faint)) { append("$it") } }
        }, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The only secondary control shape: ink text, no border or fill (redesign principle 4). */
@Composable
fun InkTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
                  color: Color = MaterialTheme.colorScheme.primary) {
    TextButton(onClick, modifier.heightIn(min = Spacing.touch), enabled = enabled, shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(contentColor = color), contentPadding = PaddingValues(horizontal = 10.dp)) {
        icon?.let { Icon(it, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Top bar on the paper background: 22 Bold title, outlined 22dp icons (back/close labelled for TalkBack). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, close: Boolean = false, center: (@Composable () -> Unit)? = null,
              actions: @Composable () -> Unit = {}) {
    val titleSlot: @Composable () -> Unit = center ?: { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
    val nav: @Composable () -> Unit = {
        // code review U-06: a second tap while the screen is already leaving would pop the screen below as well
        if (onBack != null) IconButton(onClick = dropUnlessResumed(block = onBack)) {
            Icon(if (close) Ink.Close else Ink.Back, if (close) "닫기" else "뒤로")
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.background, navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurface)
    // designer 11-04: [center] (S6 ‹ 1/4 ›) sits on the screen's centre line, not in the gap between back and the actions
    if (center != null) androidx.compose.material3.CenterAlignedTopAppBar(titleSlot, navigationIcon = nav, actions = { actions() }, colors = colors)
    else TopAppBar(titleSlot, navigationIcon = nav, actions = { actions() }, colors = colors)
}

/**
 * design-system §6.6 / redesign principle 8: a notice is one neutral line on a surface — small round icon, text,
 * text buttons. Colour lives only in the icon dot ([accent]: soon/review), never in the whole block.
 */
@Composable
fun InfoBanner(
    icon: ImageVector, text: String,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    sub: String? = null,
    accent: Color? = null,
    inlineActions: Boolean = false, // owner feedback 11-04: a single short button sits right of the text, not on its own line
    actions: @Composable () -> Unit = {},
) {
    val st = MaterialTheme.statusColors
    val shape = MaterialTheme.shapes.large
    val base = modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, st.border, shape)
    Column(if (onClick != null) base.clickable(onClick = onClick) else base) {
        // designer 11-04: a one-line notice is 48-52dp (the inline button's 48dp touch sets it), not a 64dp slab
        Row(Modifier.padding(start = 12.dp, end = Spacing.xs, top = if (inlineActions) 2.dp else Spacing.s, bottom = if (inlineActions) 2.dp else Spacing.s).heightIn(min = 46.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(accent?.copy(alpha = 0.14f) ?: st.track), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(16.dp), tint = accent ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(end = Spacing.xs)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (inlineActions) actions()
        }
        if (!inlineActions) Row(Modifier.fillMaxWidth().padding(end = Spacing.xs, bottom = Spacing.xs), horizontalArrangement = Arrangement.End) { actions() }
    }
}

/** design-system §6.7 */
@Composable
fun EmptyState(
    icon: ImageVector?, title: String, body: String? = null, modifier: Modifier = Modifier,
    primary: Pair<String, () -> Unit>? = null, secondary: Pair<String, () -> Unit>? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.statusColors.faint)
        Spacer(Modifier.height(Spacing.l))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        body?.let {
            Spacer(Modifier.height(Spacing.s))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(Spacing.xl))
        primary?.let { (t, a) -> PrimaryButton(t, a) }
        secondary?.let { (t, a) ->
            Spacer(Modifier.height(Spacing.xs))
            SecondaryButton(t, a)
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = Spacing.cta), enabled = enabled, shape = MaterialTheme.shapes.large,
        colors = if (danger) androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
        // round-2 V12 / code review U-25: the CTA colour is a theme token (dark: white on it 4.6:1)
        else androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.statusColors.cta, contentColor = MaterialTheme.statusColors.onCta),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick, modifier.fillMaxWidth().heightIn(min = Spacing.touch), shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** design-system §6.9: fixed bottom button area, with an optional reason line above a disabled button. */
@Composable
fun BottomCta(text: String, onClick: () -> Unit, enabled: Boolean = true, hint: String? = null, outlined: Boolean = false, raised: Boolean = false) {
    // round-2 V1: [raised] while the page scrolls on under the bar, so a half-hidden row reads as "more below"
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = if (raised) 8.dp else 0.dp) {
        Column(Modifier.navigationBarsPadding()) {
            if (raised) HorizontalDivider(color = MaterialTheme.statusColors.line)
            Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                hint?.let {
                    Text(it, Modifier.fillMaxWidth().padding(bottom = Spacing.s), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                // code review U-06: ignored once the screen started leaving (S5 [저장] twice popped two screens)
                val click = dropUnlessResumed(block = onClick)
                if (outlined) SecondaryButton(text, click, Modifier.heightIn(min = Spacing.cta))
                else PrimaryButton(text, click, enabled = enabled)
            }
        }
    }
}

/**
 * design-system §6.5: title → description → content → stacked buttons (never side by side: 200 % font).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismiss: () -> Unit, title: String, description: String? = null, dismissible: Boolean = true, content: @Composable ColumnScope.() -> Unit,
) {
    // code review U-10: a progress sheet ([dismissible] false) cannot be swiped or backed away while its work goes on
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { dismissible || it != SheetValue.Hidden }),
        containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = { WindowInsets(0) },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible)) {
        // code review A18: the sheet's text fields (S8 amount, backup password) stay above the keyboard
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            description?.let {
                Spacer(Modifier.height(Spacing.s))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.l))
            content()
        }
    }
}

/** Settings group title (S10/S11): small grey, outside the group it names. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    GroupLabel(text, modifier.padding(horizontal = Spacing.l))
}
