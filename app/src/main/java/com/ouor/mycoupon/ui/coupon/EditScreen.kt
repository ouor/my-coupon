package com.ouor.mycoupon.ui.coupon

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DisplayMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import com.ouor.mycoupon.ui.component.isLargeFont
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ouor.mycoupon.domain.ImportSession
import androidx.compose.runtime.saveable.rememberSaveable
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.data.CodeSource
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.BottomCta
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.dotted
import com.ouor.mycoupon.ui.component.fieldLabel
import com.ouor.mycoupon.ui.component.firstReason
import com.ouor.mycoupon.ui.component.loadImage
import com.ouor.mycoupon.ui.component.reasonText
import com.ouor.mycoupon.ui.component.subject
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors
import com.ouor.mycoupon.ui.toHome
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.launch

/** S5 for an import draft; [draft] null = gone from the batch. Nav.kt passes the import session's pieces (code review A2-05). */
@Composable
fun EditDraftScreen(draft: Coupon?, brands: List<String>, nav: NavHostController, onSave: (Coupon) -> Unit, onExclude: () -> Unit) {
    if (draft == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    CouponEditor(draft, brands, onSave = { onSave(it); nav.popBackStack() }, onBack = { nav.popBackStack() },
        onNotCoupon = { onExclude(); nav.popBackStack() })
}

@Composable
fun EditCouponScreen(id: Long, nav: NavHostController) {
    val vm: CouponViewModel = hiltViewModel()
    LaunchedEffect(id) { vm.load(id) }
    val c by vm.coupon.collectAsStateWithLifecycle()
    val brands by vm.brands.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    c?.let { CouponEditor(it, brands, codeRequired = it.code != null, onSave = { n -> vm.save(n); snackbar.show("저장했어요"); nav.popBackStack() }, onBack = { nav.popBackStack() },
        onNotCoupon = { confirmDelete = true }) }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("이 쿠폰을 지울까요?") },
        text = { Text("쿠폰이 아닌 사진이면 목록에서 지워요. 갤러리 사진은 그대로예요.") },
        confirmButton = { TextButton({ confirmDelete = false; scope.launch { vm.delete().join(); snackbar.show("쿠폰을 지웠어요"); nav.toHome() } }) {
            Text("지우기", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("그대로 두기") } },
    )
}

private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

/** "2026년 11월 1일 일요일" (TalkBack, code review U-08) */
private fun spokenDate(d: LocalDate) = d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN))

