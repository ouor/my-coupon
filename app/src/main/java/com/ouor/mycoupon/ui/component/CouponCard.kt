package com.ouor.mycoupon.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.ui.theme.DdayStyle
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors
import java.time.LocalDate

/**
 * design-system §6.1 `CouponRow` (S1, S13, search): one row of a group — D-day column first (redesign principle 1),
 * brand / name / one value line, 40dp calm thumbnail last. The whole row is one tap target and one TalkBack node:
 * "스타벅스, 아메리카노 T, 3일 남음, 11월 1일까지, 번호 끝 2416, 확인 필요". Past rows get [onUndo] (사용 완료·넘김 only).
 * [first]/[last] place it inside its group (rounded ends, hairline between rows).
 */
@Composable
fun CouponRow(c: Coupon, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier, onUndo: (() -> Unit)? = null,
              first: Boolean = true, last: Boolean = true) {
    val state = c.state(today)
    val past = state == CouponState.EXPIRED || state == CouponState.USED || state == CouponState.GIVEN
    val large = isLargeFont()
    val review = c.needsCheck && !past
    val st = MaterialTheme.statusColors
    val balance = if (c.type == CouponType.AMOUNT && c.balance != null && !past) "잔액 ${won(c.balance)}" else null
    // C2-25: "10월 27일 넘김 · 김○○"
    val usedLine = c.usedAt?.takeIf { past }?.let { epochDay(it).monthDay() + if (state == CouponState.GIVEN) " 넘김" + (c.givenTo?.let { n -> " · $n" } ?: "") else " 사용" }
    // C2-05 / design-system §9: the tail is spoken only (owner feedback 11-04 took it off the screen)
    val pastWhen = if (state == CouponState.EXPIRED) c.expiryDate?.let { it.monthDay() + "까지" } else c.usedAt?.let { epochDay(it).monthDay() }
    val spoken = (if (past) listOfNotNull(ddaySpoken(c, today), pastWhen) else emptyList())
        .plus(listOfNotNull(c.brand, c.name() ?: "이름 없는 쿠폰", ddaySpoken(c, today).takeIf { !past }, c.givenTo?.takeIf { state == CouponState.GIVEN },
            c.code?.let(::codeTail), balance, "확인 필요".takeIf { review }))
        .joinToString(", ")
    val sub = listOfNotNull(balance, usedLine, "유효기간 확인 필요".takeIf { review && c.expiryDate == null }).joinToString(" · ")

    Row(
        modifier.fillMaxWidth()
            .groupItem(first, last, MaterialTheme.colorScheme.surface, st.border, st.line)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick("바코드 열기") { onClick(); true }
                if (onUndo != null) customActions = listOf(CustomAccessibilityAction("되돌리기") { onUndo(); true })
            }
            .clickable(onClick = onClick)
            .heightIn(min = 66.dp).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // §2.3-3: at 150 %+ the D-day sits on its own line above the name instead of a squeezed column
            if (large) DdayCell(c, today, review, Modifier.padding(bottom = Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!large) DdayCell(c, today, review, Modifier.widthIn(min = 58.dp).padding(end = Spacing.m))
                Column(Modifier.weight(1f)) {
                    c.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    // designer 11-04: one line at 100 % so a screen holds more rows; bigger fonts wrap
                    val scale = LocalDensity.current.fontScale
                    Text(c.listName() ?: "이름 없는 쿠폰", style = MaterialTheme.typography.titleMedium,
                        color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = if (large) Int.MAX_VALUE else if (scale > 1.05f) 2 else 1, overflow = TextOverflow.Ellipsis)
                    if (sub.isNotEmpty()) Text(sub, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (large && onUndo != null) InkTextButton("되돌리기", onUndo, Modifier.padding(top = Spacing.xs))
        }
        if (!large) {
            // C2-27: [되돌리기] is a text button, never the status chip's look
            if (onUndo != null) InkTextButton("되돌리기", onUndo, Modifier.padding(start = Spacing.s))
            else { androidx.compose.foundation.layout.Spacer(Modifier.width(Spacing.m)); CouponThumb(c.thumbPath, past = past, alpha = if (past) 0.7f else 1f) }
        }
    }
}

/** Left column of a row: "D-12 / 11.16", red with a clock inside 7 days, "! 확인" chip for review, grey chip for past ones. */
@Composable
private fun DdayCell(c: Coupon, today: LocalDate, review: Boolean, modifier: Modifier = Modifier) {
    val st = MaterialTheme.statusColors
    val date = c.expiryDate?.let { "%02d.%02d".format(it.monthValue, it.dayOfMonth) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        val state = c.state(today)
        when {
            state == CouponState.EXPIRED -> StatusChip(Chip.EXPIRED)
            state == CouponState.USED -> StatusChip(Chip.USED)
            state == CouponState.GIVEN -> StatusChip(Chip.GIVEN)
            review -> StatusChip("확인", Ink.Alert, st.review, st.reviewContainer)
            state == CouponState.TODAY -> Text("오늘", style = DdayStyle, color = st.soon)
            // owner 11-04: no clock — red text only, so every D-day, date and chip starts on the column's left edge
            state == CouponState.SOON -> Text("D-${Stats.daysLeft(c, today)}", style = DdayStyle, color = st.soon)
            state == CouponState.NO_EXPIRY -> Text("기간 없음", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text("D-${Stats.daysLeft(c, today)}", style = DdayStyle)
        }
        if (date != null && state != CouponState.USED && state != CouponState.GIVEN)
            Text(date, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = st.faint)
    }
}
