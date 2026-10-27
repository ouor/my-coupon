package com.ouor.mycoupon.ui.component

import android.content.ClipData
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
import com.ouor.mycoupon.data.ImageStore
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
    val bmp = ImageStore.decode(f.readBytes(), maxSide) ?: return@withContext null
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
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
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
 * design-system §6.4. Always white with black bars, whatever the theme (P5). Tap → S7 ([onExpand]).
 */
@Composable
fun BarcodeBlock(c: Coupon, onExpand: () -> Unit, onCopied: () -> Unit, modifier: Modifier = Modifier) {
    val code = c.code ?: return
    val context = LocalContext.current
    val view = c.codeView()
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = BarcodeColors.background,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(vertical = Spacing.l, horizontal = Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
            if (view != CodeView.PIN) {
                Column(Modifier.fillMaxWidth().clickable(onClickLabel = "크게 보기", onClick = onExpand), horizontalAlignment = Alignment.CenterHorizontally) {
                    BarcodeImage(c)
                }
                if (view == CodeView.ORIGINAL) Text("원본 이미지예요. 안 찍히면 번호를 불러 주세요",
                    Modifier.padding(top = Spacing.s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.m))
            }
            CodeNumber(code, if (view == CodeView.PIN) CodeTypography.codePin else CodeTypography.codeLarge,
                Modifier.fillMaxWidth().clickable(onClickLabel = "크게 보기", onClick = onExpand).padding(vertical = Spacing.s))
            // C9: a corrected number is never redrawn; the original image may still show the old one
            if (view == CodeView.ORIGINAL && c.codeSource == CodeSource.USER) Text("직접 고친 번호예요. 계산대에서는 이 번호를 불러 주세요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.s))
            OutlinedButton({ copyCode(context, code); onCopied() }, Modifier.heightIn(min = Spacing.touch)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text("번호 복사", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
