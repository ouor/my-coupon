package com.ouor.mycoupon.ui.coupon

import com.ouor.mycoupon.ui.component.OffsetChecks
import com.ouor.mycoupon.ui.component.offsetsLabel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.ouor.mycoupon.data.AppSettings
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.ui.component.AppSheet
import com.ouor.mycoupon.ui.component.PrimaryButton
import com.ouor.mycoupon.ui.component.SecondaryButton
import com.ouor.mycoupon.ui.theme.Spacing

/** S12 (null = follow the app setting). */
@Composable
internal fun ReminderSheet(c: Coupon, defaults: List<Int>, onDismiss: () -> Unit, onSave: (List<Int>?) -> Unit) {
    var follow by remember { mutableStateOf(c.reminderOffsets == null) }
    var sel by remember { mutableStateOf(c.reminderOffsets ?: AppSettings().reminderOffsets) }
    AppSheet(onDismiss, "이 쿠폰 알림", if (c.expiryDate == null) "유효기간이 없어 알림을 보내지 않아요" else null) {
        val enabled = c.expiryDate != null
        ChoiceRow("기본 설정 따르기 (${offsetsLabel(defaults)})", follow, enabled) { follow = true }
        ChoiceRow("이 쿠폰만 따로 정하기", !follow, enabled) { follow = false }
        if (!follow) OffsetChecks(sel, enabled) { sel = it }
        Spacer(Modifier.height(Spacing.m))
        PrimaryButton("저장", { onSave(if (follow) null else sel) }, enabled = enabled && (follow || sel.isNotEmpty()))
        SecondaryButton("취소", onDismiss)
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).selectable(selected, enabled, Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null, enabled = enabled)
        Spacer(Modifier.width(Spacing.m))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
