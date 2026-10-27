package com.ouor.mycoupon.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ouor.mycoupon.BuildConfig
import com.ouor.mycoupon.data.AppSettings
import com.ouor.mycoupon.data.CodeSource
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.ImageStore
import com.ouor.mycoupon.data.ListFilter
import com.ouor.mycoupon.data.ListSort
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.domain.DraftStage
import com.ouor.mycoupon.domain.Duplicate
import com.ouor.mycoupon.domain.ImportOrigin
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.gallery.ScanRange
import com.ouor.mycoupon.ocr.core.CouponType
import com.ouor.mycoupon.reminder.ReminderPlanner
import com.ouor.mycoupon.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

// Minimal functional screens for QA and for wiring; the UI developer rebuilds them per docs/design.

@Composable
fun AppScreens(top: Screen, push: (Screen) -> Unit, pop: () -> Unit, replaceAll: (Screen) -> Unit) {
    when (top) {
        Screen.Onboarding -> OnboardingScreen { replaceAll(Screen.Home) }
        Screen.Home -> HomeScreen(push)
        Screen.Import -> ImportScreen(push, pop)
        is Screen.EditDraft -> EditDraftScreen(top.key, pop)
        is Screen.EditCoupon -> EditCouponScreen(top.id, pop)
        is Screen.Detail -> DetailScreen(top.id, top.fromReminder, push, pop)
        Screen.Settings -> SettingsScreen(pop)
        Screen.Gallery -> GalleryScreen(push, pop)
    }
}

@Composable
private fun Title(text: String, back: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) TextButton(onClick = back) { Text("뒤로") }
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
        actions()
    }
}

@Composable
private fun ImageFile(path: String, modifier: Modifier = Modifier, maxSide: Int = 0, desc: String? = null) {
    var bmp by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path) {
        bmp = withContext(Dispatchers.IO) {
            val f = File(path)
            if (!f.exists()) null else if (path.endsWith(".jpg")) android.graphics.BitmapFactory.decodeFile(path) else ImageStore.decode(f.readBytes(), maxSide)
        }
    }
    bmp?.let { Image(it.asImageBitmap(), desc, modifier, contentScale = ContentScale.Fit) }
}

private fun dday(c: Coupon, today: LocalDate): String = when {
    c.status == CouponStatus.USED -> if (c.usedReason == UsedReason.GIVEN) "넘김" else "사용 완료"
    c.isExpired(today) -> "만료"
    else -> when (val d = Stats.daysLeft(c, today)) { null -> "기간 없음"; 0L -> "오늘"; else -> "D-$d" }
}

// ---------- S0 ----------

