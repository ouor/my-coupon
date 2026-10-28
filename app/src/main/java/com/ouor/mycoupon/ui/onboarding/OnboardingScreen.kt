package com.ouor.mycoupon.ui.onboarding

import com.ouor.mycoupon.ui.component.Promise
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ouor.mycoupon.ui.component.BottomCta
import com.ouor.mycoupon.ui.theme.Spacing

/** S0 (F13): no permission asks here. */
@Composable
fun OnboardingScreen(start: () -> Unit) {
    Scaffold(bottomBar = { BottomCta("시작하기", start, hint = "가입 없이 바로 써요") }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.xxl)) {
            Surface(Modifier.size(64.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.ConfirmationNumber, null, Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.height(Spacing.xl))
            Text("쿠폰, 이제 사진만 고르세요", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.xl))
            Promise(Icons.Rounded.PhonelinkLock, "사진은 이 폰 안에서만 읽어요", "어디에도 보내지 않아요. 인터넷을 쓰지 않는 앱이에요.")
            Promise(Icons.Rounded.NotificationsActive, "만료되기 전에 알려 드려요", "하루에 한 번, 시각은 바꿀 수 있어요")
            // C2-12: the checkout promises, said concretely
            Promise(Icons.Rounded.LightMode, "바코드 화면은 밝기 최대, 꺼지지 않아요", null)
            Promise(Icons.Rounded.ConfirmationNumber, "위젯·알림을 누르면 바로 바코드", null)
        }
    }
}
