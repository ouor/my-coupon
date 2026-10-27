package com.ouor.mycoupon.ui.onboarding

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ouor.mycoupon.ui.MainActivity
import com.ouor.mycoupon.ui.component.BottomCta
import com.ouor.mycoupon.ui.theme.Spacing

/** S0 (F13): no permission asks here. */
@Composable
fun OnboardingScreen(start: () -> Unit) {
    val activity = LocalActivity.current as MainActivity
    Scaffold(bottomBar = { BottomCta("시작하기", { activity.settings.onboarded = true; start() }, hint = "가입 없이 바로 써요") }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.xxl)) {
            Surface(Modifier.size(64.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.ConfirmationNumber, null, Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.height(Spacing.xl))
            Text("쿠폰, 이제 사진만 고르세요", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.xl))
            Promise(Icons.Rounded.PhonelinkLock, "사진은 이 폰 안에서만 읽어요", "어디에도 보내지 않아요. 인터넷을 쓰지 않는 앱이에요.")
            Promise(Icons.Rounded.NotificationsActive, "만료되기 전에 알려 드려요", null)
            Promise(Icons.Rounded.ConfirmationNumber, "계산대에서는 바코드를 크게 보여 드려요", null)
        }
    }
}

@Composable
fun Promise(icon: ImageVector, title: String, sub: String?) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = sub?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    )
}