@Composable
private fun OnboardingScreen(start: () -> Unit) {
    val vm: HomeViewModel = hiltViewModel()
    val activity = LocalContext.current as MainActivity
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("가입 없이 바로 써요", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("쿠폰 사진은 이 폰 안에서만 읽고 저장해요. 어디에도 보내지 않아요.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = { activity.settings.onboarded = true; vm.refresh(); start() }, Modifier.fillMaxWidth()) { Text("시작하기") }
    }
}

// ---------- S1 / S13 ----------

@Composable
private fun HomeScreen(push: (Screen) -> Unit) {
    val vm: HomeViewModel = hiltViewModel(LocalContext.current as MainActivity)
    val importVm: ImportViewModel = hiltViewModel(LocalContext.current as MainActivity)
    val st by vm.state.collectAsState()
    val context = LocalContext.current
    var addSheet by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) {
            importVm.start(uris, ImportOrigin.PICKER)
            push(Screen.Import)
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    Column(Modifier.fillMaxSize()) {
        Title("내 쿠폰") { TextButton(onClick = { push(Screen.Settings) }) { Text("설정") } }
        when (st.banner) {
            HomeBanner.CRASH -> Banner("지난번에 앱이 멈췄어요. [설정 > 오류 기록 보내기]") { vm.crashBannerShown(); push(Screen.Settings) }
            HomeBanner.NOTIFICATIONS_OFF -> Banner("알림이 꺼져 있어 만료를 알려 드릴 수 없어요 [켜기]") {
                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            HomeBanner.NEEDS_REVIEW -> Banner("확인 필요 ${st.reviewCount}개") {
                st.coupons.firstOrNull { it.needsCheck }?.let { push(Screen.EditCoupon(it.id)) }
            }
            HomeBanner.NEW_IMAGES -> Banner("새 쿠폰 이미지 ${st.newImages}장 있어요") { vm.reviewNewImages(); push(Screen.Import) }
            null -> {}
        }
        OutlinedTextField(st.query, { vm.query.value = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp), placeholder = { Text("브랜드·상품명 검색") }, singleLine = true)
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(st.filter == ListFilter.AVAILABLE, { vm.filter.value = ListFilter.AVAILABLE }, { Text("사용 가능") })
            FilterChip(st.filter == ListFilter.PAST, { vm.filter.value = ListFilter.PAST }, { Text("지난 쿠폰") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.sort.value = if (st.sort == ListSort.EXPIRY) ListSort.RECENT else ListSort.EXPIRY }) {
                Text(if (st.sort == ListSort.EXPIRY) "만료 임박순" else "최근 등록순")
            }
        }
        if (st.coupons.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                Text(when {
                    st.query.isNotBlank() -> "‘${st.query}’과 맞는 쿠폰이 없어요"
                    st.filter == ListFilter.PAST -> "지난 쿠폰이 없어요"
                    else -> "아직 쿠폰이 없어요"
                })
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(st.coupons, key = { it.id }) { c -> CouponRow(c, st.today) { push(Screen.Detail(c.id)) } }
            }
        }
        Button(onClick = { addSheet = true }, Modifier.fillMaxWidth().padding(12.dp)) { Text("+ 쿠폰 추가") }
    }
    if (addSheet) AlertDialog(
        onDismissRequest = { addSheet = false },
        title = { Text("쿠폰 추가") },
        text = {
            Column {
                TextButton(onClick = { addSheet = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("사진 고르기") }
                TextButton(onClick = { addSheet = false; push(Screen.Gallery) }) { Text("갤러리에서 쿠폰 찾기") }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun Banner(text: String, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().padding(12.dp).background(MaterialTheme.colorScheme.secondaryContainer).clickable(onClick = onClick).padding(12.dp))
}

@Composable
private fun CouponRow(c: Coupon, today: LocalDate, onClick: () -> Unit) {
    val dd = dday(c, today)
    val soon = c.isAvailable(today) && (Stats.daysLeft(c, today) ?: 99) <= 7
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ImageFile(c.thumbPath, Modifier.size(56.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(c.brand ?: "브랜드 없음", style = MaterialTheme.typography.labelLarge)
                Text(c.productName ?: "상품명 없음", style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                if (c.type == CouponType.AMOUNT && c.balance != null) Text("잔액 ${ReminderPlanner.won(c.balance)}")
                if (c.needsCheck && c.status == CouponStatus.ACTIVE) Text("확인 필요", color = AppColors.review)
            }
            Text(dd, style = MaterialTheme.typography.titleLarge, color = if (soon) AppColors.soon else Color.Unspecified)
        }
    }
}

// ---------- S3 + S4 ----------

@Composable
private fun ImportScreen(push: (Screen) -> Unit, pop: () -> Unit) {
    val vm: ImportViewModel = hiltViewModel(LocalContext.current as MainActivity)
    val st by vm.state.collectAsState()
    val context = LocalContext.current
    var confirmClose by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val activity = context as MainActivity

    Column(Modifier.fillMaxSize()) {
        Title("결과 확인", back = { confirmClose = true })
        if (st.running) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${st.total}장 중 ${st.done + 1}장 읽는 중", Modifier.weight(1f))
                TextButton(onClick = vm::stop) { Text("그만 읽기") }
            }
            LinearProgressIndicator(progress = { st.done.toFloat() / maxOf(1, st.total) }, Modifier.fillMaxWidth().padding(12.dp))
        }
        if (st.truncated) Text("한 번에 30장까지 읽어요. 앞의 30장만 가져왔어요", Modifier.padding(12.dp))
        val review = st.drafts.count { it.coupon?.needsCheck == true && it.isCoupon }
        val notCoupon = st.drafts.count { it.stage == DraftStage.DONE && !it.isCoupon }
        Text("확인할 쿠폰 ${review}개 · 쿠폰이 아닌 것 ${notCoupon}장", Modifier.padding(horizontal = 12.dp))
        val ordered = st.drafts.sortedBy {
            when {
                it.stage == DraftStage.READING -> 4
                it.coupon?.needsCheck == true && it.isCoupon && it.duplicate == null -> 0
                it.isCoupon && it.duplicate == null -> 1
                else -> 2
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(ordered, key = { it.key }) { d ->
                val c = d.coupon
                val reviewBorder = c?.needsCheck == true && d.isCoupon
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .then(if (reviewBorder) Modifier.border(2.dp, AppColors.review, MaterialTheme.shapes.medium) else Modifier)
                        .clickable(enabled = d.stage != DraftStage.READING) { push(Screen.EditDraft(d.key)) },
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(d.checked, { vm.setChecked(d.key, it) }, enabled = d.stage != DraftStage.READING)
                        c?.let { ImageFile(it.thumbPath, Modifier.size(72.dp)) }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            when {
                                d.stage == DraftStage.READING -> Text("읽는 중")
                                d.stage == DraftStage.FAILED -> Text("읽지 못했어요 · 직접 입력")
                                else -> {
                                    when (d.duplicate) {
                                        Duplicate.USED -> Text("이미 사용한 쿠폰이에요", color = AppColors.soon)
                                        Duplicate.REGISTERED, Duplicate.IN_BATCH -> Text("이미 등록된 쿠폰", color = AppColors.ended)
                                        null -> {}
                                    }
                                    if (!d.isCoupon) Text(if (c?.code == null) "번호를 찾지 못했어요 · 핀번호면 직접 입력해 주세요" else "쿠폰이 아닌 것 같아요", color = AppColors.ended)
                                    Text(c?.brand ?: "", style = MaterialTheme.typography.labelLarge)
                                    Text(c?.productName ?: "")
                                    Text("유효기간", style = MaterialTheme.typography.labelMedium)
                                    Text(c?.expiryDate?.toString() ?: "기간 없음", style = MaterialTheme.typography.headlineSmall)
                                    if (c?.needsReview?.containsKey("expiryDate") == true) Text("확인 필요", color = AppColors.review)
                                    Text("번호 끝 ${c?.code?.takeLast(4) ?: "-"} · " + if (c?.type == CouponType.AMOUNT) "금액권" else "교환·할인권")
                                }
                            }
                        }
                    }
                }
            }
            if (st.origin == ImportOrigin.GALLERY_SCAN) item { Text("바코드 없는 상품권은 [사진 고르기]로 추가해 주세요", Modifier.padding(12.dp)) }
        }
        Button(
            onClick = {
                vm.register { ids ->
                    android.widget.Toast.makeText(context, "쿠폰 ${ids.size}개를 등록했어요", android.widget.Toast.LENGTH_SHORT).show()
                    // D8: the first registration asks for notifications (rationale = this toast + system dialog in the minimal UI)
                    if (ids.isNotEmpty() && !activity.settings.notificationAsked && Build.VERSION.SDK_INT >= 33) {
                        activity.settings.notificationAsked = true
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    pop()
                }
            },
            enabled = st.checkedCount > 0,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) { Text("${st.checkedCount}개 등록") }
    }
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false },
        title = { Text("읽은 결과를 버릴까요?") },
        confirmButton = { TextButton(onClick = { confirmClose = false; vm.discard(); pop() }) { Text("버리기") } },
        dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("계속 확인") } },
    )
}

