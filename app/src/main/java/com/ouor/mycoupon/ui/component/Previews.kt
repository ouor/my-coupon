package com.ouor.mycoupon.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ouor.mycoupon.ui.theme.MyCouponTheme

// Code review U-26 / design-system §2.3-7: the pieces that broke at 200 % (U-11, U-20) seen without a device.

@Preview(name = "200 %", fontScale = 2f, widthDp = 360)
@Composable
private fun UndoBarLarge() = MyCouponTheme {
    UndoBar(Copy.USED, "아이스 카페 아메리카노 Tall 2잔 + 블루베리 머핀 세트", 7) {}
}

@Preview(name = "200 %", fontScale = 2f, widthDp = 360)
@Composable
private fun BannerAndCtaLarge() = MyCouponTheme {
    Column {
        InfoBanner(Icons.Rounded.TaskAlt, "아까 아메리카노 썼어요?", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface,
            sub = "스타벅스 · 잔액 12,400원 · …3456") { OutlinedButton({}) { Text("아니요") } }
        BottomCta("쓴 금액 입력", {}, hint = "확인 필요 쿠폰도 지금 등록하고 나중에 고칠 수 있어요")
    }
}
