package com.ouor.mycoupon.ui.coupon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.ui.component.BrightnessChip
import com.ouor.mycoupon.ui.component.PastCouponBand
import com.ouor.mycoupon.ui.component.UndoBar
import com.ouor.mycoupon.ui.component.codeSpoken
import com.ouor.mycoupon.ui.component.pastBand
import com.ouor.mycoupon.ui.component.rememberTts
import com.ouor.mycoupon.ui.component.undoMillis
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.ui.home.HomeViewModel
import com.ouor.mycoupon.ui.MainActivity
import com.ouor.mycoupon.ui.Route
import com.ouor.mycoupon.ui.Sharing
import com.ouor.mycoupon.ui.component.AppSheet
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.BarcodeBlock
import com.ouor.mycoupon.ui.component.BottomCta
import com.ouor.mycoupon.ui.component.CouponState
import com.ouor.mycoupon.ui.component.CouponThumb
import com.ouor.mycoupon.ui.component.DdayBadge
import com.ouor.mycoupon.ui.component.EmptyState
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.PrimaryButton
import com.ouor.mycoupon.ui.component.SecondaryButton
import com.ouor.mycoupon.ui.component.dotted
import com.ouor.mycoupon.ui.component.epochDay
import com.ouor.mycoupon.ui.component.isLargeFont
import com.ouor.mycoupon.ui.component.loadImage
import com.ouor.mycoupon.ui.component.monthDay
import com.ouor.mycoupon.ui.component.state
import com.ouor.mycoupon.ui.component.title
import com.ouor.mycoupon.ui.component.won
import com.ouor.mycoupon.ui.theme.MyCouponTheme
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors
import com.ouor.mycoupon.ui.toHome
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * F6: max brightness + keep-on for this window while [on]; restored on leave. Bars follow the system theme (S6 is no longer forced light, U11).
 * Returns whether the window really is at full brightness (C2-01: the chip shows only then).
 */
