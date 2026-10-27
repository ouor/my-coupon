package com.ouor.mycoupon.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.ui.home.HomeViewModel
import com.ouor.mycoupon.ui.MainActivity
import com.ouor.mycoupon.ui.component.AppTopBar
import com.ouor.mycoupon.ui.component.InfoBanner
import com.ouor.mycoupon.ui.component.SectionTitle
import com.ouor.mycoupon.ui.coupon.OffsetChecks
import com.ouor.mycoupon.ui.home.HomeBanner
import com.ouor.mycoupon.ui.home.openNotificationSettings
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors

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
            if (homeState.banner == com.ouor.mycoupon.ui.home.HomeBanner.NOTIFICATIONS_OFF || !androidx.core.app.NotificationManagerCompat.from(activity).areNotificationsEnabled())
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
