package com.ouor.mycoupon.ui.component

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.ouor.mycoupon.data.AppSettings
import com.ouor.mycoupon.ui.theme.Spacing

// Pieces several feature screens use (code review A2-05: features share only ui.component, never each other).

fun openNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

fun timeLabel(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = (h % 12).let { if (it == 0) 12 else it }
    return "$ampm $h12:${"%02d".format(m)}"
}

fun offsetLabel(d: Int) = if (d == 0) "당일" else "${d}일 전"

fun offsetsLabel(list: List<Int>) = if (list.isEmpty()) "알림 없음" else list.sortedDescending().joinToString(" · ", transform = ::offsetLabel)

/** S11/S12 offsets: vertical rows, never chips in a row (200 % font). Several can be on: toggleable (code review U-15). */
@Composable
fun OffsetChecks(sel: List<Int>, enabled: Boolean = true, onChange: (List<Int>) -> Unit) {
    AppSettings.OFFSET_CHOICES.forEach { d ->
        Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch)
            .toggleable(d in sel, enabled, Role.Checkbox) { onChange(if (d in sel) sel - d else sel + d) }.padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(d in sel, null, enabled = enabled)
            Spacer(Modifier.width(Spacing.m))
            Text(offsetLabel(d), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Reminder time picker, shared by S11 and the first-registration sheet (C2-08).
 * Code review U-12: at 150 %+ the dial does not fit, so the number-entry form is used, and the body scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(minute: Int, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    val state = rememberTimePickerState(minute / 60, minute % 60, is24Hour = false)
    val large = isLargeFont()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("알림 시각") },
        text = { Row(Modifier.verticalScroll(rememberScrollState())) { if (large) TimeInput(state) else TimePicker(state) } },
        confirmButton = { TextButton({ onOk(state.hour * 60 + state.minute) }) { Text("확인") } },
        dismissButton = { TextButton(onDismiss) { Text("취소") } },
    )
}

/** S0 / S9 promise line. */
@Composable
fun Promise(icon: ImageVector, title: String, sub: String?) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = sub?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    )
}
