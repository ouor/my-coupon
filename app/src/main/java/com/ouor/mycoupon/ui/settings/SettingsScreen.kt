package com.ouor.mycoupon.ui.settings

import com.ouor.mycoupon.ui.component.Ink
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ouor.mycoupon.ui.component.Group
import com.ouor.mycoupon.ui.component.GroupDivider
import com.ouor.mycoupon.ui.theme.statusColors
import com.ouor.mycoupon.ui.component.offsetsLabel
import com.ouor.mycoupon.ui.component.timeLabel
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.ouor.mycoupon.R
import com.ouor.mycoupon.tile.CouponTileService
import com.ouor.mycoupon.ui.component.AppSheet
import com.ouor.mycoupon.ui.component.PrimaryButton
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
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.toHome

/**
 * design-system §6.10 `SettingsGroup` row (redesign): no icon, 52dp, title + optional one-line [sub], an optional grey
 * [value] on the right, chevron when it opens something. Rows sit in a [Group] separated by [GroupDivider].
 */
@Composable
internal fun SettingRow(title: String, sub: String? = null, value: String? = null, danger: Boolean = false, chevron: Boolean = true, onClick: (() -> Unit)?) {
    val color = if (danger) MaterialTheme.statusColors.soon else MaterialTheme.colorScheme.onSurface
    Row((if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
        .fillMaxWidth().heightIn(min = 52.dp).padding(start = 14.dp, end = 10.dp, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            sub?.let { Text(it, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        value?.let { Text(it, Modifier.padding(start = Spacing.s), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (chevron && onClick != null) Icon(Ink.ChevronRight, null, Modifier.padding(start = Spacing.xs).size(18.dp),
            tint = MaterialTheme.statusColors.faint)
    }
}

/** S10 (F13, F14, F18, F19). */
@Composable
fun SettingsScreen(nav: NavHostController) {
    val vm: SettingsViewModel = hiltViewModel()
    val st by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    var wipeStep by remember { mutableStateOf(0) }
    var tileGuide by remember { mutableStateOf(false) }
    val count = st.coupons
    val storage by vm.storage.collectAsStateWithLifecycle()
    LaunchedEffect(st.coupons) { vm.measureStorage() }

    Scaffold(topBar = { AppTopBar("설정", onBack = { nav.popBackStack() }) }, snackbarHost = { SnackbarHost(snackbar.host) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            val g = Modifier.padding(horizontal = Spacing.l)
            // F18: counts only, never money. Owner feedback 11-04: no card at all until something was used
            if (st.stats.usedBeforeExpiry > 0 || st.stats.usedViaReminder > 0) Group(g.padding(top = Spacing.xs)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = Spacing.m).semantics(mergeDescendants = true) {}) {
                    Text("만료 전에 쓴 쿠폰 ${st.stats.usedBeforeExpiry}개", style = MaterialTheme.typography.titleMedium)
                    Text("알림 받고 쓴 쿠폰 ${st.stats.usedViaReminder}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionTitle("알림")
            Group(g) {
                // owner feedback 11-04: a row like the others (whole row opens S10), the value in one line
                SettingRow("만료 알림", "매일 ${timeLabel(st.app.reminderMinute)} · ${offsetsLabel(st.app.reminderOffsets)}") { nav.navigate(Route.Reminders) }
                GroupDivider()
                SettingRow("알림 소리") { context.startActivity(vm.channelSettingsIntent()) }
                GroupDivider()
                // C2-14
                SettingRow("알림창 버튼에 '내 쿠폰' 넣기") {
                    if (Build.VERSION.SDK_INT >= 33) requestAddTile(context) { msg -> snackbar.show(msg) } else tileGuide = true
                }
            }
            SectionTitle("데이터")
            Group(g) {
                SettingRow("백업·복원") { nav.navigate(Route.Backup) }
                GroupDivider()
                // C2-16: read only
                SettingRow("앱이 차지하는 공간", value = storage?.let { "${mb(it.bytes)} · ${it.images}장" } ?: "재는 중", chevron = false, onClick = null)
            }
            SectionTitle("도움")
            Group(g) {
                SettingRow("개인정보 안내") { nav.navigate(Route.Privacy) }
                GroupDivider()
                SettingRow("오류 기록 보내기", value = if (st.crashFiles.isEmpty()) "없음" else "${st.crashFiles.size}건") {
                    val f = st.crashFiles.firstOrNull()
                    if (f == null) snackbar.show("보낼 오류 기록이 없어요") else context.startActivity(Sharing.intent(context, f, f.name, "text/plain"))
                }
                GroupDivider()
                SettingRow("오픈소스 라이선스") { nav.navigate(Route.Licenses) }
                if (BuildConfig.DEBUG) { GroupDivider(); SettingRow("(디버그) 강제 종료", chevron = false) { throw RuntimeException("QA forced crash 1234567890 스타벅스") } }
            }
            // C2-27: the destructive row last, in a group of its own
            Spacer(Modifier.height(Spacing.xl))
            Group(g) { SettingRow("모든 데이터 삭제", danger = true, chevron = false) { wipeStep = 1 } }
            Text("내 쿠폰 ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(vertical = 18.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.statusColors.faint, textAlign = TextAlign.Center)
        }
    }

    if (tileGuide) AppSheet({ tileGuide = false }, "빠른 설정에 '내 쿠폰' 넣기") {
        Text("알림창을 두 번 내려 연필(편집) 단추를 누르고, '내 쿠폰'을 위로 끌어 넣어요.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Spacing.l))
        PrimaryButton("알겠어요", { tileGuide = false })
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
                    Text(body, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

private fun mb(bytes: Long): String = if (bytes < 1_000_000) "1MB 미만" else "${(bytes + 500_000) / 1_000_000}MB"

/** C2-14: Android 13+ system "add tile" dialog; the result comes back as a Snackbar line. */
@RequiresApi(33)
private fun requestAddTile(context: Context, show: (String) -> Unit) {
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return show("이 폰에서는 바로 넣을 수 없어요")
    sbm.requestAddTileService(ComponentName(context, CouponTileService::class.java), context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_tile), ContextCompat.getMainExecutor(context)) { r ->
        when (r) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> show("빠른 설정에 넣었어요")
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> show("이미 빠른 설정에 있어요")
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> {}
            else -> show("지금은 넣을 수 없어요. 알림창 편집에서 넣어 주세요")
        }
    }
}