/** S5 (F3). [맞아요] / editing a field clears that field's review reasons. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CouponEditor(initial: Coupon, brands: List<String>, codeRequired: Boolean = true, onSave: (Coupon) -> Unit, onBack: () -> Unit, onNotCoupon: () -> Unit) {
    // code review A8: the values being edited survive a process death (e.g. while checking a PIN in KakaoTalk)
    var c by rememberSaveable(initial.id, initial.imagePath) { mutableStateOf(initial) }
    var noExpiry by rememberSaveable(initial.imagePath) { mutableStateOf(initial.expiryDate == null && !initial.needsReview.containsKey("expiryDate")) }
    var codeError by remember { mutableStateOf<String?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    // C2-18: null = closed, "" = whole image, else the fieldBoxes key the viewer zooms to
    var viewer by rememberSaveable { mutableStateOf<String?>(null) }
    fun confirm(field: String) { c = c.copy(needsReview = c.needsReview - field) }
    val changed = c != initial
    val large = isLargeFont()
    val leave = { if (changed) confirmLeave = true else onBack() }
    BackHandler(onBack = leave)

    fun save() {
        // code review A20: a saved coupon that never had a number (shown by its image) can be edited without one
        codeError = if (codeRequired && c.code.isNullOrBlank()) "번호를 입력해 주세요" else null
        balanceError = if (c.type == CouponType.AMOUNT && c.balance != null && c.faceValue != null && c.balance!! > c.faceValue!!) "잔액이 금액보다 클 수 없어요" else null
        if (codeError != null || balanceError != null) return
        val codeChanged = c.code != initial.code
        onSave(c.copy(
            expiryDate = if (noExpiry) null else c.expiryDate,
            needsReview = if (noExpiry) c.needsReview - "expiryDate" else c.needsReview,
            codeKey = Coupon.codeKey(c.code),
            codeSource = if (codeChanged) CodeSource.USER else c.codeSource,
            codeFormat = if (codeChanged) (initial.codeFormat ?: "PIN") else c.codeFormat,
        ))
    }

    Scaffold(
        topBar = { AppTopBar("쿠폰 확인", onBack = leave) {
            // review U2: a receipt read as a coupon must be removable right here; review U7: [저장] only at the bottom
            // code review U-11: at 150 %+ it does not fit the top bar, so it moves to the first body row
            if (!large) OutlinedButton(onNotCoupon, Modifier.padding(end = Spacing.m).heightIn(min = 40.dp)) { Text("쿠폰 아님 · 빼기", style = MaterialTheme.typography.labelLarge) }
        } },
        bottomBar = { BottomCta("저장", ::save) },
    ) { pad ->
        // code review A18: number and memo fields scroll above the keyboard
        Column(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                // C2-18: the 40 % image is gone (it pushed the fields off small / 200 % screens); crops sit above each review field
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
                    if (c.needsReview.isNotEmpty()) Row(Modifier.padding(end = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.statusColors.review)
                        Text("  확인할 항목 ${c.needsReview.size}개", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.statusColors.review)
                    }
                    OutlinedButton({ viewer = "" }, Modifier.heightIn(min = Spacing.touch)) {
                        Icon(Icons.Rounded.Image, null, Modifier.padding(end = Spacing.xs)); Text("원본 전체 보기")
                    }
                    if (large) OutlinedButton(onNotCoupon, Modifier.heightIn(min = Spacing.touch)) { Text("쿠폰 아님 · 빼기", style = MaterialTheme.typography.labelLarge) }
                }
                if ("brand" in c.needsReview) SourceCrop(c, "brand") { viewer = it }
                BrandField(c.brand ?: "", brands, review = c.needsReview["brand"]) { c = c.copy(brand = it.ifBlank { null }); confirm("brand") }
                ReviewLine("brand", c.needsReview["brand"]) { confirm("brand") }

                if ("productName" in c.needsReview) SourceCrop(c, "productName") { viewer = it }
                Field("상품명", c.productName ?: "", review = c.needsReview["productName"]) { c = c.copy(productName = it.ifBlank { null }); confirm("productName") }
                ReviewLine("productName", c.needsReview["productName"]) { confirm("productName") }

                Text("유형", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(c.type == CouponType.EXCHANGE, { c = c.copy(type = CouponType.EXCHANGE, faceValue = null, balance = null) },
                        SegmentedButtonDefaults.itemShape(0, 2), Modifier.heightIn(min = Spacing.touch)) { Text("교환·할인권") }
                    SegmentedButton(c.type == CouponType.AMOUNT, { c = c.copy(type = CouponType.AMOUNT) },
                        SegmentedButtonDefaults.itemShape(1, 2), Modifier.heightIn(min = Spacing.touch)) { Text("금액권") }
                }
                if (c.type == CouponType.AMOUNT) {
                    if ("faceValue" in c.needsReview) SourceCrop(c, "faceValue") { viewer = it }
                    Field("금액", c.faceValue?.let(numberFormat::format) ?: "", review = c.needsReview["faceValue"], number = true, suffix = "원") { v ->
                        val n = v.filter(Char::isDigit).take(9).toLongOrNull()
                        c = c.copy(faceValue = n, balance = if (c.balance == null || c.balance == c.faceValue) n else c.balance); confirm("faceValue")
                    }
                    ReviewLine("faceValue", c.needsReview["faceValue"]) { confirm("faceValue") }
                    Field("잔액", c.balance?.let(numberFormat::format) ?: "", number = true, suffix = "원", error = balanceError) { v ->
                        c = c.copy(balance = v.filter(Char::isDigit).take(9).toLongOrNull()); balanceError = null
                    }
                }

                // expiry: read-only field + date picker (§6.10)
                val expiryReview = c.needsReview["expiryDate"]
                val candidates = expiryReview != null && "MULTIPLE_CANDIDATES" in expiryReview && c.expiryCandidates.size > 1
                if (expiryReview != null && !candidates && !noExpiry) SourceCrop(c, "expiryDate") { viewer = it }
                val expirySpoken = listOfNotNull("유효기간", if (noExpiry) "기간 없음" else c.expiryDate?.let(::spokenDate) ?: "날짜를 골라 주세요",
                    "확인 필요".takeIf { expiryReview != null }).joinToString(", ")
                Surface(onClick = { if (!noExpiry) datePicker = true }, Modifier.clearAndSetSemantics {
                    contentDescription = expirySpoken; role = Role.Button
                    if (noExpiry) disabled() else onClick("유효기간 고르기") { datePicker = true; true }
                }, enabled = !noExpiry) {
                    OutlinedTextField(
                        if (noExpiry) "기간 없음" else c.expiryDate?.dotted() ?: "", {}, Modifier.fillMaxWidth(), readOnly = true, enabled = false,
                        label = { Text("유효기간") },
                        leadingIcon = reviewIcon(expiryReview != null),
                        trailingIcon = { Icon(Icons.Rounded.CalendarMonth, "유효기간 고르기") },
                        textStyle = MaterialTheme.typography.titleMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = if (expiryReview != null) MaterialTheme.statusColors.reviewBorder else MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                // C2-18: each candidate with the part of the image it was read from, crop above its chip
                if (candidates) c.expiryCandidates.forEach { d ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SourceCrop(c, ImportSession.candidateBoxKey(d), label = "유효기간 후보 ${d.dotted()}") { viewer = it }
                        FilterChip(c.expiryDate == d, { c = c.copy(expiryDate = d); confirm("expiryDate") }, { Text(d.dotted()) }, Modifier.heightIn(min = Spacing.touch))
                    }
                }
                if (!noExpiry) ReviewLine("expiryDate", expiryReview) { confirm("expiryDate") }
                Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch)
                    .toggleable(noExpiry, role = Role.Checkbox) { noExpiry = it; if (it) confirm("expiryDate") },
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(noExpiry, null)
                    Text("유효기간 없음", style = MaterialTheme.typography.bodyLarge)
                }

                if ("code" in c.needsReview) SourceCrop(c, "code") { viewer = it }
                Field("번호", c.code ?: "", review = c.needsReview["code"], error = codeError, mono = true) {
                    c = c.copy(code = it.trim().ifBlank { null }); confirm("code"); codeError = null
                }
                ReviewLine("code", c.needsReview["code"]) { confirm("code") }

                // C2-19: a PIN / 인증번호 apart from the barcode, typed by hand (never recognised)
                OutlinedTextField(c.authCode ?: "", { v -> c = c.copy(authCode = v.filter(Char::isLetterOrDigit).uppercase().take(32).ifBlank { null }) },
                    Modifier.fillMaxWidth(), label = { Text("인증번호(선택)") }, singleLine = true,
                    supportingText = { Text("바코드와 따로 있는 인증번호·PIN") },
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontFeatureSettings = "tnum"),
                    visualTransformation = FourGroups,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false))

                if ("sender" in c.needsReview) SourceCrop(c, "sender") { viewer = it }
                Field("메모", c.memo ?: "", review = c.needsReview["sender"], singleLine = false) { c = c.copy(memo = it.ifBlank { null }); confirm("sender") }
                ReviewLine("sender", c.needsReview["sender"]) { confirm("sender") }

                c.notes?.let { notes ->
                    TextButton({ notesOpen = !notesOpen }, Modifier.heightIn(min = Spacing.touch).semantics { stateDescription = if (notesOpen) "펼쳐짐" else "접힘" }) {
                        Text("유의사항 원문", style = MaterialTheme.typography.labelLarge)
                        Icon(if (notesOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    if (notesOpen) Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }

    if (datePicker) {
        val utc = (c.expiryDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        // code review U-12: the calendar does not fit at 150 %+; the typed form does, and it scrolls
        val state = rememberDatePickerState(initialSelectedDateMillis = utc, initialDisplayMode = if (large) DisplayMode.Input else DisplayMode.Picker)
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { ms -> c = c.copy(expiryDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()); confirm("expiryDate") }
                    datePicker = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton({ datePicker = false }) { Text("취소") } },
        ) { DatePicker(state, if (large) Modifier.verticalScroll(rememberScrollState()) else Modifier) }
    }
    viewer?.let { key ->
        Dialog({ viewer = null }, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                SourceImage(c, c.fieldBoxes[key], Modifier.fillMaxSize())
                IconButton({ viewer = null }, Modifier.align(Alignment.TopEnd).padding(Spacing.s).size(56.dp)) { Icon(Icons.Rounded.Close, "닫기") }
            }
        }
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("고친 내용을 버릴까요?") },
        confirmButton = { TextButton({ confirmLeave = false; onBack() }) { Text("버리기", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmLeave = false }) { Text("계속 고치기") } },
    )
}

@Composable
private fun fieldColors(review: Boolean) = if (review) OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.statusColors.reviewBorder, focusedBorderColor = MaterialTheme.statusColors.reviewBorder,
) else OutlinedTextFieldDefaults.colors()

@Composable
private fun Field(
    label: String, value: String, review: List<String>? = null, error: String? = null, number: Boolean = false,
    suffix: String? = null, mono: Boolean = false, singleLine: Boolean = true, onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) },
        singleLine = singleLine, isError = error != null,
        supportingText = error?.let { { Text(it, Modifier.semantics { error(it) }) } },
        suffix = suffix?.let { { Text(it) } },
        textStyle = if (mono) MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontFeatureSettings = "tnum")
        else MaterialTheme.typography.bodyLarge,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        leadingIcon = reviewIcon(review != null && error == null),
        colors = fieldColors(review != null && error == null),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrandField(value: String, brands: List<String>, review: List<String>?, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = brands.filter { it.contains(value.trim(), ignoreCase = true) && it != value }.take(6)
    ExposedDropdownMenuBox(open && options.isNotEmpty(), { open = it }) {
        OutlinedTextField(value, { onChange(it); open = true }, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            label = { Text("브랜드") }, singleLine = true,
            supportingText = if (value.isBlank()) ({ Text("쿠폰을 쓰는 가게 이름이에요 (예: 스타벅스, CU)") }) else null, textStyle = MaterialTheme.typography.bodyLarge, colors = fieldColors(review != null),
            leadingIcon = reviewIcon(review != null))
        ExposedDropdownMenu(open && options.isNotEmpty(), { open = false }) {
            options.forEach { b -> DropdownMenuItem({ Text(b, style = MaterialTheme.typography.bodyLarge) }, { onChange(b); open = false }) }
        }
    }
}

/** ReviewMark + first reason copy + [맞아요] (no [맞아요] for NOT_FOUND). Shows "확인했어요" for a second after confirming. */
@Composable
private fun ReviewLine(field: String, reasons: List<String>?, onConfirm: () -> Unit) {
    var justConfirmed by remember { mutableStateOf(false) }
    LaunchedEffect(justConfirmed) { if (justConfirmed) { kotlinx.coroutines.delay(1000); justConfirmed = false } }
    val s = MaterialTheme.statusColors
    if (reasons == null) {
        if (justConfirmed) Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = s.available)
            Text(" 확인했어요", style = MaterialTheme.typography.labelMedium, color = s.available)
        }
        return
    }
    val reason = firstReason(reasons)
    val text = reasonText(field, reason)
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "확인 필요, $text" }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = s.review)
                Text(" 확인 필요", style = MaterialTheme.typography.labelMedium, color = s.review)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (reason != "NOT_FOUND") OutlinedButton({ onConfirm(); justConfirmed = true }, Modifier.heightIn(min = Spacing.touch)
            .semantics { contentDescription = "${(fieldLabel[field] ?: "").subject()} 맞아요" }) { Text("맞아요", style = MaterialTheme.typography.labelLarge) }
    }
}

