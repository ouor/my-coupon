package com.ouor.mycoupon.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.BuildConfig
import com.ouor.mycoupon.ui.Route
import com.ouor.mycoupon.ui.Sharing
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.SectionTitle
import com.ouor.mycoupon.ui.coupon.offsetsLabel
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.toHome

fun timeLabel(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = (h % 12).let { if (it == 0) 12 else it }
    return "$ampm $h12:${"%02d".format(m)}"
}

@Composable
internal fun SettingRow(icon: ImageVector?, title: String, sub: String? = null, danger: Boolean = false, chevron: Boolean = true, onClick: (() -> Unit)?) {
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
    val st by vm.state.collectAsStateWithLifecycle()
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
