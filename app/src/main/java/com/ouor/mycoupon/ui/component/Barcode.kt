package com.ouor.mycoupon.ui.component

import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import android.content.ClipData
import com.ouor.mycoupon.image.ImageCodec
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ouor.mycoupon.data.CodeSource
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.ui.BarcodeRenderer
import com.ouor.mycoupon.ui.theme.BarcodeColors
import com.ouor.mycoupon.ui.theme.CodeTypography
import com.ouor.mycoupon.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** How S6/S7 shows the code (design-system §6.4). */
enum class CodeView { RENDERED, ORIGINAL, PIN, NONE }

fun Coupon.codeView(): CodeView = when {
    code == null -> CodeView.NONE
    codeSource == CodeSource.DECODED && BarcodeRenderer.canRender(codeFormat) -> CodeView.RENDERED
    codeFormat == "PIN" -> CodeView.PIN
    else -> CodeView.ORIGINAL
}

/** Decodes an app image off the main thread; [box] (original px) crops with a margin. Null when unreadable. */
suspend fun loadImage(path: String, maxSide: Int, box: List<Double>? = null, srcW: Int = 0): Bitmap? = withContext(Dispatchers.IO) {
    val f = File(path)
    if (!f.exists()) return@withContext null
    val bmp = ImageCodec.decode(f.readBytes(), maxSide) ?: return@withContext null
    if (box == null || box.size < 4 || srcW <= 0) return@withContext bmp
    val k = bmp.width.toDouble() / srcW
    val padX = (box[2] - box[0]) * 0.08 + 12
    val padY = (box[3] - box[1]) * 0.25 + 12
    val x0 = ((box[0] - padX) * k).toInt().coerceIn(0, bmp.width - 1)
    val y0 = ((box[1] - padY) * k).toInt().coerceIn(0, bmp.height - 1)
    val x1 = ((box[2] + padX) * k).toInt().coerceIn(x0 + 1, bmp.width)
    val y1 = ((box[3] + padY) * k).toInt().coerceIn(y0 + 1, bmp.height)
    Bitmap.createBitmap(bmp, x0, y0, x1 - x0, y1 - y0)
}

/** Clipboard with the sensitive flag: the keyboard clipboard preview does not show the number. */
fun copyCode(context: Context, code: String) {
    val clip = ClipData.newPlainText("쿠폰 번호", code)
    clip.description.extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= 33) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) else putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT <= 32) clearLater(cm, code) // 33+ clears the clipboard by itself
}