@Composable
internal fun CheckoutWindow(on: Boolean): Boolean {
    val activity = LocalActivity.current as Activity
    var applied by remember { mutableStateOf(false) }
    // counted: S6 and S7 overlap during the navigation transition, the last one out restores
    DisposableEffect(on) {
        val w = activity.window
        if (on) {
            Checkout.bright++
            w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        applied = on && w.attributes.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL &&
            w.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        onDispose {
            if (on && --Checkout.bright == 0) {
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    // security review S-12: the recents thumbnail (saved to disk) never shows the barcode; screenshots stay allowed
    if (Build.VERSION.SDK_INT >= 33) DisposableEffect(Unit) {
        if (Checkout.hidden++ == 0) activity.setRecentsScreenshotEnabled(false)
        onDispose { if (--Checkout.hidden == 0) activity.setRecentsScreenshotEnabled(true) }
    }
    return applied
}

private object Checkout {
    var bright = 0
    var hidden = 0
}

/** C2-21: number (and 인증번호) read aloud by the device TTS, the same words TalkBack uses (C2-05). */
internal fun speakCode(tts: TextToSpeech, c: Coupon) {
    val text = listOfNotNull(c.code?.let { "번호, " + codeSpoken(it) }, c.authCode?.let { "인증번호, " + codeSpoken(it) }).joinToString(". ")
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "code")
}

/** S6 (F6, F7, F12, F16, C2-01~06, 16, 17, 19~21, 25~27) with S8, S12, F10. Follows the system theme; only the barcode card is always white (D6, U11). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(id: Long, fromReminder: Boolean, nav: NavHostController, swiped: Boolean = false, openUse: Boolean = false) = MyCouponTheme {
    val activity = LocalActivity.current as MainActivity
    val vm: CouponViewModel = hiltViewModel()
    val homeVm: HomeViewModel = hiltViewModel(activity)
    LaunchedEffect(id) { vm.load(id, fromReminder) }
    val c by vm.coupon.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var useSheet by remember { mutableStateOf(openUse) }
    var askGiven by remember { mutableStateOf(false) }
    var givenTo by rememberSaveable { mutableStateOf("") }
    var askTrash by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var reminderSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    /** C2-02: a past coupon's barcode after [그래도 보기] (this visit only) */
    var revealed by remember { mutableStateOf(false) }
    /** set when this screen just ended the coupon: then F10, then back to S1 with an undo Snackbar */
    var finishing by remember { mutableStateOf<String?>(null) }
    /** C2-04: seconds left on the undo bar after [사용 완료] (review U5 replaced the confirm sheet) */
    var undoLeft by remember { mutableStateOf(0) }
    val undoSeconds = remember { (undoMillis(activity) / 1000).toInt() }
    val openedAt = remember { SystemClock.elapsedRealtime() }
    // C2-04: right after a swipe to this coupon the CTA ignores presses for a second (a palm swipe must not mark it used)
    // and right after an undo, so a second tap on the same spot does not mark it used again
    var ctaFrom by remember { mutableLongStateOf(if (swiped) openedAt + 1_000 else 0L) }
    val tts = rememberTts()

    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.trashAnswered(); finishing?.let { leave(nav, snackbar, homeVm, id, it) } }
    // C2-26 (QA R2-01): ask "넘겼나요?" only when the chooser reports a picked target, not when the share sheet was just closed
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { askGiven = true } }
        ContextCompat.registerReceiver(activity, r, IntentFilter(Sharing.ACTION_CHOSEN), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { activity.unregisterReceiver(r) }
    }
    // C2-17: an available coupon looked at for a while and left without a use is asked about once on S1 (the core applies the 15 s rule)
    val latest by rememberUpdatedState(c)
    DisposableEffect(id) {
        onDispose {
            latest?.takeIf { it.id == id && it.status == CouponStatus.ACTIVE }?.let { vm.lingered(id, SystemClock.elapsedRealtime() - openedAt) }
        }
    }

    val missing by vm.missing.collectAsStateWithLifecycle()
    if (c == null && missing) {
        Scaffold(topBar = { AppTopBar("", onBack = { nav.toHome() }) }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.SearchOff, "이 쿠폰을 찾을 수 없어요", "지웠거나 백업에서 다시 불러온 쿠폰이에요. 목록에서 찾아 주세요",
                    primary = "내 쿠폰 목록" to { nav.toHome() })
            }
        }
        return@MyCouponTheme
    }
    val coupon = c ?: return@MyCouponTheme
    val today = LocalDate.now()
    val nm = coupon.title()
    // C2-02: used / given / expired, not while this screen is finishing its own [사용 완료]
    val past = finishing == null && pastBand(coupon, today) != null
    val dimmed = past && !revealed
    val bright = CheckoutWindow(on = !dimmed && (coupon.status == CouponStatus.ACTIVE || revealed))
    // review U13: swipe (or ‹ ›) to the neighbouring available coupon in the S1 order, without going back to the list
    val home by homeVm.state.collectAsStateWithLifecycle()
    val ring = remember(home.coupons, today) { home.coupons.filter { it.isAvailable(today) }.map { it.id } }
    val pos = ring.indexOf(id)
    val go = { step: Int ->
        if (pos >= 0) ring.getOrNull(pos + step)?.let { n -> nav.navigate(Route.Detail(n, swiped = true)) { popUpTo<Route.Detail> { inclusive = true } } }
    }

    LaunchedEffect(coupon.status, finishing) {
        val msg = finishing ?: return@LaunchedEffect
        if (coupon.status != CouponStatus.USED) return@LaunchedEffect
        while (undoLeft > 0) { delay(1000); undoLeft-- }
        // C2-04 (core-found defect): an undo tapped while this coroutine was waking must win over leaving the screen
        if (finishing == null) return@LaunchedEffect
        // F10: offer to trash the gallery original once (API 30+, MediaStore uri that still exists)
        val canTrash = !coupon.trashAsked && Build.VERSION.SDK_INT >= 30 && coupon.sourceUri?.startsWith("content://media/") == true &&
            withContext(Dispatchers.IO) {
                // code review A7: without gallery permission (picker / share, the usual path) another app's photo can't be opened,
                // so "can't tell" means try; the system trash dialog itself reports a missing photo
                try { activity.contentResolver.openInputStream(Uri.parse(coupon.sourceUri))?.close(); true }
                catch (_: SecurityException) { true }
                catch (_: Exception) { false }
            }
        if (canTrash) askTrash = true else { if (!coupon.trashAsked) vm.trashAnswered(); leave(nav, snackbar, homeVm, id, msg) }
    }

    Scaffold(
        topBar = {
            AppTopBar("", onBack = { nav.popBackStack() }) {
                // C2-06: 56 dp targets, full-contrast icons and position
                if (pos >= 0 && ring.size > 1 && undoLeft == 0) {
                    IconButton({ go(-1) }, Modifier.size(Spacing.cta), enabled = pos > 0) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, if (pos > 0) "이전 쿠폰" else "이전 쿠폰 없음")
                    }
                    Text("${pos + 1}/${ring.size}", Modifier.semantics { contentDescription = "${ring.size}개 중 ${pos + 1}번째" },
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurface)
                    IconButton({ go(1) }, Modifier.size(Spacing.cta), enabled = pos < ring.lastIndex) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, if (pos < ring.lastIndex) "다음 쿠폰" else "다음 쿠폰 없음")
                    }
                }
                Box {
                    IconButton({ menu = true }, Modifier.size(Spacing.cta)) { Icon(Icons.Rounded.MoreVert, "더 보기") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("수정") }, { menu = false; nav.navigate(Route.EditCoupon(id)) },
                            Modifier.heightIn(min = Spacing.cta), leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                        DropdownMenuItem({ Text("삭제") }, { menu = false; confirmDelete = true },
                            Modifier.heightIn(min = Spacing.cta), leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                    }
                }
            }
        },
        bottomBar = {
            when {
                // C2-04: the CTA spot becomes "〈상품명〉 사용 완료했어요" + [되돌리기 · 8], then F10 and S1 (+ Snackbar undo)
                undoLeft > 0 -> UndoBar("$nm 사용 완료했어요", undoLeft) {
                    undoLeft = 0; finishing = null; ctaFrom = SystemClock.elapsedRealtime() + 1_000
                    vm.undoUse(); snackbar.show("다시 쓸 수 있게 되돌렸어요")
                }
                finishing != null -> Unit // leaving: F10 sheet or S1 next
                coupon.status != CouponStatus.ACTIVE -> BottomCta("되돌리기", { vm.undoUse(); revealed = false; snackbar.show("다시 쓸 수 있게 되돌렸어요") }, outlined = true)
                // review U4: the amount CTA says what it does ("사용 기록" read as "show history")
                coupon.type == CouponType.AMOUNT -> BottomCta("쓴 금액 입력", { useSheet = true })
                else -> BottomCta("사용 완료", {
                    if (SystemClock.elapsedRealtime() >= ctaFrom) { vm.markUsed(); undoLeft = undoSeconds; finishing = "$nm 사용 완료했어요" }
                })
            }
        },
        snackbarHost = { SnackbarHost(snackbar.host) },
    ) { pad ->
        val title = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { title.requestFocus() } }
        var drag by remember { mutableStateOf(0f) }
        Column(Modifier.padding(pad).fillMaxSize()
            .pointerInput(pos, ring) {
                // C2-04: a quarter of the screen width, so a brushing palm does not switch coupons
                if (pos >= 0) detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { _, d -> drag += d },
                    onDragEnd = { val t = size.width / 4f; if (drag < -t) go(1) else if (drag > t) go(-1) })
            }
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l)) {
            // C2-02: the band first (also TalkBack's first stop), then the title
            if (past) PastCouponBand(coupon, today, Modifier.padding(bottom = Spacing.m).focusRequester(title).focusable())
            // C2-03: "〈브랜드〉에서 사용" always (never hidden at 200 %), the product name in at most two lines
            coupon.brand?.let { Text("${it}에서 사용", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
            Text(nm, (if (past) Modifier else Modifier.focusRequester(title).focusable()).semantics { heading() }, // A19: focusable, or requestFocus fails silently
                style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(Modifier.padding(top = Spacing.xs, bottom = Spacing.s), itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                DdayBadge(coupon, today)
                val sub = when (coupon.state(today)) {
                    CouponState.USED, CouponState.GIVEN -> coupon.usedAt?.let { epochDay(it).monthDay() }
                    else -> coupon.expiryDate?.let { it.dotted() + "까지" }
                }
                sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            // C2-27: the balance sits above the barcode (what the clerk asks first)
            if (coupon.type == CouponType.AMOUNT) {
                val bal = coupon.balance ?: coupon.faceValue
                // QA R1-05: FlowRow so "사용 기록 N건" drops to its own line at 200 % instead of being squeezed letter by letter
                FlowRow(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).clickable(enabled = usage.isNotEmpty()) { history = !history }
                    .padding(bottom = Spacing.s), itemVerticalAlignment = Alignment.CenterVertically) {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    val big = MaterialTheme.typography.titleLarge.toSpanStyle()
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = muted)) { append("잔액 ") }
                        withStyle(big) { append(bal?.let(::won) ?: "-") }
                        coupon.faceValue?.let { withStyle(SpanStyle(color = muted)) { append(" / ${won(it)}") } }
                    }, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
                    Spacer(Modifier.weight(1f))
                    if (usage.isNotEmpty()) Row(Modifier.heightIn(min = Spacing.touch), verticalAlignment = Alignment.CenterVertically) {
                        Text("사용 기록 ${usage.size}건", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Icon(if (history) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (bright) BrightnessChip(Modifier.padding(bottom = Spacing.s))
            if (coupon.code == null || showOriginal) OriginalImage(coupon.imagePath, Modifier.padding(bottom = Spacing.m))
            if (coupon.code != null) BarcodeBlock(coupon, onExpand = { nav.navigate(Route.BarcodeFull(id)) },
                onCopied = { auth -> snackbar.show(if (auth) "인증번호를 복사했어요" else "번호를 복사했어요") },
                dimmed = dimmed, onReveal = { revealed = true }, onSpeak = tts?.let { t -> { speakCode(t, coupon) } })
            else Notice("번호가 없어요. 원본 이미지를 보여 주세요")
            // C2Q-02: the opened history goes under the number, so at 200 % it never pushes the number under the bottom CTA
            if (coupon.type == CouponType.AMOUNT && history) UsageHistory(usage) { vm.undoLastBalance(); snackbar.show("마지막 기록을 지우고 잔액을 되돌렸어요") }

            // C2-20: the S7 mode is a visible button, not only a tap on the barcode
            if (coupon.code != null && !dimmed) OutlinedButton({ nav.navigate(Route.BarcodeFull(id)) },
                Modifier.fillMaxWidth().padding(top = Spacing.m).heightIn(min = Spacing.cta), shape = MaterialTheme.shapes.large) {
                Icon(Icons.Rounded.Storefront, null)
                Spacer(Modifier.width(Spacing.s))
                Text("점원에게 보여 주기", style = MaterialTheme.typography.labelLarge)
            }
            // R3: secondary actions in plain sight (원본 · 알림 · 보내기)
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (coupon.code != null) QuickAction(Icons.Rounded.Image, if (showOriginal) "원본 닫기" else "원본 보기", Modifier.weight(1f)) { showOriginal = !showOriginal }
                QuickAction(Icons.Rounded.Notifications, "알림", Modifier.weight(1f)) { reminderSheet = true }
                QuickAction(Icons.AutoMirrored.Rounded.Send, "보내기", Modifier.weight(1f)) {
                    // code review A21: copying a multi-MB original and stripping EXIF stays off the main thread
                    scope.launch {
                        val i = withContext(Dispatchers.IO) {
                            runCatching {
                                val f = File(coupon.imagePath)
                                val mime = Sharing.imageMime(f)
                                val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
                                Sharing.intent(activity, f, "coupon.$ext", mime, reportChoice = coupon.status == CouponStatus.ACTIVE)
                            }.getOrNull()
                        }
                        if (i != null) share.launch(i) else snackbar.show("이미지를 보내지 못했어요")
                    }
                }
            }
            // C2-16: the gallery original may go, the app keeps its own copy
            if (showOriginal || coupon.code == null) Text("앱에 사본이 있어 갤러리 원본을 지워도 계속 볼 수 있어요", Modifier.padding(bottom = Spacing.s),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val days = Stats.daysLeft(coupon, today)
            if (coupon.issuer?.contains("카카오") == true && days != null && days in 0..7 && coupon.status == CouponStatus.ACTIVE)
                Notice("카카오톡 선물함에서 기간 연장이 될 수 있어요")
            if (coupon.needsCheck && coupon.status == CouponStatus.ACTIVE) Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs).background(MaterialTheme.statusColors.reviewContainer, MaterialTheme.shapes.medium)
                    .padding(start = Spacing.m, end = Spacing.s, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.statusColors.review)
                Text("  확인할 항목이 있어요", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton({ nav.navigate(Route.EditCoupon(id)) }, Modifier.heightIn(min = Spacing.touch)) { Text("확인하기") }
            }
            coupon.notes?.let { notes ->
                TextButton({ notesOpen = !notesOpen }, Modifier.heightIn(min = Spacing.touch)) {
                    Text("유의사항", style = MaterialTheme.typography.labelLarge)
                    Icon(if (notesOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                if (notesOpen) Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            coupon.memo?.let { Text("메모: $it", Modifier.padding(vertical = Spacing.s), style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (useSheet) UseSheet(coupon, startSpent = vm.amountInputSpent, onMode = { vm.amountInputSpent = it }, onDismiss = { useSheet = false },
        onRemaining = { v ->
            useSheet = false
            // pre-checked in the sheet; a refusal here (stale balance) replaces the success Snackbar
            vm.recordRemaining(v) { e -> snackbar.show(usageMessage(e, coupon.balance ?: coupon.faceValue ?: 0, false)) }
            if (v == 0L) finishing = "$nm 사용 완료했어요"
            else snackbar.show("$nm 잔액 ${won(v)}으로 기록했어요", "되돌리기") { homeVm.undoLastBalance(id) }
        })
    if (askGiven) AppSheet({ askGiven = false }, "이 쿠폰을 넘겼나요?", "넘긴 쿠폰은 지난 쿠폰으로 옮겨 다시 쓰지 않게 해요") {
        // C2-25: optional name; the keyboard is not raised (leaving it empty is the default)
        OutlinedTextField(givenTo, { givenTo = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("누구에게(선택)") },
            placeholder = { Text("예: 김민지") }, singleLine = true)
        Spacer(Modifier.height(Spacing.l))
        PrimaryButton("넘겼어요", {
            askGiven = false; vm.markUsed(UsedReason.GIVEN, givenTo.trim().ifEmpty { null }); finishing = "$nm 넘긴 쿠폰으로 표시했어요"
        })
        SecondaryButton("아니요", { askGiven = false })
    }
    if (askTrash) AppSheet({ askTrash = false; vm.trashAnswered(); finishing?.let { leave(nav, snackbar, homeVm, id, it) } },
        "갤러리 원본 이미지를 휴지통으로 옮길까요?", "앱에 사본이 있어 갤러리 원본을 지워도 계속 볼 수 있어요. 휴지통에서 30일 안에 되살릴 수 있어요.") {
        CouponThumb(coupon.thumbPath, size = 96.dp)
        Spacer(Modifier.height(Spacing.l))
        PrimaryButton("휴지통으로 옮기기", {
            askTrash = false
            if (Build.VERSION.SDK_INT >= 30) runCatching {
                val pi = MediaStore.createTrashRequest(activity.contentResolver, listOf(Uri.parse(coupon.sourceUri)), true)
                trash.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            }.onFailure { vm.trashAnswered(); finishing?.let { leave(nav, snackbar, homeVm, id, it) } }
        })
        SecondaryButton("그대로 두기", { askTrash = false; vm.trashAnswered(); finishing?.let { leave(nav, snackbar, homeVm, id, it) } })
    }
    if (reminderSheet) ReminderSheet(coupon, onDismiss = { reminderSheet = false }) { vm.setReminderOffsets(it); reminderSheet = false; snackbar.show("알림을 바꿨어요") }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("이 쿠폰을 지울까요?") },
        text = { Text("지우면 되돌릴 수 없어요. 갤러리 사진은 그대로예요.") },
        confirmButton = {
            TextButton({ confirmDelete = false; scope.launch { vm.delete().join(); snackbar.show("쿠폰을 지웠어요"); nav.toHome() } }) {
                Text("지우기", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("취소") } },
    )
}

internal fun leave(nav: NavHostController, snackbar: com.ouor.mycoupon.ui.component.AppSnackbar, homeVm: HomeViewModel, id: Long, msg: String) {
    homeVm.arrivedFromFinish()
    nav.toHome()
    snackbar.show(msg, "되돌리기") { homeVm.undoUse(id) }
}

@Composable
private fun Notice(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs).background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
        .padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.s))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick, modifier.heightIn(min = Spacing.cta), shape = MaterialTheme.shapes.medium,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.xs, vertical = Spacing.s)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
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
fun ImageViewer(bmp: Bitmap, onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val tf = rememberTransformableState { z, pan, _ -> scale = (scale * z).coerceIn(1f, 8f); offset = if (scale == 1f) Offset.Zero else offset + pan }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(bmp.asImageBitmap(), "쿠폰 원본 이미지, 두 손가락으로 확대",
                Modifier.fillMaxSize().transformable(tf).graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                contentScale = ContentScale.Fit)
            IconButton(onClose, Modifier.align(Alignment.TopEnd).padding(Spacing.l)) { Icon(Icons.Rounded.Close, "닫기", tint = Color.White) }
        }
    }
}
