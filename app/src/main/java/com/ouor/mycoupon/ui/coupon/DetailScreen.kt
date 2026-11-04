package com.ouor.mycoupon.ui.coupon

import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.ui.HomeLinks
import com.ouor.mycoupon.ui.Route
import com.ouor.mycoupon.ui.Sharing
import com.ouor.mycoupon.ui.component.AppSnackbar
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.BottomCta
import com.ouor.mycoupon.ui.component.Copy
import com.ouor.mycoupon.ui.component.EmptyState
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.UndoBar
import com.ouor.mycoupon.ui.component.pastBand
import com.ouor.mycoupon.ui.component.title
import com.ouor.mycoupon.ui.component.undoMillis
import com.ouor.mycoupon.ui.theme.MyCouponTheme
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.toHome
import java.time.LocalDate
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * S6 (F6, F7, F12, F16, C2-01~06, 16, 17, 19~21, 25~27) with S8, S12, F10. Follows the system theme; only the barcode card is always
 * white (D6, U11). Code review A2-07: state in [DetailState], the page in [DetailContent], sheets in [DetailSheets].
 */
@Composable
fun DetailScreen(id: Long, fromReminder: Boolean, nav: NavHostController, home: HomeLinks, swiped: Boolean = false, openUse: Boolean = false) = MyCouponTheme {
    val vm: CouponViewModel = hiltViewModel()
    LaunchedEffect(id) { vm.load(id, fromReminder) }
    val c by vm.coupon.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    val st = rememberDetailState(openUse, swiped)
    val snackbar = LocalSnackbar.current
    val undoMs = undoMillis(LocalContext.current)
    val openedAt = remember { SystemClock.elapsedRealtime() }
    // C2-26 (QA R2-01): ask "넘겼나요?" only when the chooser reports a picked target, not when the share sheet was just closed
    OnShareChosen { st.askGiven = true }
    // C2-17: an available coupon looked at for a while and left without a use is asked about once on S1 (the core applies the 15 s rule)
    val latest by rememberUpdatedState(c)
    DisposableEffect(id) {
        onDispose { latest?.takeIf { it.id == id && it.status == CouponStatus.ACTIVE }?.let { vm.lingered(id, SystemClock.elapsedRealtime() - openedAt) } }
    }

    if (c == null && missing) return@MyCouponTheme MissingCoupon(nav)
    val coupon = c ?: return@MyCouponTheme
    val today = LocalDate.now()
    val nm = coupon.title()
    // C2-02: used / given / expired, not while this screen is finishing its own [사용 완료]
    val past = st.finishing == null && pastBand(coupon, today) != null
    val dimmed = past && !st.revealed
    // code review U-22: full brightness holds through the undo seconds too (the clerk may still be scanning)
    val bright = CheckoutWindow(on = st.finishing != null || (!dimmed && (coupon.status == CouponStatus.ACTIVE || st.revealed)))
    // review U13: swipe (or ‹ ›) to the neighbouring available coupon in the S1 order, without going back to the list
    val ring by home.availableIds.collectAsStateWithLifecycle()
    val pos = ring.indexOf(id)
    val go = { step: Int ->
        if (pos >= 0) ring.getOrNull(pos + step)?.let { n -> nav.navigate(Route.Detail(n, swiped = true)) { popUpTo<Route.Detail> { inclusive = true } } }
    }
    val leaveNow = { leave(nav, snackbar, home, id, "$nm ${st.finishing}", vm.useLog) }
    val undoLeft = finishCountdown(st, coupon, vm, leaveNow)
    // code review U-03: back during the undo seconds leaves like the end of the countdown (S1 Snackbar keeps the undo; F10 waits)
    val back = { if (st.finishing != null) leaveNow() else nav.popBackStack(); Unit }
    BackHandler(st.finishing != null && !st.askTrash, back)
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            AppTopBar("", onBack = back) {
                // C2-06: 56 dp targets, full-contrast icons and position; code review U-06: one step per tap
                if (pos >= 0 && ring.size > 1 && undoLeft == 0) {
                    IconButton(dropUnlessResumed { go(-1) }, Modifier.size(Spacing.cta), enabled = pos > 0) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, if (pos > 0) "이전 쿠폰" else "이전 쿠폰 없음")
                    }
                    Text("${pos + 1} / ${ring.size}", Modifier.semantics { contentDescription = "${ring.size}개 중 ${pos + 1}번째" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(dropUnlessResumed { go(1) }, Modifier.size(Spacing.cta), enabled = pos < ring.lastIndex) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, if (pos < ring.lastIndex) "다음 쿠폰" else "다음 쿠폰 없음")
                    }
                }
                Box {
                    IconButton({ st.menu = true }, Modifier.size(Spacing.cta)) { Icon(Icons.Outlined.MoreVert, "더 보기") }
                    DropdownMenu(st.menu, { st.menu = false }) {
                        DropdownMenuItem({ Text("수정") }, { st.menu = false; nav.navigate(Route.EditCoupon(id)) },
                            Modifier.heightIn(min = Spacing.cta), leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                        DropdownMenuItem({ Text("삭제") }, { st.menu = false; st.confirmDelete = true },
                            Modifier.heightIn(min = Spacing.cta), leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                    }
                }
            }
        },
        bottomBar = {
            val raised = scroll.canScrollForward
            when {
                // C2-04: the CTA spot becomes "사용 완료했어요 / 〈상품명〉" + [되돌리기 · 8초], then F10 and S1 (+ Snackbar undo)
                undoLeft > 0 -> UndoBar(st.finishing.orEmpty(), nm, undoLeft) {
                    st.undoUntil = 0; st.finishing = null; st.ctaFrom = SystemClock.elapsedRealtime() + 1_000
                    vm.undoUse(); snackbar.show(Copy.UNDONE)
                }
                st.finishing != null -> Unit // leaving: F10 sheet or S1 next
                coupon.status != CouponStatus.ACTIVE -> BottomCta(Copy.UNDO, { vm.undoUse(); st.revealed = false; snackbar.show(Copy.UNDONE) }, outlined = true, raised = raised)
                // review U4: the amount CTA says what it does ("사용 기록" read as "show history")
                coupon.type == CouponType.AMOUNT -> BottomCta("쓴 금액 입력", { st.useSheet = true }, raised = raised)
                else -> BottomCta("사용 완료", {
                    if (st.ctaOpen()) { vm.markUsed(); st.undoUntil = System.currentTimeMillis() + undoMs; st.finishing = Copy.USED }
                }, raised = raised)
            }
        },
        snackbarHost = { SnackbarHost(snackbar.host) },
    ) { pad ->
        DetailContent(coupon, st, today, past, dimmed, bright, vm, nav, scroll, Modifier.padding(pad), swipe = if (pos >= 0) go else null)
    }
    DetailSheets(coupon, st, vm, home, nav, leaveNow)
}

