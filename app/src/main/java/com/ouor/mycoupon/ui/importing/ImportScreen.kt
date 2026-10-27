package com.ouor.mycoupon.ui.importing

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.data.Duplicate
import com.ouor.mycoupon.domain.Draft
import com.ouor.mycoupon.domain.DraftStage
import com.ouor.mycoupon.domain.ImportOrigin
import com.ouor.mycoupon.ui.home.HomeViewModel
import com.ouor.mycoupon.ui.MainActivity
import com.ouor.mycoupon.ui.Route
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.BottomCta
import com.ouor.mycoupon.ui.component.Chip
import com.ouor.mycoupon.ui.component.CouponThumb
import com.ouor.mycoupon.ui.component.EmptyState
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.ReviewMark
import com.ouor.mycoupon.ui.component.StatusChip
import com.ouor.mycoupon.ui.component.codeTail
import com.ouor.mycoupon.ui.component.dotted
import com.ouor.mycoupon.ui.component.isLargeFont
import com.ouor.mycoupon.ui.component.name
import com.ouor.mycoupon.ui.component.spoken
import com.ouor.mycoupon.ui.component.won
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors
import com.ouor.mycoupon.ui.toHome
import java.time.LocalDate

/** S4 sort: review → normal → duplicate / not a coupon / failed → still reading. */
private fun order(d: Draft): Int = when {
    d.stage == DraftStage.READING -> 3
    d.stage == DraftStage.FAILED || !d.isCoupon || d.duplicate != null -> 2
    d.coupon?.needsCheck == true -> 0
    else -> 1
}