// ---------- S5 ----------

/** S5 copy per reason (screens.md S5): the first reason of a field is shown. */
fun reasonText(field: String, reason: String): String = when (reason) {
    "YEAR_INFERRED" -> "사진에 연도가 없어 추정했어요"
    "MULTIPLE_CANDIDATES" -> if (field == "faceValue") "금액이 여러 개 있어요. 맞는 금액인지 봐 주세요" else "날짜가 여러 개 있어요. 맞는 날짜를 골라 주세요"
    "AMBIGUOUS" -> "비슷한 상품 이름이 더 있어요. 맞는지 봐 주세요"
    "NO_KEYWORD" -> "유효기간 글자를 찾지 못해 추정했어요"
    "LOW_CONFIDENCE" -> "글자가 흐려 잘못 읽었을 수 있어요"
    "NOT_FOUND" -> when (field) {
        "expiryDate" -> "날짜를 찾지 못했어요. 직접 골라 주세요"
        "code" -> "번호를 찾지 못했어요. 직접 입력해 주세요"
        "productName" -> "상품 이름을 찾지 못했어요"
        "brand" -> "브랜드를 찾지 못했어요"
        "faceValue" -> "금액을 찾지 못했어요. 직접 입력해 주세요"
        else -> "사진과 같은지 확인해 주세요"
    }
    "CODE_MISMATCH" -> "바코드와 숫자가 달라요. 사진과 비교해 주세요"
    "CODE_NOT_DECODED" -> "바코드를 못 읽어 숫자로 가져왔어요. 사진과 같은지 봐 주세요"
    "ALWAYS_CHECK" -> if (field == "sender") "사진에서 보낸 사람을 가져왔어요. 맞는지 봐 주세요" else "사진과 같은지 확인해 주세요"
    else -> "사진과 같은지 확인해 주세요"
}