/** Security review S-14: the copied number leaves the clipboard after 90 s unless something else was copied since. */
private fun clearLater(cm: ClipboardManager, code: String) {
    var replaced = false
    val listener = ClipboardManager.OnPrimaryClipChangedListener { replaced = true }
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    main.post { cm.addPrimaryClipChangedListener(listener) } // after our own change has been dispatched
    main.postDelayed({
        cm.removePrimaryClipChangedListener(listener)
        // in the background (API 29+) the clip is unreadable: then rely on the change listener alone
        val now = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        if (if (now != null) now == code else !replaced) {
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }, 90_000)
}

/** The barcode image only (shared by S6 and S7). */
@Composable
fun BarcodeImage(c: Coupon, modifier: Modifier = Modifier) {
    val code = c.code ?: return
    when (c.codeView()) {
        CodeView.RENDERED -> BoxWithConstraints(modifier.fillMaxWidth()) {
            val px = with(LocalDensity.current) { maxWidth.roundToPx() }
            val twoD = BarcodeRenderer.is2d(c.codeFormat)
            val h1d = maxOf(with(LocalDensity.current) { 96.dp.roundToPx() }, (px * 0.35).toInt())
            val bmp = remember(code, c.codeFormat, px) {
                BarcodeRenderer.render(code, c.codeFormat!!, if (twoD) (px * 0.7).toInt() else px, h1d)
            }
            // shown at its own pixel size: integer module width, no resampling blur (§6.4)
            if (bmp != null) Image(
                bmp.asImageBitmap(), "바코드",
                Modifier.align(Alignment.Center).size(with(LocalDensity.current) { bmp.width.toDp() }, with(LocalDensity.current) { bmp.height.toDp() }),
                contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None,
            )
        }
        CodeView.ORIGINAL -> {
            var bmp by remember(c.imagePath) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(c.imagePath) { bmp = loadImage(c.imagePath, 2400, c.fieldBoxes["code"], c.imageWidth) }
            bmp?.let { Image(it.asImageBitmap(), "바코드, 원본 이미지", modifier.fillMaxWidth().heightIn(max = 320.dp), contentScale = ContentScale.Fit) }
        }
        CodeView.PIN, CodeView.NONE -> {}
    }
}

/** Number in groups of four that wrap as a whole (§2.3-5); TalkBack reads "1234, 5678". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodeNumber(code: String, style: TextStyle, modifier: Modifier = Modifier) {
    FlowRow(modifier.clearAndSetSemantics { contentDescription = codeSpoken(code) },
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        codeGroups(code).forEach { Text(it, style = style, color = BarcodeColors.bar) }
    }
}

/**
 * design-system §6.4. Always white with black bars, whatever the theme (P5): the card has its own light theme, so its texts
 * and button stay readable on white in dark mode (U11). Tap → S7 ([onExpand], "점원에게 보여 주기", C2-20).
 * [dimmed] (C2-02, past coupon): bars and number at alpha 0.25 with [그래도 보기] → [onReveal].
 * [onSpeak] (C2-21) is null when the device has no TTS engine: then [번호 읽기] is hidden.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BarcodeBlock(
    c: Coupon, onExpand: () -> Unit, onCopied: (auth: Boolean) -> Unit, modifier: Modifier = Modifier,
    dimmed: Boolean = false, onReveal: () -> Unit = {}, onSpeak: (() -> Unit)? = null,
) = com.ouor.mycoupon.ui.theme.MyCouponTheme(forceLight = true) {
    val code = c.code ?: return@MyCouponTheme
    val context = LocalContext.current
    val view = c.codeView()
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = BarcodeColors.background,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(contentAlignment = Alignment.Center) {
            Column(Modifier.padding(vertical = Spacing.l, horizontal = Spacing.l).alpha(if (dimmed) 0.25f else 1f)
                .then(if (dimmed) Modifier.clearAndSetSemantics {} else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                if (view != CodeView.PIN) {
                    Column(Modifier.fillMaxWidth().clickable(enabled = !dimmed, onClickLabel = "점원에게 보여 주기", onClick = onExpand)
                        .clearAndSetSemantics {
                            contentDescription = "${listOfNotNull(c.brand, c.name()).joinToString(" ")} 바코드, 번호 ${codeSpoken(code)}"
                            if (!dimmed) onClick("점원에게 보여 주기") { onExpand(); true }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        BarcodeImage(c)
                    }
                    if (view == CodeView.ORIGINAL) Text("원본 이미지예요. 안 찍히면 번호를 불러 주세요",
                        Modifier.padding(top = Spacing.s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.m))
                }
                CodeNumber(code, if (view == CodeView.PIN) CodeTypography.codePin else CodeTypography.codeLarge,
                    Modifier.fillMaxWidth().clickable(enabled = !dimmed, onClickLabel = "점원에게 보여 주기", onClick = onExpand).padding(vertical = Spacing.s))
                // C9: a corrected number is never redrawn; the original image may still show the old one
                if (view == CodeView.ORIGINAL && c.codeSource == CodeSource.USER) Text("직접 고친 번호예요. 계산대에서는 이 번호를 불러 주세요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                c.authCode?.let { auth -> AuthCodeRow(auth) { copyCode(context, auth); onCopied(true) } }
                Spacer(Modifier.height(Spacing.s))
                // round-2 V7: a dimmed card has [그래도 보기] in this row's place instead of on top of the number
                if (dimmed) Spacer(Modifier.height(Spacing.cta))
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    OutlinedButton({ copyCode(context, code); onCopied(false) }, Modifier.heightIn(min = Spacing.touch), enabled = !dimmed) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.s))
                        Text("번호 복사", style = MaterialTheme.typography.labelLarge)
                    }
                    if (onSpeak != null) OutlinedButton(onSpeak, Modifier.heightIn(min = Spacing.touch), enabled = !dimmed) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.s))
                        Text("번호 읽기", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (dimmed) OutlinedButton(onReveal, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.l).heightIn(min = Spacing.cta), colors = ButtonDefaults.outlinedButtonColors(containerColor = BarcodeColors.background)) {
                Text("그래도 보기", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** C2-19: 인증번호·PIN under the number, same size, groups of four, its own [복사]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthCodeRow(auth: String, onCopy: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("인증번호", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CodeNumber(auth, CodeTypography.codeLarge, Modifier.fillMaxWidth())
        if (onCopy != null) TextButton(onCopy, Modifier.heightIn(min = Spacing.touch).semantics { contentDescription = "인증번호 복사" }) {
            Text("복사", style = MaterialTheme.typography.labelLarge)
        }
    }
}