/** S3 + S4 (F2-F4). */
@Composable
fun ImportScreen(nav: NavHostController) {
    val activity = LocalActivity.current as MainActivity
    val vm: ImportViewModel = hiltViewModel(activity)
    val homeVm: HomeViewModel = hiltViewModel(activity)
    val st by vm.state.collectAsState()
    val snackbar = LocalSnackbar.current
    var confirmClose by remember { mutableStateOf(false) }
    var confirmEarly by remember { mutableStateOf(false) }
    var registering by remember { mutableStateOf(false) } // one register per tap burst (double insert otherwise)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) vm.start(uris, ImportOrigin.PICKER)
    }

    LaunchedEffect(st.truncated) { if (st.truncated) snackbar.show("한 번에 30장까지 읽어요. 앞의 30장만 가져왔어요") }
    // the batch was registered or discarded elsewhere (e.g. data wiped): nothing to show
    LaunchedEffect(st.drafts.isEmpty(), st.running) { if (st.drafts.isEmpty() && !st.running) nav.toHome() }
    BackHandler { confirmClose = true }

    fun register() {
        if (registering) return
        registering = true
        vm.register { ids ->
            // C10: a second, concurrent call gets [] from the core; the first call already announced and left
            if (ids.isEmpty()) return@register
            snackbar.show("쿠폰 ${ids.size}개를 등록했어요")
            homeVm.registered()
            nav.toHome()
        }
    }

    val today = LocalDate.now()
    val expired = { d: Draft -> d.coupon?.expiryDate?.isBefore(today) == true }
    val reviewCount = st.drafts.count { it.stage == DraftStage.DONE && it.isCoupon && it.duplicate == null && it.coupon?.needsCheck == true && !expired(it) }
    // review U2: unchecked not-a-coupon drafts fold into one row that can be opened
    val (folded, shown) = st.drafts.sortedBy(::order).partition { it.stage == DraftStage.DONE && !it.isCoupon && !it.checked }
    var foldOpen by rememberSaveable { mutableStateOf(false) }
    val allFailed = !st.running && st.drafts.isNotEmpty() && st.drafts.all { it.stage == DraftStage.FAILED }
    val reading = st.total - st.done

    Scaffold(
        topBar = { AppTopBar("결과 확인", onBack = { confirmClose = true }, close = true) },
        bottomBar = {
            BottomCta("${st.checkedCount}개 등록", { if (st.running) confirmEarly = true else register() },
                enabled = st.checkedCount > 0 && !registering, hint = if (st.checkedCount == 0 && st.done > 0) "등록할 쿠폰을 골라 주세요" else null)
        },
        snackbarHost = { SnackbarHost(snackbar.host) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (st.running) item(key = "progress") {
                // TalkBack hears the progress in 10 % steps only
                val step = (st.done * 10 / maxOf(1, st.total))
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "${st.total}장 중 ${st.done}장 읽음, ${step * 10}퍼센트" }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${st.total}장 중 ${minOf(st.done + 1, st.total)}장 읽는 중", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(vm::stop) { Text("그만 읽기") }
                    }
                    LinearProgressIndicator(progress = { st.done.toFloat() / maxOf(1, st.total) }, Modifier.fillMaxWidth().padding(top = Spacing.s))
                    Text("사진은 이 폰 안에서만 읽어요", Modifier.padding(top = Spacing.s), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (allFailed) item(key = "failed") {
                EmptyState(Icons.Rounded.BrokenImage, "사진에서 쿠폰을 읽지 못했어요", "직접 입력하거나 다른 사진을 골라 주세요",
                    primary = "다른 사진 고르기" to { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            } else if (st.done > 0) item(key = "summary") {
                // review U8: the headline number is the same one the [N개 등록] button uses; the rest says where the others went
                val done = st.drafts.filter { it.stage != DraftStage.READING }
                val parts = listOfNotNull(
                    "확인 필요 ${reviewCount}".takeIf { reviewCount > 0 },
                    done.count { it.duplicate != null }.takeIf { it > 0 }?.let { "이미 등록 $it" },
                    done.count { it.isCoupon && expired(it) }.takeIf { it > 0 }?.let { "만료 $it" },
                    folded.size.takeIf { it > 0 }?.let { "쿠폰 아님 $it" },
                    done.count { it.stage == DraftStage.FAILED }.takeIf { it > 0 }?.let { "못 읽음 $it" },
                )
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text("${done.size}장 중 ${st.checkedCount}개를 등록해요", style = MaterialTheme.typography.titleMedium)
                    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(shown, key = { it.key }) { d ->
                DraftCard(d, Modifier.animateItem(), onCheck = { vm.setChecked(d.key, it) }, onOpen = { nav.navigate(Route.EditDraft(d.key)) },
                    onExclude = { vm.exclude(d.key) })
            }
            if (folded.isNotEmpty()) item(key = "folded") {
                TextButton({ foldOpen = !foldOpen }, Modifier.fillMaxWidth().heightIn(min = Spacing.touch)) {
                    Text("쿠폰이 아닌 것 ${folded.size}장", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Icon(if (foldOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (foldOpen) "접기" else "펼치기")
                }
            }
            if (foldOpen) items(folded, key = { it.key }) { d ->
                DraftCard(d, Modifier.animateItem(), onCheck = { vm.setChecked(d.key, it) }, onOpen = { nav.navigate(Route.EditDraft(d.key)) })
            }
            if (st.origin == ImportOrigin.GALLERY_SCAN) item(key = "pin-hint") {
                Text("바코드 없는 상품권은 [사진 고르기]로 추가해 주세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("읽은 결과를 버릴까요?") },
        text = { Text("등록하지 않은 쿠폰은 지워져요. 갤러리 사진은 그대로예요.") },
        confirmButton = { TextButton({ confirmClose = false; vm.discard(); nav.toHome() }) { Text("버리기", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmClose = false }) { Text("계속 확인") } },
    )
    if (confirmEarly) AlertDialog(
        onDismissRequest = { confirmEarly = false },
        title = { Text("아직 ${reading}장을 읽고 있어요") },
        text = { Text("지금까지 읽은 ${st.checkedCount}개만 등록할까요?") },
        confirmButton = { TextButton({ confirmEarly = false; register() }) { Text("${st.checkedCount}개 등록") } },
        dismissButton = { TextButton({ confirmEarly = false }) { Text("계속 읽기") } },
    )
}

/** S4 RecognitionCard. Checkbox = select only; card tap = S5. */
@Composable
private fun DraftCard(d: Draft, modifier: Modifier, onCheck: (Boolean) -> Unit, onOpen: () -> Unit, onExclude: (() -> Unit)? = null) {
    val c = d.coupon
    val s = MaterialTheme.statusColors
    val readingNow = d.stage == DraftStage.READING
    val expired = c?.expiryDate?.isBefore(LocalDate.now()) == true
    // review U8: an expired card shows one warning (만료), not 만료 + 확인 필요
    val review = d.stage == DraftStage.DONE && d.isCoupon && d.duplicate == null && c?.needsCheck == true && !expired
    val large = isLargeFont()
    val spoken = when {
        readingNow -> "읽는 중"
        d.stage == DraftStage.FAILED -> "읽지 못했어요, 직접 입력"
        else -> listOfNotNull(
            c?.brand, c?.name(),
            c?.expiryDate?.let { "유효기간 ${it.spoken()}" } ?: "유효기간 없음",
            "확인 필요".takeIf { review },
            when (d.duplicate) { Duplicate.USED -> "이미 사용한 쿠폰이에요"; Duplicate.REGISTERED, Duplicate.IN_BATCH -> "이미 등록된 쿠폰"; null -> null },
            "쿠폰이 아닌 것 같아요".takeIf { !d.isCoupon },
            "이미 만료됐어요".takeIf { expired && d.isCoupon && d.duplicate == null },
        ).joinToString(", ")
    }
    Card(
        onClick = onOpen, enabled = !readingNow,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (d.checked || readingNow) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (review) BorderStroke(2.dp, s.reviewBorder) else null,
    ) {
        Row(Modifier.padding(end = Spacing.l, top = Spacing.xs, bottom = Spacing.l), verticalAlignment = Alignment.Top) {
            Checkbox(d.checked, onCheck, enabled = !readingNow,
                modifier = Modifier.semantics { contentDescription = spoken; stateDescription = if (d.checked) "선택됨" else "선택 안 됨" })
            Column(Modifier.weight(1f).padding(top = Spacing.m)) {
                Row(verticalAlignment = Alignment.Top) {
                    if (!large) {
                        CouponThumb(c?.thumbPath, size = 72.dp, alpha = if (d.checked) 1f else 0.6f)
                        Spacer(Modifier.width(Spacing.m))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        when {
                            readingNow -> {
                                Text("읽는 중", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(Modifier.fillMaxWidth(0.6f).height(14.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall))
                                Box(Modifier.fillMaxWidth(0.4f).height(14.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall))
                            }
                            d.stage == DraftStage.FAILED -> {
                                Text("읽지 못했어요", style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onOpen) { Text("직접 입력") }
                            }
                            else -> {
                                when (d.duplicate) {
                                    Duplicate.USED -> {
                                        StatusChip(Chip.ALREADY_USED)
                                        Text("사용 완료로 표시한 쿠폰과 번호가 같아요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Duplicate.REGISTERED, Duplicate.IN_BATCH -> StatusChip(Chip.REGISTERED)
                                    null -> if (!d.isCoupon) {
                                        StatusChip(Chip.NOT_COUPON)
                                        Text("쿠폰이 맞으면 체크해 주세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (expired) {
                                        StatusChip(Chip.EXPIRED_DRAFT)
                                        Text("등록하면 지난 쿠폰으로 가요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                c?.brand?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text(c?.name() ?: "상품 이름 없음", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                if (d.stage == DraftStage.DONE && c != null) {
                    HorizontalDivider(Modifier.padding(vertical = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("유효기간", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(c.expiryDate?.dotted() ?: "기간 없음", style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.alpha(if (d.checked) 1f else 0.87f))
                    if (c.needsReview.containsKey("expiryDate") && review) ReviewMark()
                    val other = c.needsReview.keys.any { it != "expiryDate" }
                    val line = listOfNotNull(
                        c.code?.let(::codeTail) ?: "번호를 찾지 못했어요 · 핀번호면 직접 입력해 주세요",
                        if (c.type == CouponType.AMOUNT) "금액권" + (c.faceValue?.let { " ${won(it)}" } ?: "") else "교환·할인권",
                    ).joinToString(" · ")
                    Text(line, Modifier.padding(top = Spacing.xs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (other && review && !c.needsReview.containsKey("expiryDate")) ReviewMark()
                    // review U2: doubtful cards (확인 필요, or a not-a-coupon the user ticked) can be dropped from the card itself
                    if (onExclude != null && (review || (!d.isCoupon && d.checked))) TextButton(onExclude, Modifier.heightIn(min = Spacing.touch)) {
                        Text("쿠폰 아님 · 빼기", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