@Composable
private fun EditDraftScreen(key: Int, pop: () -> Unit) {
    val vm: ImportViewModel = hiltViewModel(LocalContext.current as MainActivity)
    val st by vm.state.collectAsState()
    val d = st.drafts.firstOrNull { it.key == key }?.coupon ?: run { pop(); return }
    CouponEditor(d, onSave = { vm.edit(key, it); pop() }, onBack = pop)
}

@Composable
private fun EditCouponScreen(id: Long, pop: () -> Unit) {
    val vm: CouponViewModel = hiltViewModel()
    LaunchedEffect(id) { vm.load(id) }
    val c by vm.coupon.collectAsState()
    c?.let { CouponEditor(it, onSave = { n -> vm.save(n); pop() }, onBack = pop) }
}

/** S5 editor over a [Coupon] copy; [맞아요] clears that field's reasons. */
@Composable
private fun CouponEditor(initial: Coupon, onSave: (Coupon) -> Unit, onBack: () -> Unit) {
    var c by remember(initial.id, initial.imagePath) { mutableStateOf(initial) }
    var expiryText by remember(initial.imagePath) { mutableStateOf(initial.expiryDate?.toString() ?: "") }
    var noExpiry by remember(initial.imagePath) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun confirm(field: String) { c = c.copy(needsReview = c.needsReview - field) }

    @Composable
    fun Review(field: String) {
        val reasons = c.needsReview[field] ?: return
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("확인 필요 · " + reasonText(field, reasons.first()), color = AppColors.review, modifier = Modifier.weight(1f))
            if ("NOT_FOUND" !in reasons) TextButton(onClick = { confirm(field) }) { Text("맞아요") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Title("쿠폰 확인", back = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
            ImageFile(c.imagePath, Modifier.fillMaxWidth().height(260.dp), maxSide = 2000, desc = "쿠폰 원본 이미지")
            OutlinedTextField(c.brand ?: "", { c = c.copy(brand = it.ifBlank { null }); confirm("brand") }, Modifier.fillMaxWidth(), label = { Text("브랜드") })
            Review("brand")
            OutlinedTextField(c.productName ?: "", { c = c.copy(productName = it.ifBlank { null }); confirm("productName") }, Modifier.fillMaxWidth(), label = { Text("상품명") })
            Review("productName")
            Row {
                FilterChip(c.type == CouponType.EXCHANGE, { c = c.copy(type = CouponType.EXCHANGE, faceValue = null, balance = null) }, { Text("교환·할인권") })
                Spacer(Modifier.width(8.dp))
                FilterChip(c.type == CouponType.AMOUNT, { c = c.copy(type = CouponType.AMOUNT) }, { Text("금액권") })
            }
            if (c.type == CouponType.AMOUNT) {
                OutlinedTextField(c.faceValue?.toString() ?: "", { v -> val n = v.filter(Char::isDigit).toLongOrNull(); c = c.copy(faceValue = n, balance = c.balance ?: n); confirm("faceValue") },
                    Modifier.fillMaxWidth(), label = { Text("금액(원)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Review("faceValue")
                OutlinedTextField(c.balance?.toString() ?: "", { v -> c = c.copy(balance = v.filter(Char::isDigit).toLongOrNull()) },
                    Modifier.fillMaxWidth(), label = { Text("잔액(원)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            OutlinedTextField(expiryText, { expiryText = it; confirm("expiryDate") }, Modifier.fillMaxWidth(), label = { Text("유효기간 (YYYY-MM-DD)") }, enabled = !noExpiry)
            if (c.expiryCandidates.size > 1) Row {
                c.expiryCandidates.forEach { d -> FilterChip(expiryText == d.toString(), { expiryText = d.toString(); confirm("expiryDate") }, { Text(d.toString()) }) }
            }
            Review("expiryDate")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(noExpiry, { noExpiry = it; if (it) confirm("expiryDate") })
                Text("유효기간 없음")
            }
            OutlinedTextField(c.code ?: "", { v ->
                c = c.copy(code = v.ifBlank { null }, codeFormat = if (v.isBlank()) null else "PIN", codeSource = if (v.isBlank()) null else CodeSource.USER)
                confirm("code")
            }, Modifier.fillMaxWidth(), label = { Text("번호") })
            Review("code")
            OutlinedTextField(c.memo ?: "", { c = c.copy(memo = it.ifBlank { null }); confirm("sender") }, Modifier.fillMaxWidth(), label = { Text("메모") })
            Review("sender")
            c.notes?.let { Text("유의사항 원문\n$it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Button(onClick = {
            val date = if (noExpiry || expiryText.isBlank()) null else runCatching { LocalDate.parse(expiryText.trim()) }.getOrNull()
            when {
                !noExpiry && expiryText.isNotBlank() && date == null -> error = "날짜를 YYYY-MM-DD로 입력해 주세요"
                c.type == CouponType.AMOUNT && c.balance != null && c.faceValue != null && c.balance!! > c.faceValue!! -> error = "잔액이 금액보다 클 수 없어요"
                else -> onSave(c.copy(expiryDate = date, codeKey = Coupon.codeKey(c.code),
                    needsReview = if (noExpiry) c.needsReview - "expiryDate" else c.needsReview))
            }
        }, Modifier.fillMaxWidth().padding(12.dp)) { Text("저장") }
    }
}

// ---------- S6 / S8 / S12 / F10 / F16 ----------

@Composable
private fun DetailScreen(id: Long, fromReminder: Boolean, push: (Screen) -> Unit, pop: () -> Unit) {
    val vm: CouponViewModel = hiltViewModel()
    LaunchedEffect(id) { vm.load(id, fromReminder) }
    val c by vm.coupon.collectAsState()
    val usage by vm.usage.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    var useSheet by remember { mutableStateOf(false) }
    var askGiven by remember { mutableStateOf(false) }
    var askTrash by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var reminderSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // F6: max brightness for this window only + keep the screen on; restored on leave
    DisposableEffect(Unit) {
        val w = activity.window
        val before = w.attributes.screenBrightness
        w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            w.attributes = w.attributes.apply { screenBrightness = before }
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { askGiven = true }
    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.trashAnswered() }

    val coupon = c ?: return
    val today = LocalDate.now()
    // F10: after use, offer to trash the gallery original once (API 30+, MediaStore uri only)
    LaunchedEffect(coupon.status) {
        if (coupon.status == CouponStatus.USED && !coupon.trashAsked && coupon.sourceUri?.startsWith("content://media/") == true && Build.VERSION.SDK_INT >= 30) {
            val exists = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(Uri.parse(coupon.sourceUri))?.close(); true }.getOrDefault(false) }
            if (exists) askTrash = true else vm.trashAnswered()
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())) {
        Title(coupon.brand ?: "", back = pop) { Text(dday(coupon, today), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp)) }
        Text(coupon.productName ?: "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        val code = coupon.code
        val width = LocalConfiguration.current.screenWidthDp
        val canRender = code != null && coupon.codeSource == CodeSource.DECODED && BarcodeRenderer.canRender(coupon.codeFormat) && !showOriginal
        if (canRender) {
            val px = (width * context.resources.displayMetrics.density).toInt()
            val bmp = remember(code, coupon.codeFormat, px) { BarcodeRenderer.render(code!!, coupon.codeFormat!!, px, (px * 0.35).toInt()) }
            bmp?.let { Image(it.asImageBitmap(), "바코드", Modifier.fillMaxWidth().padding(vertical = 8.dp), contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.None) }
        } else {
            ImageFile(coupon.imagePath, Modifier.fillMaxWidth(), maxSide = 2400, desc = "쿠폰 원본 이미지")
        }
        if (code != null) {
            val g = BarcodeRenderer.groups(code)
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(g, fontFamily = FontFamily.Monospace, fontSize = if (coupon.codeFormat == "PIN") 30.sp else 24.sp, fontWeight = FontWeight.Bold,
                    color = Color.Black, modifier = Modifier.weight(1f).semantics { contentDescription = g })
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code))
                }) { Text("복사") }
            }
        }
        if (coupon.type == CouponType.AMOUNT && coupon.balance != null) Text("잔액 ${ReminderPlanner.won(coupon.balance)}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge, color = Color.Black)
        if (coupon.issuer?.contains("카카오톡") == true && (Stats.daysLeft(coupon, today) ?: 99) in 0..7) Text("카카오톡 선물함에서 기간 연장이 될 수 있어요", Modifier.padding(16.dp), color = Color.Black)
        Spacer(Modifier.height(12.dp))
        if (coupon.status == CouponStatus.ACTIVE) {
            Button(onClick = { useSheet = true }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(if (coupon.type == CouponType.AMOUNT) "사용 기록" else "사용 완료") }
        } else {
            OutlinedButton(onClick = vm::undoUse, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("되돌리기") }
        }
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { showOriginal = !showOriginal }) { Text(if (showOriginal) "바코드 보기" else "원본 이미지 보기") }
            TextButton(onClick = { push(Screen.EditCoupon(coupon.id)) }) { Text("수정") }
            TextButton(onClick = { reminderSheet = true }) { Text("알림 설정") }
        }
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = {
                val f = File(coupon.imagePath)
                val ext = when (Sharing.imageMime(f)) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
                share.launch(Sharing.intent(context, f, "coupon.$ext", Sharing.imageMime(f)))
            }) { Text("다른 사람에게 보내기") }
            TextButton(onClick = { confirmDelete = true }) { Text("삭제") }
        }
        if (coupon.type == CouponType.AMOUNT && usage.isNotEmpty()) {
            Text("사용 기록", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge, color = Color.Black)
            usage.forEach { u ->
                Text("${java.time.Instant.ofEpochMilli(u.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()} · ${u.spent?.let(ReminderPlanner::won) ?: "-"} 사용 · 남은 ${u.balanceAfter?.let(ReminderPlanner::won) ?: "-"}",
                    Modifier.padding(horizontal = 16.dp), color = Color.Black)
            }
            TextButton(onClick = vm::undoLastBalance, Modifier.padding(horizontal = 8.dp)) { Text("마지막 기록 취소") }
        }
        coupon.notes?.let {
            var open by remember { mutableStateOf(false) }
            TextButton(onClick = { open = !open }, Modifier.padding(horizontal = 8.dp)) { Text("유의사항 원문") }
            if (open) Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = Color.Black)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (useSheet) UseSheet(coupon, onDismiss = { useSheet = false }, vm = vm)
    if (askGiven) AlertDialog(
        onDismissRequest = { askGiven = false },
        title = { Text("이 쿠폰을 넘겼나요?") },
        confirmButton = { TextButton(onClick = { askGiven = false; vm.markUsed(UsedReason.GIVEN) }) { Text("넘겼어요 → 사용 완료로 표시") } },
        dismissButton = { TextButton(onClick = { askGiven = false }) { Text("아니요") } },
    )
    if (askTrash) AlertDialog(
        onDismissRequest = { askTrash = false; vm.trashAnswered() },
        title = { Text("갤러리 원본을 휴지통으로 옮길까요?") },
        text = { Text("30일 안에 되살릴 수 있어요") },
        confirmButton = {
            TextButton(onClick = {
                askTrash = false
                if (Build.VERSION.SDK_INT >= 30) {
                    val pi = MediaStore.createTrashRequest(context.contentResolver, listOf(Uri.parse(coupon.sourceUri)), true)
                    trash.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                }
            }) { Text("옮기기") }
        },
        dismissButton = { TextButton(onClick = { askTrash = false; vm.trashAnswered() }) { Text("그대로 두기") } },
    )
    if (reminderSheet) ReminderSheet(coupon.reminderOffsets, onDismiss = { reminderSheet = false }) { vm.setReminderOffsets(it); reminderSheet = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("이 쿠폰을 삭제할까요?") },
        text = { Text("갤러리 사진은 그대로예요") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { vm.delete().join(); pop() } }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
    )
}

