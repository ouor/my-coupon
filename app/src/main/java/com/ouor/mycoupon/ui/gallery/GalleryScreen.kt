package com.ouor.mycoupon.ui.gallery

import com.ouor.mycoupon.ui.component.Ink
import com.ouor.mycoupon.ui.ImportStarter
import com.ouor.mycoupon.ui.component.Promise
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.domain.ImportOrigin
import com.ouor.mycoupon.gallery.ScanRange
import com.ouor.mycoupon.ui.Route
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.BottomCta
import com.ouor.mycoupon.ui.component.CouponThumb
import com.ouor.mycoupon.ui.component.EmptyState
import com.ouor.mycoupon.ui.component.PrimaryButton
import com.ouor.mycoupon.ui.component.SecondaryButton
import com.ouor.mycoupon.ui.theme.Spacing
import java.text.NumberFormat
import java.util.Locale

private fun n(v: Int) = NumberFormat.getNumberInstance(Locale.KOREA).format(v)

/** S9 (F9): ① permission rationale → ② range → ③ progress, one screen. Leaving pauses (the ViewModel cancels the scan). */
@Composable
fun GalleryScreen(nav: NavHostController, importer: ImportStarter) {
    val activity = LocalActivity.current as Activity
    val vm: GalleryViewModel = hiltViewModel()
    val p by vm.progress.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(vm.hasPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(ScanRange.MONTH_3) }
    var started by rememberSaveable { mutableStateOf(p.checked > 0 || p.found.isNotEmpty()) }
    var photos by remember { mutableIntStateOf(-1) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = vm.hasPermission()
        // denied twice: the system no longer shows its dialog → settings
        denied = !granted && vm.permissions().none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) { importer.start(uris, ImportOrigin.PICKER); nav.navigate(Route.Import) { popUpTo(Route.Home) } }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val partial = granted && vm.isPartialAccess() // C5: only the photos the user allowed, no range
    // code review U-09: back from the system settings (allowed, or a different "selected photos" set): read it again
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { granted = vm.hasPermission(); resumed++; onPauseOrDispose {} }
    LaunchedEffect(granted, range, resumed) { if (granted) photos = vm.count(range) }
    // back on this screen after a pause: resume (screens.md S9 "이어서 찾을게요")
    LaunchedEffect(granted) { if (granted && started && !p.finished && !p.running) vm.scan(range) }
    DisposableEffect(Unit) { onDispose { vm.pause() } }

    val review = { vm.sendToReview(); nav.navigate(Route.Import) { popUpTo(Route.Home) } }
    Scaffold(
        topBar = { AppTopBar("갤러리에서 쿠폰 찾기", onBack = { nav.popBackStack() }, close = true) },
        bottomBar = {
            when {
                !granted -> {}
                !started -> BottomCta("찾기 시작", { started = true; vm.scan(range) }, enabled = photos != 0)
                p.found.isNotEmpty() -> BottomCta(if (p.finished) "찾은 ${p.found.size}장 확인하기" else "지금까지 찾은 ${p.found.size}장 확인하기", review)
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l)) {
            when {
                !granted && denied -> {
                    Rationale("사진 접근이 꺼져 있어요", emptyList())
                    PrimaryButton("설정 열기", {
                        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
                    })
                    SecondaryButton("사진 고르기로 추가", pick)
                }
                !granted -> {
                    Rationale("갤러리에서 쿠폰을 찾아 드릴게요", listOf(
                        "사진은 이 폰 안에서만 확인해요. 밖으로 보내지 않아요.",
                    ))
                    PrimaryButton("사진 접근 허용하기", { request.launch(vm.permissions()) })
                    SecondaryButton("나중에", { nav.popBackStack() })
                }
                !started -> {
                    Spacer(Modifier.height(Spacing.l))
                    Text(if (partial) "허용한 사진에서 찾아볼게요" else "어디까지 찾아볼까요?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(Spacing.l))
                    if (!partial) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ScanRange.entries.forEachIndexed { i, r ->
                            SegmentedButton(range == r, { range = r }, SegmentedButtonDefaults.itemShape(i, ScanRange.entries.size), Modifier.heightIn(min = Spacing.touch)) {
                                Text(when (r) { ScanRange.MONTH_1 -> "최근 1개월"; ScanRange.MONTH_3 -> "3개월"; ScanRange.MONTH_12 -> "12개월" })
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.m))
                    Text(when {
                        photos < 0 -> " "
                        partial && photos == 0 -> "허용한 사진이 없어요. 설정에서 사진을 더 고를 수 있어요"
                        partial -> "허용한 사진 ${n(photos)}장을 찾아볼게요"
                        photos == 0 -> "이 기간에는 사진이 없어요"
                        else -> "사진 ${n(photos)}장 · 약 ${maxOf(1, photos / 600)}분 걸려요" // screening ~10 images/s
                    }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // C4: nothing in the range / everything already checked / checked and found nothing
                p.finished && p.found.isEmpty() -> when {
                    p.inRange == 0 -> EmptyState(Ink.ImageSearch, "이 기간에는 사진이 없어요",
                        primary = "다른 기간 찾기" to { started = false }, secondary = "사진 고르기" to pick)
                    p.total == 0 -> EmptyState(Ink.ImageSearch, "새로 확인할 사진이 없어요", "이 기간의 사진 ${n(p.inRange)}장은 지난번에 모두 확인했어요.",
                        primary = "다른 기간 찾기" to { started = false }, secondary = "사진 고르기" to pick)
                    // C2-11 (QA): "찾지 못했어요" read as a failure; the reasons say the photos were seen and set aside
                    else -> EmptyState(Ink.ImageSearch, "새로 찾은 쿠폰이 없어요", listOfNotNull(
                        p.registered.takeIf { it > 0 }?.let { "이미 등록한 쿠폰 ${n(it)}장 제외" },
                        p.notCoupon.takeIf { it > 0 }?.let { "쿠폰이 아닌 사진 ${n(it)}장 제외" },
                    ).joinToString("\n").ifEmpty { null }, primary = "사진 고르기" to pick, secondary = "다른 기간 찾기" to { started = false })
                }
                else -> {
                    Spacer(Modifier.height(Spacing.l))
                    Text(if (p.finished) "다 찾았어요 · 쿠폰 ${p.found.size}장" else "쿠폰 ${p.found.size}장 찾았어요",
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(Spacing.l))
                    LinearProgressIndicator(progress = { p.checked.toFloat() / maxOf(1, p.total) }, Modifier.fillMaxWidth())
                    Text("${p.checked * 100 / maxOf(1, p.total)}% · ${n(p.total)}장 중 ${n(p.checked)}장 확인", Modifier.padding(top = Spacing.s),
                        style = MaterialTheme.typography.bodyLarge)
                    // C6: resumed after leaving the screen
                    if (p.resumedFrom > 0 && !p.finished) Text("${n(p.resumedFrom)}장까지 확인했어요. 이어서 찾을게요", Modifier.padding(top = Spacing.s),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    if (p.found.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.l))
                        // C2-27: the latest six in a 3-column grid, the rest as "+N"
                        val shown = p.found.takeLast(6)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            shown.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) { row.forEach { CouponThumb(it.coupon?.thumbPath, size = 96.dp) } }
                            }
                            if (p.found.size > 6) Text("+${p.found.size - 6}장 더", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Rationale(title: String, promises: List<String>) {
    Spacer(Modifier.height(Spacing.xl))
    androidx.compose.material3.Icon(Ink.PhoneLock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(Spacing.l))
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(Spacing.m))
    promises.forEach { Promise(Ink.CheckCircle, it, null) }
    Spacer(Modifier.height(Spacing.xl))
}
