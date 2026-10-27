package com.ouor.mycoupon.ui.coupon

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.domain.UsageError
import com.ouor.mycoupon.domain.UsageInput
import com.ouor.mycoupon.domain.UsageInputException
import com.ouor.mycoupon.ui.component.AppSheet
import com.ouor.mycoupon.ui.component.PrimaryButton
import com.ouor.mycoupon.ui.component.SecondaryButton
import com.ouor.mycoupon.ui.component.ThousandsTransformation
import com.ouor.mycoupon.ui.component.won
import com.ouor.mycoupon.ui.theme.Spacing

/** S8 (amount only): spent (default) or remaining amount with live preview and range check. Exchange coupons are marked from the CTA (U5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UseSheet(c: Coupon, onDismiss: () -> Unit, onRemaining: (Long) -> Unit) {
    val bal = c.balance ?: c.faceValue ?: 0
    var text by remember { mutableStateOf("") }
    var spentMode by remember { mutableStateOf(true) } // matches [쓴 금액 입력]
    val n = text.toLongOrNull()
    // C1: the core's own rule decides; the sheet only words it
    val check = n?.let { v -> runCatching { if (spentMode) UsageInput.spent(bal, v) else UsageInput.remaining(bal, v) } }
    val remaining = check?.getOrNull()
    val refusal = check?.exceptionOrNull() as? UsageInputException
    // "잔액이 그대로예요" / "쓴 금액을 입력해 주세요" only disable the button; range errors are said out loud
    val error = refusal?.takeIf { it.error == UsageError.OVER_BALANCE || it.error == UsageError.NEGATIVE }?.let { usageMessage(it, bal, spentMode) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AppSheet(onDismiss, if (spentMode) "얼마 썼나요?" else "얼마 남았나요?", "지금 잔액 ${won(bal)}") {
        // review U4: the mode is a visible two-way choice, not a link whose meaning flips
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = Spacing.m)) {
            listOf(true to "쓴 금액", false to "남은 금액").forEachIndexed { i, (mode, label) ->
                SegmentedButton(spentMode == mode, { if (spentMode != mode) { spentMode = mode; text = "" } },
                    SegmentedButtonDefaults.itemShape(i, 2), Modifier.heightIn(min = Spacing.touch)) { Text(label) }
            }
        }
        OutlinedTextField(
            text, { v -> text = v.filter(Char::isDigit).take(9) }, Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text(if (spentMode) "쓴 금액" else "남은 금액") }, suffix = { Text("원") },
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), singleLine = true,
            isError = error != null, supportingText = error?.let { { Text(it, Modifier.semantics { error(it) }) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = ThousandsTransformation,
        )
        if (remaining != null) Text(
            if (spentMode) "남는 금액 ${won(bal - n)}" else "이번에 쓴 금액 ${won(bal - n)}",
            Modifier.padding(vertical = Spacing.s), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Spacing.s))
        PrimaryButton(if (remaining == 0L) "다 썼어요 · 사용 완료" else "기록하기", { remaining?.let(onRemaining) },
            enabled = remaining != null)
        SecondaryButton("취소", onDismiss)
    }
}

/** S8 copy per refusal (screens.md S8); unknown kinds fall back to the core's Korean message. */
fun usageMessage(e: UsageInputException, balance: Long, spentMode: Boolean): String = when (e.error) {
    UsageError.OVER_BALANCE -> if (spentMode) "남은 ${won(balance)}보다 많이 쓸 수 없어요" else "잔액 ${won(balance)}보다 클 수 없어요"
    UsageError.NO_CHANGE -> "잔액이 그대로예요. 쓴 금액이 있을 때 기록해 주세요"
    UsageError.SPENT_ZERO -> "쓴 금액을 입력해 주세요"
    UsageError.NEGATIVE -> "0원보다 적게 입력할 수 없어요"
    UsageError.NOT_AMOUNT -> e.message ?: "금액권만 잔액을 기록할 수 있어요"
}