/** S8 */
@Composable
private fun UseSheet(c: Coupon, onDismiss: () -> Unit, vm: CouponViewModel) {
    var text by remember { mutableStateOf("") }
    var spentMode by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val bal = c.balance ?: c.faceValue ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c.type == CouponType.AMOUNT) "얼마 남았나요?" else "사용 완료로 표시할까요?") },
        text = {
            if (c.type == CouponType.AMOUNT) Column {
                OutlinedTextField(text, { text = it.filter(Char::isDigit) }, label = { Text(if (spentMode) "쓴 금액" else "남은 잔액") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                val n = text.toLongOrNull()
                if (n != null) Text("남는 금액 ${ReminderPlanner.won(if (spentMode) bal - n else n)}")
                TextButton(onClick = { spentMode = !spentMode }) { Text(if (spentMode) "남은 잔액으로 입력" else "쓴 금액으로 입력") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (c.type != CouponType.AMOUNT) { vm.markUsed(); onDismiss(); return@TextButton }
                val n = text.toLongOrNull()
                when {
                    n == null -> error = "금액을 입력해 주세요"
                    spentMode && (n <= 0 || n > bal) -> error = "잔액보다 많이 쓸 수 없어요"
                    !spentMode && n > bal -> error = "잔액보다 클 수 없어요"
                    else -> { if (spentMode) vm.recordSpent(n) else vm.recordRemaining(n); onDismiss() }
                }
            }) { Text(if (c.type == CouponType.AMOUNT) "기록" else "사용 완료") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** S12 (null = follow settings) and S11 offsets */
@Composable
private fun ReminderSheet(current: List<Int>?, onDismiss: () -> Unit, onSave: (List<Int>?) -> Unit) {
    var follow by remember { mutableStateOf(current == null) }
    var sel by remember { mutableStateOf(current ?: AppSettings().reminderOffsets) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 쿠폰 알림") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(follow, { follow = it }); Text("기본 설정 따르기") }
                if (!follow) OffsetChecks(sel) { sel = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (follow) null else sel) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun OffsetChecks(sel: List<Int>, onChange: (List<Int>) -> Unit) {
    AppSettings.OFFSET_CHOICES.forEach { d ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(d in sel, { on -> onChange(if (on) sel + d else sel - d) })
            Text(if (d == 0) "당일" else "D-$d")
        }
    }
}

// ---------- S10 / S11 / S14 ----------

@Composable
private fun SettingsScreen(pop: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val st by vm.state.collectAsState()
    val backup by vm.backupUi.collectAsState()
    val context = LocalContext.current
    var wipeStep by remember { mutableStateOf(0) }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var hour by remember(st.app.reminderMinute) { mutableStateOf((st.app.reminderMinute / 60).toString()) }
    var minute by remember(st.app.reminderMinute) { mutableStateOf((st.app.reminderMinute % 60).toString()) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.export(uri, password.toCharArray())
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.open(uri, password.toCharArray())
    }

    Column(Modifier.fillMaxSize()) {
        Title("설정", back = pop)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("만료 전에 쓴 쿠폰 ${st.stats.usedBeforeExpiry}개 · 알림 받고 쓴 쿠폰 ${st.stats.usedViaReminder}개")
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("알림", style = MaterialTheme.typography.titleMedium)
            OffsetChecks(st.app.reminderOffsets) { vm.setReminders(it, st.app.reminderMinute) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, Modifier.width(80.dp), label = { Text("시") })
                OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, Modifier.width(80.dp), label = { Text("분") })
                TextButton(onClick = {
                    val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 9
                    val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    vm.setReminders(st.app.reminderOffsets, h * 60 + m)
                }) { Text("시각 저장") }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("백업·복원", style = MaterialTheme.typography.titleMedium)
            Text("백업 파일에는 바코드 번호가 들어 있어요. 비밀번호를 잊으면 열 수 없어요.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(password, { password = it }, label = { Text("비밀번호") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            OutlinedTextField(password2, { password2 = it }, label = { Text("비밀번호 확인(백업 만들 때)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Row {
                TextButton(enabled = password.isNotEmpty() && password == password2, onClick = { create.launch("mycoupon-backup.mcb") }) { Text("백업 파일 만들기") }
                TextButton(enabled = password.isNotEmpty(), onClick = { openDoc.launch(arrayOf("*/*")) }) { Text("백업 파일 가져오기") }
            }
            when (val b = backup) {
                BackupUi.Idle -> {}
                BackupUi.Working -> Text("처리 중…")
                is BackupUi.Exported -> Text("쿠폰 ${b.count}개를 저장했어요")
                is BackupUi.Preview -> Column {
                    Text("쿠폰 ${b.p.total}개, 그중 이미 있는 쿠폰 ${b.p.skipped}개는 건너뜀")
                    Row {
                        TextButton(onClick = { vm.import(b.p) }) { Text("가져오기") }
                        TextButton(onClick = { vm.cancelImport(b.p) }) { Text("취소") }
                    }
                }
                is BackupUi.Imported -> Text("쿠폰 ${b.count}개를 가져왔어요")
                BackupUi.WrongPassword -> Text("비밀번호가 맞지 않아요", color = MaterialTheme.colorScheme.error)
                is BackupUi.Failed -> Text("파일을 읽을 수 없어요. 기존 데이터는 그대로예요", color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            TextButton(enabled = st.crashFiles.isNotEmpty(), onClick = {
                val f = st.crashFiles.first()
                context.startActivity(Sharing.intent(context, f, f.name, "text/plain"))
            }) { Text("오류 기록 보내기" + if (st.crashFiles.isEmpty()) " (기록 없음)" else "") }
            if (BuildConfig.DEBUG) TextButton(onClick = { throw RuntimeException("QA forced crash 1234567890 스타벅스") }) { Text("(디버그) 강제 종료") }
            TextButton(onClick = { wipeStep = 1 }) { Text("모든 데이터 삭제", color = MaterialTheme.colorScheme.error) }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("개인정보 안내", style = MaterialTheme.typography.titleMedium)
            Text("쿠폰 정보(브랜드, 상품명, 번호, 유효기간, 유의사항)와 쿠폰 사진 사본, 사용 기록을 이 폰의 앱 저장소에만 저장해요. 앱을 지우면 함께 지워져요. 이 앱은 인터넷 권한이 없어요.", style = MaterialTheme.typography.bodySmall)
            Text("오픈소스: PaddleOCR(PP-OCRv5, Apache-2.0), ONNX Runtime(MIT), ZXing(Apache-2.0), RapidOCR ONNX 모델(Apache-2.0), AndroidX(Apache-2.0)", style = MaterialTheme.typography.bodySmall)
            Text("버전 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (wipeStep > 0) AlertDialog(
        onDismissRequest = { wipeStep = 0 },
        title = { Text(if (wipeStep == 1) "모든 데이터를 삭제할까요?" else "정말 삭제할까요? 되돌릴 수 없어요") },
        text = { Text("쿠폰, 이미지 사본, 기록이 모두 지워져요. 갤러리 사진은 그대로예요.") },
        confirmButton = {
            TextButton(onClick = { if (wipeStep == 1) wipeStep = 2 else { wipeStep = 0; vm.wipeAll { pop() } } }) { Text(if (wipeStep == 1) "계속" else "삭제") }
        },
        dismissButton = { TextButton(onClick = { wipeStep = 0 }) { Text("취소") } },
    )
}

// ---------- S9 ----------

@Composable
private fun GalleryScreen(push: (Screen) -> Unit, pop: () -> Unit) {
    val vm: GalleryViewModel = hiltViewModel()
    val p by vm.progress.collectAsState()
    var granted by remember { mutableStateOf(vm.hasPermission()) }
    var range by remember { mutableStateOf(ScanRange.MONTH_3) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = vm.hasPermission() }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Title("갤러리에서 쿠폰 찾기", back = pop)
        if (!granted) {
            Text("갤러리에서 쿠폰 사진을 찾으려면 사진 접근 허용이 필요해요. 사진은 이 폰 안에서만 확인하고, 밖으로 보내지 않아요. '선택한 사진만 허용'을 골라도 돼요.")
            Button(onClick = { request.launch(vm.permissions()) }) { Text("허용하기") }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanRange.entries.forEach { r ->
                FilterChip(range == r, { range = r }, { Text(when (r) { ScanRange.MONTH_1 -> "최근 1개월"; ScanRange.MONTH_3 -> "3개월"; ScanRange.MONTH_12 -> "12개월" }) })
            }
        }
        Row {
            Button(onClick = { vm.scan(range) }, enabled = !p.running) { Text(if (p.checked > 0 && !p.finished) "이어서 찾기" else "찾기") }
            if (p.running) TextButton(onClick = vm::pause) { Text("멈추기") }
        }
        if (p.total > 0) {
            Text("${p.total}장 중 ${p.checked}장 확인, 쿠폰 ${p.found.size}장 찾음")
            LinearProgressIndicator(progress = { p.checked.toFloat() / maxOf(1, p.total) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        if (p.finished && p.found.isEmpty()) Text("쿠폰 같은 사진을 찾지 못했어요. 바코드 없는 상품권은 [사진 고르기]로 추가해 주세요")
        if (p.found.isNotEmpty()) Button(onClick = { vm.sendToReview(); pop(); push(Screen.Import) }) { Text("찾은 쿠폰 ${p.found.size}장 확인하기") }
    }
    DisposableEffect(Unit) { onDispose { vm.pause() } }
}
