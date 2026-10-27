package com.ouor.mycoupon.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.ui.component.AppSheet
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.InfoBanner
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.PrimaryButton
import com.ouor.mycoupon.ui.component.SecondaryButton
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors
import com.ouor.mycoupon.ui.toHome
import java.time.LocalDate

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