/** C2-04 undo seconds, then F10 (once, API 30+), then [leave]. Returns the seconds left on the bar (0 = no bar). */
@Composable
private fun finishCountdown(st: DetailState, coupon: Coupon, vm: CouponViewModel, leave: () -> Unit): Int {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(coupon.status, st.finishing) {
        st.finishing ?: return@LaunchedEffect
        // wall clock against the saved deadline (code review U-04): after a re-creation it goes on, or ends at once
        while (true) {
            now = System.currentTimeMillis()
            val left = st.undoUntil - now
            if (left <= 0) break
            delay((left - 1) % 1000 + 1)
        }
        if (coupon.status != CouponStatus.USED) return@LaunchedEffect // restarts when the write lands
        // C2-04 (core-found defect): an undo tapped while this coroutine was waking must win over leaving the screen
        if (st.finishing == null) return@LaunchedEffect
        // F10: offer to trash the gallery original once (API 30+, MediaStore uri that still exists)
        val canTrash = !coupon.trashAsked && Build.VERSION.SDK_INT >= 30 && coupon.sourceUri?.startsWith("content://media/") == true &&
            withContext(Dispatchers.IO) {
                // code review A7: without gallery permission (picker / share, the usual path) another app's photo can't be opened,
                // so "can't tell" means try; the system trash dialog itself reports a missing photo
                try { context.contentResolver.openInputStream(Uri.parse(coupon.sourceUri))?.close(); true }
                catch (_: SecurityException) { true }
                catch (_: Exception) { false }
            }
        if (canTrash) st.askTrash = true else { if (!coupon.trashAsked) vm.trashAnswered(); leave() }
    }
    return if (st.finishing == null) 0 else ((st.undoUntil - now + 999) / 1000).toInt().coerceAtLeast(0)
}

/** The chooser's "picked an app" broadcast (C2-26) while this screen is shown. */
@Composable
private fun OnShareChosen(onChosen: () -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onChosen)
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { latest() } }
        ContextCompat.registerReceiver(context, r, IntentFilter(Sharing.ACTION_CHOSEN), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(r) }
    }
}

/** QA R1-09: an old notification/widget link to a deleted (or re-imported) coupon id. */
@Composable
private fun MissingCoupon(nav: NavHostController) {
    Scaffold(topBar = { AppTopBar("", onBack = { nav.toHome() }) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(Icons.Rounded.SearchOff, "이 쿠폰을 찾을 수 없어요", "지웠거나 백업에서 다시 불러온 쿠폰이에요. 목록에서 찾아 주세요",
                primary = "내 쿠폰 목록" to { nav.toHome() })
        }
    }
}

/** Back to S1 with the undo Snackbar; [log] is the record the finishing write made (core D-07). */
internal fun leave(nav: NavHostController, snackbar: AppSnackbar, home: HomeLinks, id: Long, msg: String, log: Deferred<Long?>?) {
    home.arrivedFromFinish()
    nav.toHome()
    snackbar.show(msg, Copy.UNDO) { home.undoUse(id, log) }
}
