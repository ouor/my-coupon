package com.ouor.mycoupon.ui.component

import androidx.compose.material3.OutlinedButton
import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors
import java.time.LocalDate
import java.util.Locale

/**
 * C2-04: how long undo stays (S6 bar and S1 Snackbar): 8 s, or longer when the system accessibility
 * "time to take action" setting asks for it.
 */
fun undoMillis(context: Context): Long {
    if (Build.VERSION.SDK_INT < 29) return 8_000
    val am = context.getSystemService(AccessibilityManager::class.java) ?: return 8_000
    return am.getRecommendedTimeoutMillis(8_000, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS).toLong()
}

/** design-system §6.12 (C2-01): shown only while the window really is at full brightness. */
@Composable
fun BrightnessChip(modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = 28.dp).clearAndSetSemantics { contentDescription = "밝기 최대, 화면 켜 둠" },
        shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(Icons.Rounded.LightMode, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text("밝기 최대 · 화면 켜 둠", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** First and second line of the C2-02 band, null for a coupon that can still be used. */
fun pastBand(c: Coupon, today: LocalDate): Pair<String, String>? = when (c.state(today)) {
    CouponState.USED -> "이미 사용한 쿠폰이에요" to (c.usedAt?.let { epochDay(it).monthDay() } ?: "")
    CouponState.GIVEN -> "넘긴 쿠폰이에요" to listOfNotNull(c.usedAt?.let { epochDay(it).monthDay() }, c.givenTo).joinToString(" · ")
    CouponState.EXPIRED -> "기간이 지난 쿠폰이에요" to "${c.expiryDate!!.monthDay()}까지 · 매장에 따라 받아 줄 수도 있어요"
    else -> null
}

/** design-system §6.13 (C2-02): above the barcode, the first thing TalkBack reads. */
@Composable
fun PastCouponBand(c: Coupon, today: LocalDate, modifier: Modifier = Modifier) {
    val (title, sub) = pastBand(c, today) ?: return
    val s = MaterialTheme.statusColors
    val state = c.state(today)
    val (fg, bg) = if (state == CouponState.EXPIRED) s.ended to s.endedContainer else s.soon to s.soonContainer
    val icon = when (state) { CouponState.GIVEN -> Icons.AutoMirrored.Rounded.Send; CouponState.EXPIRED -> Icons.Rounded.EventBusy; else -> Icons.Rounded.Warning }
    Surface(modifier.fillMaxWidth().heightIn(min = 64.dp).semantics(mergeDescendants = true) {}, shape = MaterialTheme.shapes.medium,
        color = bg, contentColor = fg) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(28.dp))
            Spacer(Modifier.width(Spacing.m))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** design-system §6.14 (C2-04): replaces the CTA right after [사용 완료]; [secondsLeft] counts down outside. */
@Composable
fun UndoBar(text: String, secondsLeft: Int, onUndo: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // FlowRow-like: the text wraps above the button at large font sizes instead of squeezing it
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)
                .semantics { liveRegion = LiveRegionMode.Assertive }) {
                Text(text, Modifier.fillMaxWidth().semantics { contentDescription = "$text, 되돌리기 ${secondsLeft}초" },
                    style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.heightIn(min = Spacing.s))
                // round-2 V10: a secondary (outlined) button, and the number says it is seconds
                OutlinedButton(onUndo, Modifier.fillMaxWidth().heightIn(min = Spacing.cta), shape = MaterialTheme.shapes.large) {
                    Text("되돌리기 · ${secondsLeft}초", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * C2-21: the device TTS engine, or null while there is none (or it has no Korean): then [번호 읽기] is hidden.
 * Speaks offline through the system engine; the app itself declares no INTERNET.
 */
@Composable
fun rememberTts(): TextToSpeech? {
    val context = LocalContext.current
    var ready by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            val t = tts ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS && t.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE) ready = t
        }
        onDispose { ready = null; tts?.stop(); tts?.shutdown() }
    }
    return ready
}
