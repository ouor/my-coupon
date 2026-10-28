package com.ouor.mycoupon.ui.settings

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ouor.mycoupon.ui.component.OffsetChecks
import com.ouor.mycoupon.ui.component.TimePickDialog
import com.ouor.mycoupon.ui.component.openNotificationSettings
import com.ouor.mycoupon.ui.component.timeLabel
import androidx.compose.material.icons.rounded.MusicNote
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.InfoBanner
import com.ouor.mycoupon.ui.component.SectionTitle
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors

private fun ignoresBattery(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

/** S11 (F8): saved on every change, no save button. */
@Composable
fun RemindersScreen(nav: NavHostController) {
    val vm: SettingsViewModel = hiltViewModel()
    val activity = LocalContext.current
    // back from the system notification settings: the banner follows (code review A2-05: no S1 state here)
    var off by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { off = vm.notificationsOff(); onPauseOrDispose {} }
    val st by vm.state.collectAsStateWithLifecycle()
    var timeDialog by remember { mutableStateOf(false) }
    var emptyWarn by remember { mutableStateOf(false) }

    Scaffold(topBar = { AppTopBar("만료 알림", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (off)
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
            Text("하루에 한 번만, 그날 챙길 쿠폰을 모아 알려 드려요. 같은 상품 여러 장은 한 줄로 합쳐요.", Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // C2-08 AC3: the system settings of this app's "만료 알림" channel
            SettingRow(Icons.Rounded.MusicNote, "알림 소리 바꾸기") { activity.startActivity(vm.channelSettingsIntent()) }
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
    if (timeDialog) TimePickDialog(st.app.reminderMinute, { timeDialog = false }) { vm.setReminders(st.app.reminderOffsets, it); timeDialog = false }
}
