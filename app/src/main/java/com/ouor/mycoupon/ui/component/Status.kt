package com.ouor.mycoupon.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.ui.theme.statusColors
import java.time.LocalDate

/** design-system §6.3: non-interactive status label, always icon + text (P4). */
@Composable
fun StatusChip(text: String, icon: ImageVector, fg: Color, container: Color, modifier: Modifier = Modifier) {
    // redesign: tinted face + strong text, 6 radius, 22dp — the only place colour fills a shape
    Surface(modifier.heightIn(min = 22.dp), shape = MaterialTheme.shapes.small, color = container, contentColor = fg) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, null, Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
        }
    }
}

/** Chips for the ended states and the S4 verdicts. */
enum class Chip { EXPIRED_DRAFT, EXPIRED, USED, GIVEN, NOT_COUPON, REGISTERED, ALREADY_USED, AVAILABLE }

@Composable
fun StatusChip(kind: Chip, modifier: Modifier = Modifier, suffix: String = "") {
    val s = MaterialTheme.statusColors
    val (text, icon, colors) = when (kind) {
        Chip.EXPIRED_DRAFT -> Triple("이미 만료됐어요", Ink.EventBusy, s.soon to s.soonContainer)
        Chip.EXPIRED -> Triple("만료", Ink.EventBusy, s.ended to s.endedContainer)
        Chip.USED -> Triple("사용 완료", Ink.CheckCircle, s.ended to s.endedContainer)
        Chip.GIVEN -> Triple("넘김", Ink.Send, s.ended to s.endedContainer)
        Chip.NOT_COUPON -> Triple("쿠폰이 아닌 것 같아요", Ink.Block, s.ended to s.endedContainer)
        Chip.REGISTERED -> Triple("이미 등록된 쿠폰", Ink.Copy, s.ended to s.endedContainer)
        Chip.ALREADY_USED -> Triple("이미 사용한 쿠폰이에요", Ink.Warning, s.soon to s.soonContainer)
        Chip.AVAILABLE -> Triple("사용 가능", Ink.CheckCircle, s.available to s.availableContainer)
    }
    StatusChip(text + suffix, icon, colors.first, colors.second, modifier)
}

/** "확인 필요" mark (small form for cards, also under S5 fields). */
@Composable
fun ReviewMark(modifier: Modifier = Modifier, text: String = "확인 필요") {
    val s = MaterialTheme.statusColors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Ink.Alert, null, Modifier.size(16.dp), tint = s.review)
        Text(text, style = MaterialTheme.typography.labelMedium, color = s.review)
    }
}

/** TalkBack sentence for the D-day (design-system §9, C2-05: never "디 마이너스 3"; the date instead of "곧 만료"). */
fun ddaySpoken(c: Coupon, today: LocalDate): String = when (c.state(today)) {
    CouponState.AVAILABLE, CouponState.SOON -> "${Stats.daysLeft(c, today)}일 남음, ${c.expiryDate!!.monthDay()}까지"
    CouponState.TODAY -> "오늘까지"
    CouponState.NO_EXPIRY -> "유효기간 없음"
    CouponState.EXPIRED -> "기간 지남"
    CouponState.USED -> "사용 완료"
    CouponState.GIVEN -> "넘김"
}

/** design-system §6.2. [large] = S6 (displaySmall). */
@Composable
fun DdayBadge(c: Coupon, today: LocalDate, modifier: Modifier = Modifier, large: Boolean = false) {
    val s = MaterialTheme.statusColors
    val m = modifier.clearAndSetSemantics { contentDescription = ddaySpoken(c, today) }
    val big: TextStyle = (if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge)
        .copy(fontFeatureSettings = "tnum")
    when (c.state(today)) {
        // redesign: on S6 the D-day is a status chip beside the date (red with a clock inside 7 days, grey otherwise)
        CouponState.AVAILABLE -> if (large) Text("D-${Stats.daysLeft(c, today)}", m, style = big)
            else StatusChip("D-${Stats.daysLeft(c, today)}", Ink.Calendar, MaterialTheme.colorScheme.onSurface, s.track, m)
        CouponState.SOON -> if (large) Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Ink.Clock, null, Modifier.size(28.dp), tint = s.soon)
            Text("D-${Stats.daysLeft(c, today)}", style = big, color = s.soon)
        } else StatusChip("D-${Stats.daysLeft(c, today)}", Ink.Clock, s.soon, s.soonContainer, m)
        CouponState.TODAY -> Surface(m, shape = MaterialTheme.shapes.small, color = s.soon, contentColor = MaterialTheme.colorScheme.surface) {
            Text("오늘까지", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge)
        }
        CouponState.NO_EXPIRY -> Text("기간 없음", m, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CouponState.EXPIRED -> StatusChip(Chip.EXPIRED, m)
        CouponState.USED -> StatusChip(Chip.USED, m)
        CouponState.GIVEN -> StatusChip(Chip.GIVEN, m)
    }
}
