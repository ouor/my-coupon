package com.ouor.mycoupon.ui.coupon

import com.ouor.mycoupon.ui.component.Ink
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.semantics.Role
import com.ouor.mycoupon.ui.component.Group
import com.ouor.mycoupon.ui.component.GroupDivider
import com.ouor.mycoupon.ui.component.InfoBanner
import com.ouor.mycoupon.ui.component.InkTextButton
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.ui.Route
import com.ouor.mycoupon.ui.Sharing
import com.ouor.mycoupon.ui.component.BarcodeBlock
import com.ouor.mycoupon.ui.component.CouponState
import com.ouor.mycoupon.ui.component.DdayBadge
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.PastCouponBand
import com.ouor.mycoupon.ui.component.dotted
import com.ouor.mycoupon.ui.component.epochDay
import com.ouor.mycoupon.ui.component.loadImage
import com.ouor.mycoupon.ui.component.monthDay
import com.ouor.mycoupon.ui.component.rememberTts
import com.ouor.mycoupon.ui.component.state
import com.ouor.mycoupon.ui.component.title
import com.ouor.mycoupon.ui.component.won
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The S6 page under the top bar: title, balance, barcode card, actions and notes. [swipe] null = no neighbours to swipe to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailContent(
    coupon: Coupon, st: DetailState, today: LocalDate, past: Boolean, dimmed: Boolean, bright: Boolean,
    vm: CouponViewModel, nav: NavHostController, scroll: ScrollState, modifier: Modifier, swipe: ((Int) -> Unit)?,
) {
    val usage by vm.usage.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val tts = rememberTts()
    val id = coupon.id
    var drag by remember { mutableStateOf(0f) }
    Column(modifier.fillMaxSize()
        .pointerInput(swipe) {
            // C2-04: a quarter of the screen width, so a brushing palm does not switch coupons
            if (swipe != null) detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { _, d -> drag += d },
                onDragEnd = { val t = size.width / 4f; if (drag < -t) swipe(1) else if (drag > t) swipe(-1) })
        }
        .verticalScroll(scroll).padding(horizontal = Spacing.l)) {
        // C2-02: the band first (also TalkBack's first stop after the top bar), then the title
        // code review U-14: no requestFocus here (it moved the keyboard focus only, not TalkBack's)
        if (past) PastCouponBand(coupon, today, Modifier.padding(bottom = Spacing.m))
        // C2-03: "〈브랜드〉에서 사용" always (never hidden at 200 %), the product name in at most two lines
        coupon.brand?.let { Text("${it}에서 사용", Modifier.padding(top = Spacing.xs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(coupon.title(), Modifier.padding(top = 2.dp).semantics { heading() }, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        // round-2 V7: the band already says the state and the date
        if (!past) FlowRow(Modifier.padding(top = Spacing.s, bottom = 14.dp), itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            DdayBadge(coupon, today)
            val sub = when (coupon.state(today)) {
                CouponState.USED, CouponState.GIVEN -> coupon.usedAt?.let { epochDay(it).monthDay() }
                else -> coupon.expiryDate?.let { it.dotted() + "까지" }
            }
            sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        // C2-27: the balance sits above the barcode (what the clerk asks first)
        if (coupon.type == CouponType.AMOUNT) BalanceRow(coupon, usage.size, st.history) { st.history = !st.history }

        if (coupon.code == null || st.showOriginal) OriginalImage(coupon.imagePath, Modifier.padding(bottom = Spacing.m))
        if (coupon.code != null) BarcodeBlock(coupon, onExpand = { nav.navigate(Route.BarcodeFull(id)) },
            onCopied = { auth -> snackbar.show(if (auth) "인증번호를 복사했어요" else "번호를 복사했어요") },
            dimmed = dimmed, onReveal = { st.revealed = true }, onSpeak = tts?.let { t -> { speakCode(t, coupon) } }, bright = bright)
        else Notice("번호가 없어요")
        // C2Q-02: the opened history goes under the number, so at 200 % it never pushes the number under the bottom CTA
        if (coupon.type == CouponType.AMOUNT && st.history) UsageHistory(usage) { logId ->
            // code review U-07: the record the user saw is the one removed, and a double tap cannot reach the next one
            if (SystemClock.elapsedRealtime() >= st.deleteFrom) {
                st.deleteFrom = SystemClock.elapsedRealtime() + 1_000
                vm.undoLastBalance(logId); snackbar.show("마지막 기록을 지우고 잔액을 되돌렸어요")
            }
        }

        // redesign: the actions are one group under the barcode — [점원에게 보여 주기] row (C2-20, S7 is a visible
        // control, not only a tap on the barcode), then 원본 · 알림 · 보내기 in three cells
        Group(Modifier.padding(top = Spacing.m, bottom = Spacing.m)) {
            if (coupon.code != null && !dimmed) {
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { nav.navigate(Route.BarcodeFull(id)) }.heightIn(min = Spacing.cta)
                    .padding(start = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ink.Store, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(Spacing.m))
                    Text("점원에게 보여 주기", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(Ink.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.statusColors.faint)
                }
                GroupDivider()
            }
            QuickActions(coupon, st)
        }
        val days = Stats.daysLeft(coupon, today)
        if (coupon.issuer?.contains("카카오") == true && days != null && days in 0..7 && coupon.status == CouponStatus.ACTIVE)
            Notice("카카오톡 선물함에서 기간 연장이 될 수 있어요")
        if (coupon.needsCheck && coupon.status == CouponStatus.ACTIVE) InfoBanner(Ink.Alert, "확인할 항목이 있어요",
            Modifier.padding(vertical = Spacing.xs), accent = MaterialTheme.statusColors.review, inlineActions = true) {
            InkTextButton("확인하기", { nav.navigate(Route.EditCoupon(id)) })
        }
        coupon.notes?.let { notes ->
            // code review U-16: says whether it is open
            TextButton({ st.notesOpen = !st.notesOpen }, Modifier.heightIn(min = Spacing.touch).semantics { stateDescription = if (st.notesOpen) "펼쳐짐" else "접힘" }) {
                Text("유의사항", style = MaterialTheme.typography.labelLarge)
                Icon(if (st.notesOpen) Ink.ExpandLess else Ink.ExpandMore, null)
            }
            if (st.notesOpen) Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        coupon.memo?.let { Text("메모: $it", Modifier.padding(vertical = Spacing.s), style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(Spacing.xl))
    }
}

/** "잔액 12,400원 / 30,000원" with the history toggle. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BalanceRow(coupon: Coupon, records: Int, open: Boolean, onToggle: () -> Unit) {
    val bal = coupon.balance ?: coupon.faceValue
    // QA R1-05: FlowRow so "사용 기록 N건" drops to its own line at 200 % instead of being squeezed letter by letter
    // code review U-16: the toggle says "펼쳐짐 / 접힘"
    FlowRow(Modifier.fillMaxWidth().heightIn(min = Spacing.touch)
        .clickable(enabled = records > 0, onClickLabel = if (open) "사용 기록 접기" else "사용 기록 펼치기", onClick = onToggle)
        .semantics { if (records > 0) stateDescription = if (open) "펼쳐짐" else "접힘" }
        .padding(bottom = Spacing.s), itemVerticalAlignment = Alignment.CenterVertically) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val big = MaterialTheme.typography.titleLarge.toSpanStyle()
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = muted)) { append("잔액 ") }
            withStyle(big) { append(bal?.let(::won) ?: "-") }
            coupon.faceValue?.let { withStyle(SpanStyle(color = muted)) { append(" / ${won(it)}") } }
        }, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
        Spacer(Modifier.weight(1f))
        if (records > 0) Row(Modifier.heightIn(min = Spacing.touch), verticalAlignment = Alignment.CenterVertically) {
            Text("사용 기록 ${records}건", style = MaterialTheme.typography.labelLarge)
            Icon(if (open) Ink.ExpandLess else Ink.ExpandMore, null)
        }
    }
}

/** R3: secondary actions in plain sight (원본 · 알림 · 보내기). */
@Composable
private fun QuickActions(coupon: Coupon, st: DetailState) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        val sep = @Composable { VerticalDivider(Modifier.padding(vertical = 14.dp), thickness = 1.dp, color = MaterialTheme.statusColors.line) }
        if (coupon.code != null) { QuickAction(Ink.Image, if (st.showOriginal) "원본 닫기" else "원본 보기", Modifier.weight(1f)) { st.showOriginal = !st.showOriginal }; sep() }
        QuickAction(Ink.Bell, "알림", Modifier.weight(1f)) { st.reminderSheet = true }
        sep()
        QuickAction(Ink.Send, "보내기", Modifier.weight(1f)) {
            // code review A21: copying a multi-MB original and stripping EXIF stays off the main thread
            scope.launch {
                val i = withContext(Dispatchers.IO) {
                    runCatching {
                        val f = File(coupon.imagePath)
                        val mime = Sharing.imageMime(f)
                        val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
                        Sharing.intent(context, f, "coupon.$ext", mime, reportChoice = coupon.status == CouponStatus.ACTIVE)
                    }.getOrNull()
                }
                if (i != null) share.launch(i) else snackbar.show("이미지를 보내지 못했어요")
            }
        }
    }
}

@Composable
private fun Notice(text: String) = InfoBanner(Ink.Info, text, Modifier.padding(vertical = Spacing.xs))

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    // redesign: a cell of the actions group (icon over label), not a third button shape
    Column(modifier.clickable(role = Role.Button, onClick = onClick).heightIn(min = 60.dp).padding(horizontal = Spacing.xs, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(22.dp))
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OriginalImage(path: String, modifier: Modifier = Modifier) {
    var bmp by remember(path) { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableStateOf(false) }
    LaunchedEffect(path) { bmp = loadImage(path, 2400) }
    bmp?.let {
        Image(it.asImageBitmap(), "원본 이미지, 누르면 크게 보기", modifier.fillMaxWidth().heightIn(max = 480.dp).clickable { zoom = true },
            contentScale = ContentScale.Fit)
        if (zoom) ImageViewer(it) { zoom = false }
    }
}

/** Full-screen pinch-zoom viewer for the original image. */
@Composable
private fun ImageViewer(bmp: Bitmap, onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val tf = rememberTransformableState { z, pan, _ -> scale = (scale * z).coerceIn(1f, 8f); offset = if (scale == 1f) Offset.Zero else offset + pan }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(bmp.asImageBitmap(), "쿠폰 원본 이미지, 두 손가락으로 확대",
                Modifier.fillMaxSize().transformable(tf).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                contentScale = ContentScale.Fit)
            IconButton(onClose, Modifier.align(Alignment.TopEnd).padding(Spacing.l)) { Icon(Ink.Close, "닫기", tint = Color.White) }
        }
    }
}
