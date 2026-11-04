package com.ouor.mycoupon.ui.component

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.OutlinedButton
import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
    CouponState.EXPIRED -> "기간이 지난 쿠폰이에요" to "${c.expiryDate!!.monthDay()}까지"
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

/**
 * design-system §6.14 (C2-04): replaces the CTA right after [사용 완료]; [secondsLeft] counts down outside.
 * [done] ("사용 완료했어요") is the first line and never cut; [name] goes under it, one line (code review U-20).
 */
@Composable
fun UndoBar(done: String, name: String, secondsLeft: Int, onUndo: () -> Unit) {
    // code review U-01: one fixed sentence, announced once when the bar appears; the ticking seconds stay on the button only
    val spoken = remember(done, name) { "$name $done, 되돌리기 ${secondsLeft}초" }
    val view = LocalView.current
    @Suppress("DEPRECATION") // the replacement (pane titles / live regions) re-reads on every change, which is the bug
    LaunchedEffect(spoken) { view.announceForAccessibility(spoken) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // FlowRow-like: the text wraps above the button at large font sizes instead of squeezing it
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                    Text(done, style = MaterialTheme.typography.bodyLarge)
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.heightIn(min = Spacing.s))
                // round-2 V10: a secondary (outlined) button, and the number says it is seconds
// C2Q-04: M3's default border is outlineVariant, invisible on this bar; outline is the design-system 3:1 border
                OutlinedButton(onUndo, Modifier.fillMaxWidth().heightIn(min = Spacing.cta), shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Text("되돌리기 · ${secondsLeft}초", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * C2-21: the device TTS engine. Code review U-23: one per app window, bound the first time S6 asks and kept until the app
 * closes, so ‹ › between coupons does not bind it again and [번호 읽기] is there at once from the second S6 on.
 * Speaks offline through the system engine; the app itself declares no INTERNET.
 */
class TtsHolder(private val context: Context) {
    /** null while there is no engine (or it has no Korean): then [번호 읽기] is hidden */
    var ready by mutableStateOf<TextToSpeech?>(null)
        private set
    private var tts: TextToSpeech? = null

    fun bind() {
        if (tts != null) return
        var t: TextToSpeech? = null
        t = TextToSpeech(context.applicationContext) { status ->
            val e = t ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS && e.setLanguage(Locale.KOREAN) >= TextToSpeech.LANG_AVAILABLE) ready = e
        }
        tts = t
    }

    fun shutdown() { ready = null; tts?.stop(); tts?.shutdown(); tts = null }
}

val LocalTts = staticCompositionLocalOf<TtsHolder?> { null }

@Composable
fun rememberTts(): TextToSpeech? {
    val holder = LocalTts.current ?: return null
    LaunchedEffect(holder) { holder.bind() }
    DisposableEffect(holder) { onDispose { holder.ready?.stop() } } // leaving S6 stops reading the number
    return holder.ready
}
