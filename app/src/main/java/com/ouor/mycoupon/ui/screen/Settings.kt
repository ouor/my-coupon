package com.ouor.mycoupon.ui.screen

import androidx.activity.compose.LocalActivity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.BuildConfig
import com.ouor.mycoupon.ui.BackupFailure
import com.ouor.mycoupon.ui.BackupUi
import com.ouor.mycoupon.ui.HomeViewModel
import com.ouor.mycoupon.ui.MainActivity
import com.ouor.mycoupon.ui.Route
import com.ouor.mycoupon.ui.SettingsViewModel
import com.ouor.mycoupon.ui.Sharing
import com.ouor.mycoupon.ui.component.AppSheet
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.InfoBanner
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.PrimaryButton
import com.ouor.mycoupon.ui.component.SecondaryButton
import com.ouor.mycoupon.ui.component.SectionTitle
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors
import com.ouor.mycoupon.ui.toHome
import java.time.LocalDate

fun timeLabel(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = (h % 12).let { if (it == 0) 12 else it }
    return "$ampm $h12:${"%02d".format(m)}"
}

@Composable
private fun SettingRow(icon: ImageVector?, title: String, sub: String? = null, danger: Boolean = false, chevron: Boolean = true, onClick: (() -> Unit)?) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge, color = color) },
        supportingContent = sub?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        leadingContent = icon?.let { { Icon(it, null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent = if (chevron && onClick != null) ({ Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }) else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
    )
}

/** S10 (F13, F14, F18, F19). */
@Composable
fun SettingsScreen(nav: NavHostController) {
    val vm: SettingsViewModel = hiltViewModel()
    val st by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    var wipeStep by remember { mutableStateOf(0) }
    val count = st.coupons

    Scaffold(topBar = { AppTopBar("설정", onBack = { nav.popBackStack() }) }, snackbarHost = { SnackbarHost(snackbar.host) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            // F18: counts only, never money
            Card(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Column(Modifier.padding(Spacing.l).semantics(mergeDescendants = true) {}) {
                    if (st.stats.usedBeforeExpiry == 0 && st.stats.usedViaReminder == 0) {
                        Text("쿠폰을 쓰면 여기에 기록이 쌓여요", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text("만료 전에 쓴 쿠폰 ${st.stats.usedBeforeExpiry}개", style = MaterialTheme.typography.titleMedium)
                        Text("알림 받고 쓴 쿠폰 ${st.stats.usedViaReminder}개", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SectionTitle("알림")
            SettingRow(Icons.Rounded.Notifications, "만료 알림", "${offsetsLabel(st.app.reminderOffsets)}, ${timeLabel(st.app.reminderMinute)}") { nav.navigate(Route.Reminders) }
            SectionTitle("데이터")
            SettingRow(Icons.Rounded.Save, "백업·복원", "파일로 저장하고 새 폰에서 가져와요") { nav.navigate(Route.Backup) }
            SettingRow(Icons.Rounded.DeleteForever, "모든 데이터 삭제", danger = true, chevron = false) { wipeStep = 1 }
            SectionTitle("도움")
            SettingRow(Icons.Rounded.PhonelinkLock, "개인정보 안내", "이 앱은 인터넷을 쓰지 않아요") { nav.navigate(Route.Privacy) }
            SettingRow(Icons.Rounded.BugReport, "오류 기록 보내기", if (st.crashFiles.isEmpty()) "기록이 없어요" else "기록 ${st.crashFiles.size}건") {
                val f = st.crashFiles.firstOrNull()
                if (f == null) snackbar.show("보낼 오류 기록이 없어요") else context.startActivity(Sharing.intent(context, f, f.name, "text/plain"))
            }
            SettingRow(Icons.Rounded.Code, "오픈소스 라이선스") { nav.navigate(Route.Licenses) }
            SettingRow(null, "버전", BuildConfig.VERSION_NAME, chevron = false, onClick = null)
            if (BuildConfig.DEBUG) SettingRow(null, "(디버그) 강제 종료", chevron = false) { throw RuntimeException("QA forced crash 1234567890 스타벅스") }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (wipeStep == 1) AlertDialog(
        onDismissRequest = { wipeStep = 0 },
        title = { Text("모든 쿠폰을 지울까요?") },
        text = { Text("쿠폰 ${count}개, 사진 사본, 사용 기록, 알림이 모두 지워져요. 갤러리 사진은 그대로예요. 되돌릴 수 없어요.") },
        confirmButton = { TextButton({ wipeStep = 2 }) { Text("다음") } },
        dismissButton = { TextButton({ wipeStep = 0 }) { Text("취소") } },
    )
    if (wipeStep == 2) AlertDialog(
        onDismissRequest = { wipeStep = 0 },
        title = { Text("정말 지울까요?") },
        text = { Text("지우기 전에 백업 파일을 만들 수 있어요.") },
        confirmButton = {
            TextButton({ wipeStep = 0; vm.wipeAll { snackbar.show("모든 데이터를 지웠어요"); nav.toHome() } }) {
                Text("모두 지우기", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton({ wipeStep = 0; nav.navigate(Route.Backup) }) { Text("백업 먼저 하기") } },
    )
}

private fun ignoresBattery(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

/** S11 (F8): saved on every change, no save button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(nav: NavHostController) {
    val vm: SettingsViewModel = hiltViewModel()
    val activity = LocalActivity.current as MainActivity
    val homeVm: HomeViewModel = hiltViewModel(activity)
    val homeState by homeVm.state.collectAsState()
    val st by vm.state.collectAsState()
    var timeDialog by remember { mutableStateOf(false) }
    var emptyWarn by remember { mutableStateOf(false) }

    Scaffold(topBar = { AppTopBar("만료 알림", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (homeState.banner == com.ouor.mycoupon.ui.HomeBanner.NOTIFICATIONS_OFF || !androidx.core.app.NotificationManagerCompat.from(activity).areNotificationsEnabled())
                InfoBanner(Icons.Rounded.NotificationsOff, "알림이 꺼져 있어요", MaterialTheme.statusColors.reviewContainer, MaterialTheme.colorScheme.onSurface,
                    Modifier.padding(horizontal = Spacing.l)) { TextButton({ openNotificationSettings(activity) }) { Text("켜기") } }
            SectionTitle("알림 시점 (여러 개 고를 수 있어요)")
            Column(Modifier.padding(horizontal = Spacing.l)) {
                OffsetChecks(st.app.reminderOffsets) { list ->
                    if (list.isEmpty()) emptyWarn = true else { emptyWarn = false; vm.setReminders(list, st.app.reminderMinute) }
                }
                if (emptyWarn) Text("알림 시점을 하나 이상 골라 주세요", Modifier.semantics { error("알림 시점을 하나 이상 골라 주세요") },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            SectionTitle("알림 시각")
            SettingRow(Icons.Rounded.Schedule, timeLabel(st.app.reminderMinute)) { timeDialog = true }
            Text("하루에 한 번만, 그날 챙길 쿠폰을 모아 알려 드려요.", Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ignoresBattery(activity)) {
                Spacer(Modifier.height(Spacing.l))
                InfoBanner(Icons.Rounded.Notifications, "알림이 늦게 오면 배터리 설정에서 ‘제한 없음’으로 바꿔 주세요",
                    MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurface, Modifier.padding(horizontal = Spacing.l)) {
                    TextButton({
                        activity.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
                    }) { Text("설정 열기") }
                }
            }
        }
    }
    if (timeDialog) {
        val state = rememberTimePickerState(st.app.reminderMinute / 60, st.app.reminderMinute % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            title = { Text("알림 시각") },
            text = { TimePicker(state) },
            confirmButton = { TextButton({ vm.setReminders(st.app.reminderOffsets, state.hour * 60 + state.minute); timeDialog = false }) { Text("확인") } },
            dismissButton = { TextButton({ timeDialog = false }) { Text("취소") } },
        )
    }
}

/** S14 (F15). */
@Composable
fun BackupScreen(nav: NavHostController) {
    val vm: SettingsViewModel = hiltViewModel()
    val st by vm.state.collectAsState()
    val backup by vm.backupUi.collectAsState()
    val snackbar = LocalSnackbar.current
    var passwordFor by remember { mutableStateOf<String?>(null) } // "export" / "import"
    var password by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var exporting by remember { mutableStateOf(false) } // which step an IO failure belongs to
    var importing by remember { mutableStateOf(false) } // import is all-or-nothing: no [그만두기]
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { exporting = true; vm.export(uri, password.toCharArray()) }
        password = ""
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { exporting = false; pendingImport = uri; password = ""; passwordFor = "import" }
    }
    LaunchedEffect(backup) {
        val b = backup
        if (b !is BackupUi.Working) importing = false
        if (b is BackupUi.Imported) { snackbar.show("쿠폰 ${b.count}개를 가져왔어요"); vm.backupUi.value = BackupUi.Idle; nav.toHome() }
    }

    Scaffold(topBar = { AppTopBar("백업·복원", onBack = { nav.popBackStack() }) }, snackbarHost = { SnackbarHost(snackbar.host) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            InfoBanner(Icons.Rounded.Lock, "백업 파일에는 바코드 번호가 들어 있어요. 비밀번호로 잠가서 저장해요. 비밀번호를 잊으면 아무도 열 수 없어요.",
                MaterialTheme.statusColors.reviewContainer, MaterialTheme.colorScheme.onSurface, Modifier.padding(horizontal = Spacing.l))
            Spacer(Modifier.height(Spacing.m))
            val last = st.lastBackupAt?.let { " · 마지막 백업 " + com.ouor.mycoupon.ui.component.epochDay(it).let { d -> "${d.monthValue}월 ${d.dayOfMonth}일" } } ?: ""
            SettingRow(Icons.Rounded.Save, "백업 파일 만들기", if (st.coupons == 0) "백업할 쿠폰이 없어요" else "쿠폰 ${st.coupons}개$last",
                onClick = if (st.coupons == 0) null else ({ password = ""; passwordFor = "export" }))
            SettingRow(Icons.Rounded.Download, "백업 파일 가져오기", "다른 폰에서 만든 파일을 합쳐요") { openDoc.launch(arrayOf("*/*")) }
            Text("파일은 내가 고른 곳에만 저장돼요. 앱이 어디로도 보내지 않아요.", Modifier.padding(Spacing.l),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    when (passwordFor) {
        "export" -> PasswordSheet("비밀번호를 정해 주세요", confirm = true, button = "저장할 곳 고르기", onDismiss = { passwordFor = null }) { pw ->
            password = pw; passwordFor = null
            create.launch("내쿠폰-백업-${LocalDate.now()}.mcbak")
        }
        "import" -> PasswordSheet("비밀번호를 입력해 주세요", confirm = false, button = "열기",
            wrong = backup is BackupUi.WrongPassword,
            working = backup is BackupUi.Working,
            onDismiss = { vm.cancelBackup(); passwordFor = null; vm.backupUi.value = BackupUi.Idle }) { pw ->
            pendingImport?.let { vm.open(it, pw.toCharArray()) }
        }
    }
    LaunchedEffect(backup) { if (backup is BackupUi.Preview || backup is BackupUi.Failed) passwordFor = null }

    when (val b = backup) {
        BackupUi.Working -> if (passwordFor == null) AppSheet({}, if (exporting) "백업 파일 만드는 중" else "잠시만요", "백업 파일을 다루는 중이에요") {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.m))
            if (!importing) SecondaryButton("그만두기", { vm.cancelBackup() })
        }
        is BackupUi.Exported -> AppSheet({ vm.backupUi.value = BackupUi.Idle }, "쿠폰 ${b.count}개를 저장했어요",
            "새 폰에서는 [백업 파일 가져오기]로 열어요. 비밀번호를 꼭 기억해 주세요.") { PrimaryButton("확인", { vm.backupUi.value = BackupUi.Idle }) }
        is BackupUi.Preview -> {
            val n = b.p.total - b.p.skipped
            AppSheet({ vm.cancelImport(b.p) }, if (n == 0) "새로 가져올 쿠폰이 없어요" else "쿠폰 ${b.p.total}개를 찾았어요",
                if (n == 0) "이미 모두 있어요." else listOfNotNull("그중 이미 있는 쿠폰 ${b.p.skipped}개는 건너뛰어요.".takeIf { b.p.skipped > 0 }, "사용 기록·알림 설정도 함께 가져와요.").joinToString(" ")) {
                if (n > 0) PrimaryButton("${n}개 가져오기", { importing = true; vm.import(b.p) })
                SecondaryButton(if (n == 0) "닫기" else "취소", { vm.cancelImport(b.p) })
            }
        }
        is BackupUi.Failed -> when (b.reason) {
            // C2: user stopped it: no error sheet, just say nothing changed
            BackupFailure.CANCELLED -> LaunchedEffect(b) { vm.backupUi.value = BackupUi.Idle; snackbar.show("그만뒀어요. 지금 쿠폰은 그대로예요") }
            BackupFailure.FORMAT -> AppSheet({ vm.backupUi.value = BackupUi.Idle }, "이 파일은 열 수 없어요",
                "내 쿠폰 백업 파일이 아니거나 파일이 손상됐어요. 지금 쿠폰은 그대로예요.") {
                PrimaryButton("다른 파일 고르기", { vm.backupUi.value = BackupUi.Idle; openDoc.launch(arrayOf("*/*")) })
                SecondaryButton("닫기", { vm.backupUi.value = BackupUi.Idle })
            }
            BackupFailure.IO -> AppSheet({ vm.backupUi.value = BackupUi.Idle },
                if (exporting) "저장하지 못했어요" else "가져오지 못했어요",
                if (exporting) "저장 공간을 확인하고 다시 해 주세요." else "파일을 읽지 못했어요. 지금 쿠폰은 그대로예요.") {
                PrimaryButton("다시 하기", {
                    vm.backupUi.value = BackupUi.Idle
                    if (exporting) { password = ""; passwordFor = "export" } else openDoc.launch(arrayOf("*/*"))
                })
                SecondaryButton("닫기", { vm.backupUi.value = BackupUi.Idle })
            }
        }
        else -> {}
    }
}

@Composable
private fun PasswordSheet(
    title: String, confirm: Boolean, button: String, onDismiss: () -> Unit,
    wrong: Boolean = false, working: Boolean = false, onDone: (String) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val tooShort = confirm && pw.isNotEmpty() && pw.length < 6
    val mismatch = confirm && pw2.isNotEmpty() && pw != pw2
    val ok = if (confirm) pw.length >= 6 && pw == pw2 else pw.isNotEmpty()
    AppSheet(onDismiss, title, if (confirm) "6자 이상으로 정해 주세요" else null) {
        val vt = if (show) VisualTransformation.None else PasswordVisualTransformation()
        val eye: @Composable () -> Unit = {
            IconButton({ show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "비밀번호 숨기기" else "비밀번호 보기") }
        }
        val err = when { wrong -> "비밀번호가 맞지 않아요"; tooShort -> "6자 이상이어야 해요"; else -> null }
        OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), label = { Text("비밀번호") }, singleLine = true, visualTransformation = vt,
            trailingIcon = eye, isError = err != null, supportingText = err?.let { { Text(it, Modifier.semantics { error(it) }) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (confirm) OutlinedTextField(pw2, { pw2 = it }, Modifier.fillMaxWidth(), label = { Text("비밀번호 확인") }, singleLine = true, visualTransformation = vt,
            isError = mismatch, supportingText = if (mismatch) ({ Text("두 비밀번호가 달라요", Modifier.semantics { error("두 비밀번호가 달라요") }) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = Spacing.s))
        Spacer(Modifier.height(Spacing.m))
        PrimaryButton(button, { onDone(pw) }, enabled = ok && !working)
        SecondaryButton("취소", onDismiss)
    }
}

/** Privacy notice (F13), read-only. */
@Composable
fun PrivacyScreen(nav: NavHostController) {
    val sections = listOf(
        "저장하는 것" to "쿠폰 정보(브랜드, 상품명, 번호, 유효기간, 유의사항), 쿠폰 사진 사본, 사용 기록, 알림 설정",
        "저장하는 곳" to "이 폰의 앱 저장 공간. 다른 앱은 볼 수 없어요.",
        "보내지 않아요" to "이 앱에는 인터넷 권한이 없어요. 광고·분석 도구도 없어요.",
        "폰 밖으로 나가는 경우" to "내가 고른 경우만이에요: 백업 파일 저장, 쿠폰 보내기, 오류 기록 보내기.",
        "앱을 지우면" to "모든 데이터가 함께 지워져요. 백업 파일은 남아요.",
    )
    TextPage("개인정보 안내", sections, nav)
}

/** Open-source notices. */
@Composable
fun LicensesScreen(nav: NavHostController) {
    val libs = listOf(
        "PaddleOCR / PP-OCRv5 모델" to "Apache License 2.0 · PaddlePaddle Authors",
        "RapidOCR ONNX 모델" to "Apache License 2.0 · RapidAI",
        "ONNX Runtime" to "MIT License · Microsoft",
        "ZXing" to "Apache License 2.0 · ZXing authors",
        "Android Jetpack (Compose, Material 3, Navigation, Room, WorkManager, Glance, Hilt)" to "Apache License 2.0 · The Android Open Source Project",
        "Material Icons" to "Apache License 2.0 · Google",
        "Coil" to "Apache License 2.0 · Coil Contributors",
        "Dagger / Hilt" to "Apache License 2.0 · The Dagger Authors",
        "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0 · JetBrains",
    )
    TextPage("오픈소스 라이선스", libs, nav)
}

@Composable
private fun TextPage(title: String, sections: List<Pair<String, String>>, nav: NavHostController) {
    Scaffold(topBar = { AppTopBar(title, onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            sections.forEach { (h, body) ->
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(h, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
