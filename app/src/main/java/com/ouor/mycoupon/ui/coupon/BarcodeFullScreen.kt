package com.ouor.mycoupon.ui.coupon

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.ui.component.BarcodeImage
import com.ouor.mycoupon.ui.component.CodeNumber
import com.ouor.mycoupon.ui.component.CodeView
import com.ouor.mycoupon.ui.component.codeView
import com.ouor.mycoupon.ui.theme.BarcodeColors
import com.ouor.mycoupon.ui.theme.CodeTypography
import com.ouor.mycoupon.ui.theme.MyCouponTheme
import com.ouor.mycoupon.ui.theme.Spacing

/** S7: landscape, white, immersive; tap anywhere to close. */
@Composable
fun BarcodeFullScreen(id: Long, nav: NavHostController) = MyCouponTheme(forceLight = true) {
    val vm: CouponViewModel = hiltViewModel()
    LaunchedEffect(id) { vm.load(id) }
    val c by vm.coupon.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as Activity
    CheckoutWindow(on = true)
    DisposableEffect(Unit) {
        val before = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            ctl.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = before
        }
    }
    val coupon = c ?: return@MyCouponTheme
    Box(
        Modifier.fillMaxSize().background(BarcodeColors.background)
            .clickable(onClickLabel = "닫기") { nav.popBackStack() }
            .semantics(mergeDescendants = true) { contentDescription = "바코드 크게 보기, 두 번 탭하면 닫기"; onClick("닫기") { nav.popBackStack(); true } },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(horizontal = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            if (coupon.codeView() != CodeView.PIN) BarcodeImage(coupon)
            coupon.code?.let {
                Spacer(Modifier.height(Spacing.m))
                CodeNumber(it, if (coupon.codeView() == CodeView.PIN) CodeTypography.codePin else CodeTypography.codeLarge, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.m))
            Text("아무 곳이나 누르면 닫혀요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
