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
import com.ouor.mycoupon.data.UsageKind
import com.ouor.mycoupon.data.UsageLog
import com.ouor.mycoupon.ui.component.epochDay
import com.ouor.mycoupon.ui.component.won
import com.ouor.mycoupon.ui.theme.Spacing

/** [onUndoLast] gets the id of the record shown on top (code review U-07: the core then removes only that one). */
@Composable
internal fun UsageHistory(usage: List<UsageLog>, onUndoLast: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium).padding(Spacing.m)) {
        usage.forEachIndexed { i, u ->
            val d = epochDay(u.at)
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                // ADJUST = balance typed in S5 (spent may be negative), not a purchase
                val what = if (u.kind == UsageKind.ADJUST) "잔액 수정" else "${u.spent?.let(::won) ?: "-"} 사용"
                Text("${d.monthValue}.${"%02d".format(d.dayOfMonth)} · $what · 잔액 ${u.balanceAfter?.let(::won) ?: "-"}",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                if (i == 0) TextButton({ onUndoLast(u.id) }) { Text("기록 지우기") }
            }
            if (i < usage.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