/** C2-18 viewer: the whole original, pinch to zoom, review boxes outlined, opened zoomed onto [focus] (original px). */
@Composable
private fun SourceImage(c: Coupon, focus: List<Double>?, modifier: Modifier) {
    var bmp by remember(c.imagePath) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(c.imagePath) { mutableStateOf(false) }
    LaunchedEffect(c.imagePath) { bmp = loadImage(c.imagePath, 2000); failed = bmp == null }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val tf = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    val border = MaterialTheme.statusColors.reviewBorder
    BoxWithConstraints(
        modifier.clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = "쿠폰 원본 이미지, 두 손가락으로 확대" }
            .transformable(tf),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b == null) {
            if (failed) Text("원본 이미지를 열 수 없어요", style = MaterialTheme.typography.bodyLarge)
            return@BoxWithConstraints
        }
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val fit = minOf(boxW / b.width, boxH / b.height)
        val dw = b.width * fit
        val dh = b.height * fit
        LaunchedEffect(b, focus) {
            val r = focus?.takeIf { it.size >= 4 } ?: return@LaunchedEffect
            val k0 = fit * b.width / (if (c.imageWidth > 0) c.imageWidth.toFloat() else b.width.toFloat())
            val w = ((r[2] - r[0]).toFloat() * k0).coerceAtLeast(1f)
            scale = (boxW * 0.8f / w).coerceIn(1f, 6f)
            // graphicsLayer scales about the centre: move the box centre there
            val cx = (boxW - dw) / 2 + (r[0] + r[2]).toFloat() / 2 * k0 - boxW / 2
            val cy = (boxH - dh) / 2 + (r[1] + r[3]).toFloat() / 2 * k0 - boxH / 2
            offset = Offset(-cx * scale, -cy * scale)
        }
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            val srcW = if (c.imageWidth > 0) c.imageWidth.toFloat() else b.width.toFloat()
            val k = fit * b.width / srcW
            Canvas(Modifier.fillMaxSize()) {
                val ox = (boxW - dw) / 2
                val oy = (boxH - dh) / 2
                c.needsReview.keys.mapNotNull { key -> c.fieldBoxes[key] }.forEach { r ->
                    if (r.size < 4) return@forEach
                    val pad = 6f
                    drawRect(border, Offset(ox + r[0].toFloat() * k - pad, oy + r[1].toFloat() * k - pad),
                        Size((r[2] - r[0]).toFloat() * k + 2 * pad, (r[3] - r[1]).toFloat() * k + 2 * pad), style = Stroke(width = 2.dp.toPx() / scale))
                }
            }
        }
    }
}

