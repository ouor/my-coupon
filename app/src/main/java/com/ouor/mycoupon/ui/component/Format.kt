package com.ouor.mycoupon.ui.component

import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.reminder.ReminderPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** design-system §1.2: what a coupon looks like right now. */
enum class CouponState { AVAILABLE, SOON, TODAY, NO_EXPIRY, EXPIRED, USED, GIVEN }

fun Coupon.state(today: LocalDate): CouponState {
    if (status == CouponStatus.USED) return if (usedReason == UsedReason.GIVEN) CouponState.GIVEN else CouponState.USED
    val d = Stats.daysLeft(this, today) ?: return CouponState.NO_EXPIRY
    return when {
        d < 0 -> CouponState.EXPIRED
        d == 0L -> CouponState.TODAY
        d <= 7 -> CouponState.SOON
        else -> CouponState.AVAILABLE
    }
}

private val dotDate = DateTimeFormatter.ofPattern("yyyy.MM.dd")

/** §7: "2026.11.03(화)" */
fun LocalDate.dotted(): String = format(dotDate) + "(" + dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")"

/** TalkBack: "2026년 11월 3일" */
fun LocalDate.spoken(): String = "${year}년 ${monthValue}월 ${dayOfMonth}일"

/** "11월 2일" */
fun LocalDate.monthDay(): String = "${monthValue}월 ${dayOfMonth}일"

fun epochDay(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun won(v: Long): String = ReminderPlanner.won(v)

/** "1234 5678 9012 3456" groups; TalkBack reads "1234, 5678, ..." (F6 AC5). */
fun codeGroups(code: String): List<String> = code.filterNot(Char::isWhitespace).chunked(4)
fun codeSpoken(code: String): String = codeGroups(code).joinToString(", ")

fun Coupon.title(): String = productName ?: brand ?: "이름 없는 쿠폰"

/** S5 copy per reason (screens.md S5): the first reason of a field is shown. */
fun reasonText(field: String, reason: String): String = when (reason) {
    "YEAR_INFERRED" -> "사진에 연도가 없어 추정했어요"
    "MULTIPLE_CANDIDATES" -> if (field == "faceValue") "금액이 여러 개 있어요. 맞는 금액인지 봐 주세요" else "날짜가 여러 개 있어요. 맞는 날짜를 골라 주세요"
    "AMBIGUOUS" -> "비슷한 상품 이름이 더 있어요. 맞는지 봐 주세요"
    "NO_KEYWORD" -> "유효기간 글자를 찾지 못해 추정했어요"
    "LOW_CONFIDENCE" -> "글자가 흐려 잘못 읽었을 수 있어요"
    "NOT_FOUND" -> when (field) {
        "expiryDate" -> "날짜를 찾지 못했어요. 직접 골라 주세요"
        "code" -> "번호를 찾지 못했어요. 직접 입력해 주세요"
        "productName" -> "상품 이름을 찾지 못했어요"
        "brand" -> "브랜드를 찾지 못했어요"
        "faceValue" -> "금액을 찾지 못했어요. 직접 입력해 주세요"
        else -> "사진과 같은지 확인해 주세요"
    }
    "CODE_MISMATCH" -> "바코드와 숫자가 달라요. 사진과 비교해 주세요"
    "CODE_NOT_DECODED" -> "바코드를 못 읽어 숫자로 가져왔어요. 사진과 같은지 봐 주세요"
    "ALWAYS_CHECK" -> if (field == "sender") "사진에서 보낸 사람을 가져왔어요. 맞는지 봐 주세요" else "사진과 같은지 확인해 주세요"
    else -> "사진과 같은지 확인해 주세요"
}

/** porting-spec order: the reason whose copy is shown first for a field. */
private val reasonOrder = listOf("NOT_FOUND", "MULTIPLE_CANDIDATES", "NO_KEYWORD", "AMBIGUOUS", "CODE_MISMATCH", "CODE_NOT_DECODED", "YEAR_INFERRED", "LOW_CONFIDENCE", "ALWAYS_CHECK")
fun firstReason(reasons: List<String>): String = reasons.minByOrNull { reasonOrder.indexOf(it).let { i -> if (i < 0) 99 else i } } ?: ""

/** S5 field labels for the review summary and TalkBack. */
val fieldLabel = mapOf(
    "brand" to "브랜드", "productName" to "상품명", "faceValue" to "금액", "code" to "번호",
    "expiryDate" to "유효기간", "issuer" to "발급처", "sender" to "메모",
)
