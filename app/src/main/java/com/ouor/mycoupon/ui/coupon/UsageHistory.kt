package com.ouor.mycoupon.ui.coupon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ouor.mycoupon.data.UsageLog
import com.ouor.mycoupon.ui.component.epochDay
import com.ouor.mycoupon.ui.component.won
import com.ouor.mycoupon.ui.theme.Spacing

@Composable
internal fun UsageHistory(usage: List<UsageLog>, onUndoLast: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium).padding(Spacing.m)) {
        usage.forEachIndexed { i, u ->
            val d = epochDay(u.at)
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${d.monthValue}.${"%02d".format(d.dayOfMonth)} · ${u.spent?.let(::won) ?: "-"} 사용 · 잔액 ${u.balanceAfter?.let(::won) ?: "-"}",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                if (i == 0) TextButton(onUndoLast) { Text("기록 지우기") }
            }
            if (i < usage.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