/**
 * design-system SourceCrop (C2-18): the part of the original a review value was read from, at least twice its size;
 * the whole image when there is no box. Tap = the full viewer zoomed there.
 */
@Composable
private fun SourceCrop(c: Coupon, key: String, label: String = "${fieldLabel[key] ?: "값"}", onOpen: (String) -> Unit) {
    // round-2 V5: no box → no crop (the whole image repeated per field filled the screen); [원본 전체 보기] covers it
    val box = c.fieldBoxes[key] ?: return
    val bmp by produceState<Bitmap?>(null, c.imagePath, key) { value = loadImage(c.imagePath, 2000, box, c.imageWidth) }
    val b = bmp ?: return
    Surface({ onOpen(key) }, Modifier.fillMaxWidth().semantics { contentDescription = "원본에서 ${label}을 읽은 부분, 누르면 크게 보기" },
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Image(b.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 96.dp).padding(Spacing.xs),
            contentScale = ContentScale.Fit)
    }
}

/** C2-07: review fields carry a shape too (readable in grey scale), not only the orange border. */
@Composable
private fun reviewIcon(on: Boolean): (@Composable () -> Unit)? =
    if (on) ({ Icon(Icons.Rounded.ErrorOutline, "확인 필요", tint = MaterialTheme.statusColors.review) }) else null

/** C2-19: "ABCD EFGH 1234" while typing; the stored value has no spaces. */
internal object FourGroups : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = raw.chunked(4).joinToString(" ")
        return TransformedText(AnnotatedString(out), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + (offset - 1).coerceAtLeast(0) / 4
            override fun transformedToOriginal(offset: Int) = (offset - offset / 5).coerceIn(0, raw.length)
        })
    }
}
