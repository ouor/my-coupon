package com.ouor.mycoupon.ui.screen

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.data.CodeSource
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.ocr.core.CouponType
import com.ouor.mycoupon.ui.CouponViewModel
import com.ouor.mycoupon.ui.ImportViewModel
import com.ouor.mycoupon.ui.MainActivity
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
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Composable
fun EditDraftScreen(key: Int, nav: NavHostController) {
    val vm: ImportViewModel = hiltViewModel(LocalContext.current as MainActivity)
    val st by vm.state.collectAsState()
    val brands by vm.brands.collectAsState()
    val d = st.drafts.firstOrNull { it.key == key }?.coupon
    if (d == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    CouponEditor(d, brands, onSave = { vm.edit(key, it); nav.popBackStack() }, onBack = { nav.popBackStack() })
}

@Composable
fun EditCouponScreen(id: Long, nav: NavHostController) {
    val vm: CouponViewModel = hiltViewModel()
    LaunchedEffect(id) { vm.load(id) }
    val c by vm.coupon.collectAsState()
    val brands by vm.brands.collectAsState()
    val snackbar = LocalSnackbar.current
    c?.let { CouponEditor(it, brands, onSave = { n -> vm.save(n); snackbar.show("저장했어요"); nav.popBackStack() }, onBack = { nav.popBackStack() }) }
}

private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

/** S5 (F3). [맞아요] / editing a field clears that field's review reasons. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CouponEditor(initial: Coupon, brands: List<String>, onSave: (Coupon) -> Unit, onBack: () -> Unit) {
    var c by remember(initial.id, initial.imagePath) { mutableStateOf(initial) }
    var noExpiry by remember(initial.imagePath) { mutableStateOf(initial.expiryDate == null && !initial.needsReview.containsKey("expiryDate")) }
    var codeError by remember { mutableStateOf<String?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    fun confirm(field: String) { c = c.copy(needsReview = c.needsReview - field) }
    val changed = c != initial
    val leave = { if (changed) confirmLeave = true else onBack() }
    BackHandler(onBack = leave)

    fun save() {
        codeError = if (c.code.isNullOrBlank()) "번호를 입력해 주세요" else null
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
        topBar = { AppTopBar("쿠폰 확인", onBack = leave) { TextButton(::save) { Text("저장", style = MaterialTheme.typography.labelLarge) } } },
        bottomBar = { BottomCta("저장", ::save) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            SourceImage(c)
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (c.needsReview.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.statusColors.review)
                    Text("  확인할 항목 ${c.needsReview.size}개", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.statusColors.review)
                }
                BrandField(c.brand ?: "", brands, review = c.needsReview["brand"]) { c = c.copy(brand = it.ifBlank { null }); confirm("brand") }
                ReviewLine("brand", c.needsReview["brand"]) { confirm("brand") }

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
                Surface(onClick = { if (!noExpiry) datePicker = true }, enabled = !noExpiry) {
                    OutlinedTextField(
                        if (noExpiry) "기간 없음" else c.expiryDate?.dotted() ?: "", {}, Modifier.fillMaxWidth(), readOnly = true, enabled = false,
                        label = { Text("유효기간") }, placeholder = { Text("날짜를 골라 주세요") },
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
                if (expiryReview != null && "MULTIPLE_CANDIDATES" in expiryReview && c.expiryCandidates.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    c.expiryCandidates.forEach { d ->
                        FilterChip(c.expiryDate == d, { c = c.copy(expiryDate = d); confirm("expiryDate") }, { Text(d.dotted()) }, Modifier.heightIn(min = Spacing.touch))
                    }
                }
                if (!noExpiry) ReviewLine("expiryDate", expiryReview) { confirm("expiryDate") }
                Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(noExpiry, { noExpiry = it; if (it) confirm("expiryDate") })
                    Text("유효기간 없음", style = MaterialTheme.typography.bodyLarge)
                }

                Field("번호", c.code ?: "", review = c.needsReview["code"], error = codeError, mono = true) {
                    c = c.copy(code = it.trim().ifBlank { null }); confirm("code"); codeError = null
                }
                ReviewLine("code", c.needsReview["code"]) { confirm("code") }

                Field("메모", c.memo ?: "", review = c.needsReview["sender"], singleLine = false) { c = c.copy(memo = it.ifBlank { null }); confirm("sender") }
                ReviewLine("sender", c.needsReview["sender"]) { confirm("sender") }

                c.notes?.let { notes ->
                    TextButton({ notesOpen = !notesOpen }, Modifier.heightIn(min = Spacing.touch)) {
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
        val state = rememberDatePickerState(initialSelectedDateMillis = utc)
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { ms -> c = c.copy(expiryDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()); confirm("expiryDate") }
                    datePicker = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton({ datePicker = false }) { Text("취소") } },
        ) { DatePicker(state) }
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
            label = { Text("브랜드") }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge, colors = fieldColors(review != null))
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
        if (justConfirmed) Row(verticalAlignment = Alignment.CenterVertically) {
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
        if (reason != "NOT_FOUND") TextButton({ onConfirm(); justConfirmed = true }, Modifier.heightIn(min = Spacing.touch)
            .semantics { contentDescription = "${(fieldLabel[field] ?: "").subject()} 맞아요" }) { Text("맞아요", style = MaterialTheme.typography.labelLarge) }
    }
}

/** Original image (40 % of the screen height), pinch to zoom, review boxes outlined. */
@Composable
private fun SourceImage(c: Coupon) {
    var bmp by remember(c.imagePath) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(c.imagePath) { mutableStateOf(false) }
    LaunchedEffect(c.imagePath) { bmp = loadImage(c.imagePath, 2000); failed = bmp == null }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val tf = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    val h = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
    val border = MaterialTheme.statusColors.reviewBorder
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(h).clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerHighest)
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
