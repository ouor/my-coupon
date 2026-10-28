package com.ouor.mycoupon.ui.component

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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

/** App-internal thumbnail / image file (Coil: async decode + memory cache, no network module). */
@Composable
fun CouponThumb(path: String?, modifier: Modifier = Modifier, size: Dp = 56.dp, alpha: Float = 1f) {
    Surface(modifier.size(size), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        if (path != null) AsyncImage(File(path), null, Modifier.clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop, alpha = alpha)
    }
}

/** TopAppBar with an optional back/close icon (labelled for TalkBack). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, close: Boolean = false, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            // code review U-06: a second tap while the screen is already leaving would pop the screen below as well
            if (onBack != null) IconButton(onClick = dropUnlessResumed(block = onBack)) {
                Icon(if (close) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, if (close) "닫기" else "뒤로")
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** design-system §6.6: one banner at a time on S1. */
@Composable
fun InfoBanner(
    icon: ImageVector, text: String, container: Color, content: Color,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    sub: String? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    val body: @Composable ColumnScope.() -> Unit = {
        // round-2 review V4: one compact block; [sub] says which coupon (same product names repeat)
        Row(Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.s, bottom = Spacing.xs).heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.size(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                sub?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = Spacing.s, bottom = Spacing.xs), horizontalArrangement = Arrangement.End) { actions() }
    }
    if (onClick != null) Card(onClick, modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = colors, content = body)
    else Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = colors, content = body)
}

/** design-system §6.7 */
@Composable
fun EmptyState(
    icon: ImageVector?, title: String, body: String? = null, modifier: Modifier = Modifier,
    primary: Pair<String, () -> Unit>? = null, secondary: Pair<String, () -> Unit>? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.l))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        body?.let {
            Spacer(Modifier.height(Spacing.s))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(Spacing.xl))
        primary?.let { (t, a) -> PrimaryButton(t, a) }
        secondary?.let { (t, a) ->
            Spacer(Modifier.height(Spacing.s))
            OutlinedButton(a, Modifier.fillMaxWidth().heightIn(min = Spacing.cta), shape = MaterialTheme.shapes.large) { Text(t, style = MaterialTheme.typography.labelLarge) }
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
    TextButton(onClick, modifier.fillMaxWidth().heightIn(min = Spacing.touch), shape = MaterialTheme.shapes.large) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** design-system §6.9: fixed bottom button area, with an optional reason line above a disabled button. */
@Composable
fun BottomCta(text: String, onClick: () -> Unit, enabled: Boolean = true, hint: String? = null, outlined: Boolean = false, raised: Boolean = false) {
    // round-2 V1: [raised] while the page scrolls on under the bar, so a half-hidden row reads as "more below"
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = if (raised) 8.dp else 0.dp) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                hint?.let {
                    Text(it, Modifier.fillMaxWidth().padding(bottom = Spacing.s), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                // code review U-06: ignored once the screen started leaving (S5 [저장] twice popped two screens)
                val click = dropUnlessResumed(block = onClick)
                if (outlined) OutlinedButton(click, Modifier.fillMaxWidth().heightIn(min = Spacing.cta), enabled = enabled, shape = MaterialTheme.shapes.large) {
                    Text(text, style = MaterialTheme.typography.labelLarge)
                } else PrimaryButton(text, click, enabled = enabled)
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentWindowInsets = { WindowInsets(0) },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible)) {
        // code review A18: the sheet's text fields (S8 amount, backup password) stay above the keyboard
        Column(Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            description?.let {
                Spacer(Modifier.height(Spacing.s))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.l))
            content()
        }
    }
}

/** Settings group title (S10/S11). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = Spacing.s).semantics { heading() },
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
