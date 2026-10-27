package com.ouor.mycoupon.ui.screen

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.rounded.SearchOff
import com.ouor.mycoupon.ui.component.EmptyState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.data.AppSettings
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.UsageLog
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.domain.UsageError
import com.ouor.mycoupon.domain.UsageInput
import com.ouor.mycoupon.domain.UsageInputException
import com.ouor.mycoupon.ocr.core.CouponType
import com.ouor.mycoupon.ui.CouponViewModel
import com.ouor.mycoupon.ui.HomeViewModel
import com.ouor.mycoupon.ui.MainActivity
import com.ouor.mycoupon.ui.Route
import com.ouor.mycoupon.ui.Sharing
import com.ouor.mycoupon.ui.component.AppSheet
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.BarcodeBlock
import com.ouor.mycoupon.ui.component.BarcodeImage
import com.ouor.mycoupon.ui.component.BottomCta
import com.ouor.mycoupon.ui.component.Chip
import com.ouor.mycoupon.ui.component.CodeNumber
import com.ouor.mycoupon.ui.component.CodeView
import com.ouor.mycoupon.ui.component.CouponState
import com.ouor.mycoupon.ui.component.CouponThumb
import com.ouor.mycoupon.ui.component.DdayBadge
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.PrimaryButton
import com.ouor.mycoupon.ui.component.SecondaryButton
import com.ouor.mycoupon.ui.component.StatusChip
import com.ouor.mycoupon.ui.component.codeView
import com.ouor.mycoupon.ui.component.dotted
import com.ouor.mycoupon.ui.component.epochDay
import com.ouor.mycoupon.ui.component.isLargeFont
import com.ouor.mycoupon.ui.component.loadImage
import com.ouor.mycoupon.ui.component.monthDay
import com.ouor.mycoupon.ui.component.state
import com.ouor.mycoupon.ui.component.won
import com.ouor.mycoupon.ui.theme.BarcodeColors
import com.ouor.mycoupon.ui.theme.CodeTypography
import com.ouor.mycoupon.ui.theme.MyCouponTheme
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors
import com.ouor.mycoupon.ui.toHome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** F6: max brightness + keep-on for this window while [on]; restored on leave. Light status-bar icons follow the forced light theme. */
@Composable
private fun CheckoutWindow(on: Boolean) {
    val activity = LocalContext.current as Activity
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    // counted: S6 and S7 overlap during the navigation transition, the last one out restores
    DisposableEffect(on) {
        val w = activity.window
        val ctl = WindowCompat.getInsetsController(w, w.decorView)
        ctl.isAppearanceLightStatusBars = true
        ctl.isAppearanceLightNavigationBars = true
        Checkout.lightBars++
        if (on) {
            Checkout.bright++
            w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (--Checkout.lightBars == 0) {
                ctl.isAppearanceLightStatusBars = !systemDark
                ctl.isAppearanceLightNavigationBars = !systemDark
            }
            if (on && --Checkout.bright == 0) {
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

private object Checkout {
    var bright = 0
    var lightBars = 0
}

/** S6 (F6, F7, F12, F16) with S8, S12, F10 trash suggestion. Always light (D6). */
@Composable
fun DetailScreen(id: Long, fromReminder: Boolean, nav: NavHostController) = MyCouponTheme(forceLight = true) {
    val activity = LocalContext.current as MainActivity
    val vm: CouponViewModel = hiltViewModel()
    val homeVm: HomeViewModel = hiltViewModel(activity)
    LaunchedEffect(id) { vm.load(id, fromReminder) }
    val c by vm.coupon.collectAsState()
    val usage by vm.usage.collectAsState()
    val snackbar = LocalSnackbar.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var useSheet by remember { mutableStateOf(false) }
    var askGiven by remember { mutableStateOf(false) }
    var askTrash by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var reminderSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    /** set when this screen just ended the coupon: then F10, then back to S1 with an undo Snackbar */
    var finishing by remember { mutableStateOf<String?>(null) }

    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { askGiven = true }
    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.trashAnswered(); finishing?.let { leave(nav, snackbar, homeVm, id, it) } }

    val missing by vm.missing.collectAsState()
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
    val state = coupon.state(today)
    CheckoutWindow(on = coupon.status == CouponStatus.ACTIVE)

    LaunchedEffect(coupon.status, finishing) {
        val msg = finishing ?: return@LaunchedEffect
        if (coupon.status != CouponStatus.USED) return@LaunchedEffect
        // F10: offer to trash the gallery original once (API 30+, MediaStore uri that still exists)
        val canTrash = !coupon.trashAsked && Build.VERSION.SDK_INT >= 30 && coupon.sourceUri?.startsWith("content://media/") == true &&
            withContext(Dispatchers.IO) { runCatching { activity.contentResolver.openInputStream(Uri.parse(coupon.sourceUri))?.close(); true }.getOrDefault(false) }
        if (canTrash) askTrash = true else { if (!coupon.trashAsked) vm.trashAnswered(); leave(nav, snackbar, homeVm, id, msg) }
    }

    Scaffold(
        topBar = {
            AppTopBar("", onBack = { nav.popBackStack() }) {
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "더 보기") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("수정") }, { menu = false; nav.navigate(Route.EditCoupon(id)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                        DropdownMenuItem({ Text("삭제") }, { menu = false; confirmDelete = true }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                    }
                }
            }
        },
        bottomBar = {
            when {
                coupon.status != CouponStatus.ACTIVE -> BottomCta("되돌리기", { vm.undoUse(); snackbar.show("다시 쓸 수 있게 되돌렸어요") }, outlined = true)
                else -> BottomCta(if (coupon.type == CouponType.AMOUNT) "사용 기록" else "사용 완료", { useSheet = true })
            }
        },
        snackbarHost = { SnackbarHost(snackbar.host) },
    ) { pad ->
        val large = isLargeFont()
        val title = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { title.requestFocus() } }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l)) {
            // short header so the barcode's top edge stays within 40 % of the screen (screens.md S6)
            if (!large) coupon.brand?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(coupon.productName ?: coupon.brand ?: "이름 없는 쿠폰", Modifier.focusRequester(title).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, maxLines = if (large) 1 else Int.MAX_VALUE)
            Row(Modifier.padding(top = Spacing.xs, bottom = Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                DdayBadge(coupon, today)
                val sub = when (state) {
                    CouponState.USED, CouponState.GIVEN -> coupon.usedAt?.let { epochDay(it).monthDay() }
                    else -> coupon.expiryDate?.let { it.dotted() + "까지" }
                }
                sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (state == CouponState.EXPIRED) Notice("기간이 지났어요. 매장에 따라 쓸 수 있을 수도 있어요")

            if (coupon.code == null || showOriginal) OriginalImage(coupon.imagePath, Modifier.padding(bottom = Spacing.m))
            if (coupon.code != null) BarcodeBlock(coupon, onExpand = { nav.navigate(Route.BarcodeFull(id)) }, onCopied = { snackbar.show("번호를 복사했어요") })
            else Notice("번호가 없어요. 원본 이미지를 보여 주세요")

            // R3: secondary actions in plain sight (원본 · 알림 · 보내기)
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (coupon.code != null) QuickAction(Icons.Rounded.Image, if (showOriginal) "원본 닫기" else "원본 보기", Modifier.weight(1f)) { showOriginal = !showOriginal }
                QuickAction(Icons.Rounded.Notifications, "알림", Modifier.weight(1f)) { reminderSheet = true }
                QuickAction(Icons.AutoMirrored.Rounded.Send, "보내기", Modifier.weight(1f)) {
                    val f = File(coupon.imagePath)
                    val mime = Sharing.imageMime(f)
                    val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
                    share.launch(Sharing.intent(activity, f, "coupon.$ext", mime))
                }
            }

            if (coupon.type == CouponType.AMOUNT) {
                val bal = coupon.balance ?: coupon.faceValue
                // QA R1-05: FlowRow so "사용 기록 N건" drops to its own line at 200 % instead of being squeezed letter by letter
                FlowRow(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).clickable(enabled = usage.isNotEmpty()) { history = !history }
                    .padding(bottom = Spacing.l), itemVerticalAlignment = Alignment.CenterVertically) {
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
                if (history) UsageHistory(usage) { vm.undoLastBalance(); snackbar.show("마지막 기록을 취소했어요") }
            }
            val days = Stats.daysLeft(coupon, today)
            if (coupon.issuer?.contains("카카오") == true && days != null && days in 0..7 && coupon.status == CouponStatus.ACTIVE)
                Notice("카카오톡 선물함에서 기간 연장이 될 수 있어요")
            if (coupon.needsCheck && coupon.status == CouponStatus.ACTIVE) Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs).background(MaterialTheme.statusColors.reviewContainer, MaterialTheme.shapes.medium)
                    .padding(start = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.statusColors.review)
                Text("  날짜를 확인해 주세요", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton({ nav.navigate(Route.EditCoupon(id)) }) { Text("확인하기") }
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

    if (useSheet) UseSheet(coupon, onDismiss = { useSheet = false },
        onUsed = { useSheet = false; vm.markUsed(); finishing = "사용 완료로 표시했어요" },
        onRemaining = { v ->
            useSheet = false
            // pre-checked in the sheet; a refusal here (stale balance) replaces the success Snackbar
            vm.recordRemaining(v) { e -> snackbar.show(usageMessage(e, coupon.balance ?: coupon.faceValue ?: 0, false)) }
            if (v == 0L) finishing = "사용 완료로 표시했어요"
            else snackbar.show("잔액 ${won(v)}으로 기록했어요", "되돌리기") { homeVm.undoLastBalance(id) }
        })
    if (askGiven) AppSheet({ askGiven = false }, "이 쿠폰을 넘겼나요?", "넘긴 쿠폰은 지난 쿠폰으로 옮겨 다시 쓰지 않게 해요") {
        PrimaryButton("넘겼어요", { askGiven = false; vm.markUsed(UsedReason.GIVEN); finishing = "넘긴 쿠폰으로 표시했어요" })
        SecondaryButton("아니요", { askGiven = false })
    }
    if (askTrash) AppSheet({ askTrash = false; vm.trashAnswered(); finishing?.let { leave(nav, snackbar, homeVm, id, it) } },
        "갤러리 원본 이미지를 휴지통으로 옮길까요?", "앱 안에 사본이 남아 있어요. 휴지통에서 30일 안에 되살릴 수 있어요.") {
        CouponThumb(coupon.thumbPath, size = 96.dp)
        Spacer(Modifier.height(Spacing.l))
        PrimaryButton("휴지통으로 옮기기", {
            askTrash = false
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createTrashRequest(activity.contentResolver, listOf(Uri.parse(coupon.sourceUri)), true)
                trash.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            }
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

private fun leave(nav: NavHostController, snackbar: com.ouor.mycoupon.ui.component.AppSnackbar, homeVm: HomeViewModel, id: Long, msg: String) {
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
private fun UsageHistory(usage: List<UsageLog>, onUndoLast: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium).padding(Spacing.m)) {
        usage.forEachIndexed { i, u ->
            val d = epochDay(u.at)
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${d.monthValue}.${"%02d".format(d.dayOfMonth)} · ${u.spent?.let(::won) ?: "-"} 사용 · 잔액 ${u.balanceAfter?.let(::won) ?: "-"}",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                if (i == 0) TextButton(onUndoLast) { Text("취소") }
            }
            if (i < usage.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

/** S8: exchange → confirm; amount → remaining (or spent) with live preview and range check. */
@Composable
private fun UseSheet(c: Coupon, onDismiss: () -> Unit, onUsed: () -> Unit, onRemaining: (Long) -> Unit) {
    if (c.type != CouponType.AMOUNT) {
        AppSheet(onDismiss, "사용 완료로 표시할까요?", "지난 쿠폰으로 옮기고 알림을 끌게요.") {
            PrimaryButton("사용 완료", onUsed)
            SecondaryButton("취소", onDismiss)
        }
        return
    }
    val bal = c.balance ?: c.faceValue ?: 0
    var text by remember { mutableStateOf("") }
    var spentMode by remember { mutableStateOf(false) }
    val n = text.toLongOrNull()
    // C1: the core's own rule decides; the sheet only words it
    val check = n?.let { v -> runCatching { if (spentMode) UsageInput.spent(bal, v) else UsageInput.remaining(bal, v) } }
    val remaining = check?.getOrNull()
    val refusal = check?.exceptionOrNull() as? UsageInputException
    // "잔액이 그대로예요" / "쓴 금액을 입력해 주세요" only disable the button; range errors are said out loud
    val error = refusal?.takeIf { it.error == UsageError.OVER_BALANCE || it.error == UsageError.NEGATIVE }?.let { usageMessage(it, bal, spentMode) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AppSheet(onDismiss, if (spentMode) "얼마 썼나요?" else "얼마 남았나요?", "지금 잔액 ${won(bal)}") {
        OutlinedTextField(
            text, { v -> text = v.filter(Char::isDigit).take(9) }, Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text(if (spentMode) "쓴 금액" else "남은 금액") }, suffix = { Text("원") },
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), singleLine = true,
            isError = error != null, supportingText = error?.let { { Text(it, Modifier.semantics { error(it) }) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (remaining != null) Text(
            if (spentMode) "남는 금액 ${won(bal - n)}" else "이번에 쓴 금액 ${won(bal - n)}",
            Modifier.padding(vertical = Spacing.s), style = MaterialTheme.typography.bodyLarge)
        TextButton({ spentMode = !spentMode; text = "" }, Modifier.heightIn(min = Spacing.touch)) {
            Text(if (spentMode) "남은 금액으로 입력" else "쓴 금액으로 입력")
        }
        Spacer(Modifier.height(Spacing.s))
        PrimaryButton(if (remaining == 0L) "다 썼어요 · 사용 완료" else "기록하기", { remaining?.let(onRemaining) },
            enabled = remaining != null)
        SecondaryButton("취소", onDismiss)
    }
}

/** S8 copy per refusal (screens.md S8); unknown kinds fall back to the core's Korean message. */
fun usageMessage(e: UsageInputException, balance: Long, spentMode: Boolean): String = when (e.error) {
    UsageError.OVER_BALANCE -> if (spentMode) "남은 ${won(balance)}보다 많이 쓸 수 없어요" else "잔액 ${won(balance)}보다 클 수 없어요"
    UsageError.NO_CHANGE -> "잔액이 그대로예요. 쓴 금액이 있을 때 기록해 주세요"
    UsageError.SPENT_ZERO -> "쓴 금액을 입력해 주세요"
    UsageError.NEGATIVE -> "0원보다 적게 입력할 수 없어요"
    UsageError.NOT_AMOUNT -> e.message ?: "금액권만 잔액을 기록할 수 있어요"
}

/** S12 (null = follow the app setting). */
@Composable
private fun ReminderSheet(c: Coupon, onDismiss: () -> Unit, onSave: (List<Int>?) -> Unit) {
    var follow by remember { mutableStateOf(c.reminderOffsets == null) }
    var sel by remember { mutableStateOf(c.reminderOffsets ?: AppSettings().reminderOffsets) }
    val defaults = (LocalContext.current as MainActivity).settings.app.reminderOffsets
    AppSheet(onDismiss, "이 쿠폰 알림", if (c.expiryDate == null) "유효기간이 없어 알림을 보내지 않아요" else null) {
        val enabled = c.expiryDate != null
        ChoiceRow("기본 설정 따르기 (${offsetsLabel(defaults)})", follow, enabled) { follow = true }
        ChoiceRow("이 쿠폰만 따로 정하기", !follow, enabled) { follow = false }
        if (!follow) OffsetChecks(sel, enabled) { sel = it }
        Spacer(Modifier.height(Spacing.m))
        PrimaryButton("저장", { onSave(if (follow) null else sel) }, enabled = enabled && (follow || sel.isNotEmpty()))
        SecondaryButton("취소", onDismiss)
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).selectable(selected, enabled, Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null, enabled = enabled)
        Spacer(Modifier.width(Spacing.m))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

fun offsetLabel(d: Int) = if (d == 0) "당일" else "${d}일 전"
fun offsetsLabel(list: List<Int>) = if (list.isEmpty()) "알림 없음" else list.sortedDescending().joinToString(" · ", transform = ::offsetLabel)

/** S11/S12 offsets: vertical rows, never chips in a row (200 % font). */
@Composable
fun OffsetChecks(sel: List<Int>, enabled: Boolean = true, onChange: (List<Int>) -> Unit) {
    AppSettings.OFFSET_CHOICES.forEach { d ->
        Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch)
            .selectable(d in sel, enabled, Role.Checkbox) { onChange(if (d in sel) sel - d else sel + d) },
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(d in sel, null, enabled = enabled)
            Spacer(Modifier.width(Spacing.m))
            Text(offsetLabel(d), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** S7: landscape, white, immersive; tap anywhere to close. */
@Composable
fun BarcodeFullScreen(id: Long, nav: NavHostController) = MyCouponTheme(forceLight = true) {
    val vm: CouponViewModel = hiltViewModel()
    LaunchedEffect(id) { vm.load(id) }
    val c by vm.coupon.collectAsState()
    val activity = LocalContext.current as Activity
    CheckoutWindow(on = true)
    DisposableEffect(Unit) {
        val before = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            ctl.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = before
        }
    }
    val coupon = c ?: return@MyCouponTheme
    Box(
        Modifier.fillMaxSize().background(BarcodeColors.background)
            .clickable(onClickLabel = "닫기") { nav.popBackStack() }
            .semantics(mergeDescendants = true) { contentDescription = "바코드 크게 보기, 두 번 탭하면 닫기"; onClick("닫기") { nav.popBackStack(); true } },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(horizontal = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            if (coupon.codeView() != CodeView.PIN) BarcodeImage(coupon)
            coupon.code?.let {
                Spacer(Modifier.height(Spacing.m))
                CodeNumber(it, if (coupon.codeView() == CodeView.PIN) CodeTypography.codePin else CodeTypography.codeLarge, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.m))
            Text("아무 곳이나 누르면 닫혀요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
