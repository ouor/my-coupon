package com.ouor.mycoupon.ui.coupon

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.ouor.mycoupon.ui.component.BarcodeRenderer
import androidx.compose.foundation.layout.Row
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ouor.mycoupon.ui.component.AuthCodeRow
import com.ouor.mycoupon.ui.component.BarcodeImage
import com.ouor.mycoupon.ui.component.BrightnessChip
import com.ouor.mycoupon.ui.component.CodeNumber
import com.ouor.mycoupon.ui.component.CodeView
import com.ouor.mycoupon.ui.component.codeView
import com.ouor.mycoupon.ui.component.name
import com.ouor.mycoupon.ui.theme.BarcodeColors
import com.ouor.mycoupon.ui.theme.CodeTypography
import com.ouor.mycoupon.ui.theme.MyCouponTheme
import com.ouor.mycoupon.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * S7 "점원에게 보여 주기" (C2-20, design-system §6.17): landscape, white, immersive. A tap anywhere does nothing (the phone is handed
 * to the clerk); it closes only with the system back or by holding [✕]. Brightness and keep-on stay (C2-01 AC2).
 */
@Composable
fun BarcodeFullScreen(id: Long, nav: NavHostController) = MyCouponTheme(forceLight = true) {
    val vm: CouponViewModel = hiltViewModel()
    LaunchedEffect(id) { vm.load(id) }
    val c by vm.coupon.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as Activity
    val bright = CheckoutWindow(on = true)
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
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) { if (hint) { delay(2_000); hint = false } }
    BoxWithConstraints(Modifier.fillMaxSize().background(BarcodeColors.background)) {
        // code review U-02: sized by the landscape height, so the whole code and its number fit the first screen
        val codeMax = maxHeight * (if (BarcodeRenderer.is2d(coupon.codeFormat)) 0.6f else 0.45f)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally) {
            // the clerk's check line, kept clear of the close button
            // round-2 V9: the brightness chip sits on this line, not alone in a corner
            Row(Modifier.fillMaxWidth().padding(end = 72.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull(coupon.brand, coupon.name()).joinToString(" "), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (bright) BrightnessChip(Modifier.padding(start = Spacing.s))
            }
            Spacer(Modifier.height(Spacing.s))
            if (coupon.codeView() != CodeView.PIN) BarcodeImage(coupon, maxHeight = codeMax)
            coupon.code?.let {
                Spacer(Modifier.height(Spacing.m))
                CodeNumber(it, if (coupon.codeView() == CodeView.PIN) CodeTypography.codePin else CodeTypography.codeLarge, Modifier.fillMaxWidth())
            }
            coupon.authCode?.let { AuthCodeRow(it, onCopy = null) }
            Spacer(Modifier.height(Spacing.xl))
        }
        // round-2 V9: how to close is always written under [✕]
        Column(Modifier.align(Alignment.TopEnd).padding(Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
            HoldToClose(Modifier, onClose = { nav.popBackStack() }, onShort = { hint = true })
            // code review U-29: TalkBack closes with a double tap, so this hint is for sight only
            Text("길게 눌러 닫기", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hint) Text("길게 누르면 닫혀요", Modifier.align(Alignment.BottomCenter).padding(Spacing.l)
            .background(MaterialTheme.colorScheme.inverseSurface, MaterialTheme.shapes.small).padding(horizontal = Spacing.m, vertical = Spacing.s),
            color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 56 dp [✕] that closes after a 600 ms hold, the ring filling meanwhile; a short press calls [onShort].
 * TalkBack's double tap uses the semantics click and closes at once (forcing a hold would be a barrier).
 */
@Composable
private fun HoldToClose(modifier: Modifier, onClose: () -> Unit, onShort: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(modifier.size(56.dp)
        .semantics { contentDescription = "닫기"; role = Role.Button; onClick { onClose(); true } }
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                val hold = scope.launch { progress.animateTo(1f, tween(600, easing = LinearEasing)); onClose() }
                val released = tryAwaitRelease()
                if (progress.value < 1f) {
                    hold.cancel()
                    scope.launch { progress.snapTo(0f) }
                    if (released) onShort()
                }
            })
        }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator({ progress.value }, Modifier.size(48.dp), trackColor = MaterialTheme.colorScheme.outlineVariant)
        Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurface)
    }
}
