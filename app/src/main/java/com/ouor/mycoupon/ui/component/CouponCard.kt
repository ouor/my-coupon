package com.ouor.mycoupon.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.ui.theme.Spacing
import java.time.LocalDate

/**
 * design-system §6.1 (S1, S13, search). The whole card is one tap target and one TalkBack node:
 * "스타벅스, 아메리카노 T, 3일 남음, 곧 만료, 확인 필요". Past coupons get [onUndo] (사용 완료·넘김 only).
 */
@Composable
fun CouponCard(c: Coupon, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier, onUndo: (() -> Unit)? = null, codeTail: Boolean = false) {
    val state = c.state(today)
    val past = state == CouponState.EXPIRED || state == CouponState.USED || state == CouponState.GIVEN
    val large = isLargeFont()
    val review = c.needsCheck && !past
    val balance = if (c.type == CouponType.AMOUNT && c.balance != null && !past) "잔액 ${won(c.balance)}" else null
    // C2-25: "10월 27일 넘김 · 김○○"
    val usedLine = c.usedAt?.takeIf { past }?.let { epochDay(it).monthDay() + if (state == CouponState.GIVEN) " 넘김" + (c.givenTo?.let { n -> " · $n" } ?: "") else " 사용" }
    val tail = c.code?.takeIf { codeTail }?.let(::codeTail)
    // C2-05 / design-system §9: "사용 완료, 10월 27일, 브랜드, 상품명" for past ones, otherwise "브랜드, 상품명, 4일 남음, 11월 1일까지, 번호 끝 2416"
    val pastWhen = if (state == CouponState.EXPIRED) c.expiryDate?.let { it.monthDay() + "까지" } else c.usedAt?.let { epochDay(it).monthDay() }
    val spoken = (if (past) listOfNotNull(ddaySpoken(c, today), pastWhen) else emptyList())
        .plus(listOfNotNull(c.brand, c.name() ?: "이름 없는 쿠폰", ddaySpoken(c, today).takeIf { !past }, c.givenTo?.takeIf { state == CouponState.GIVEN },
            c.code?.let(::codeTail), balance, "확인 필요".takeIf { review }))
        .joinToString(", ")
    val container = if (past) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer

    Card(
        onClick, modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = spoken
            role = Role.Button
            onClick("바코드 열기") { onClick(); true }
            if (onUndo != null) customActions = listOf(CustomAccessibilityAction("되돌리기") { onUndo(); true })
        },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Row(Modifier.padding(Spacing.l).heightIn(min = 48.dp), verticalAlignment = Alignment.Top) {
            if (!large) {
                CouponThumb(c.thumbPath, alpha = if (past) 0.6f else 1f)
                androidx.compose.foundation.layout.Spacer(Modifier.width(Spacing.m))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                c.brand?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(c.name() ?: "이름 없는 쿠폰", style = MaterialTheme.typography.titleMedium)
                if (large) DdayBadge(c, today, Modifier.padding(top = Spacing.xs))
                val sub = listOfNotNull(balance, usedLine, tail).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (review) ReviewMark(Modifier.padding(top = 2.dp))
            }
            if (!large) Column(horizontalAlignment = Alignment.End) {
                DdayBadge(c, today, Modifier.padding(start = Spacing.s))
                // C2-27: a bordered button, so it never looks like the (non-interactive) status chip above it
                if (onUndo != null) OutlinedButton(onUndo, Modifier.padding(top = Spacing.xs).heightIn(min = Spacing.touch)) { Text("되돌리기") }
            }
        }
        if (large && onUndo != null) OutlinedButton(onUndo, Modifier.padding(start = Spacing.l, bottom = Spacing.m).heightIn(min = Spacing.touch)) { Text("되돌리기") }
    }
}
