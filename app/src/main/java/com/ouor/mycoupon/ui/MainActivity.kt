package com.ouor.mycoupon.ui

import androidx.glance.appwidget.updateAll
import com.ouor.mycoupon.widget.CouponWidget
import com.ouor.mycoupon.ui.home.HomeViewModel
import com.ouor.mycoupon.ui.importing.ImportViewModel
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ouor.mycoupon.AppIntents
import com.ouor.mycoupon.AppIntents.EXTRA_COUPON_ID
import com.ouor.mycoupon.AppIntents.EXTRA_FROM_REMINDER
import com.ouor.mycoupon.AppIntents.EXTRA_FROM_TILE
import com.ouor.mycoupon.AppIntents.EXTRA_REMINDER_IDS
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.domain.ImportOrigin
import com.ouor.mycoupon.ui.theme.MyCouponTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: Settings
    private val importVm: ImportViewModel by viewModels()
    private val homeVm: HomeViewModel by viewModels()
    /** entry intent (share / notification / widget / tile) waiting for the NavController */
    private var pending by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pending = intent
        val start: Any = if (settings.onboarded) Route.Home else Route.Onboarding
        setContent {
            val nav = rememberNavController()
            MyCouponTheme {
                AppNavHost(nav, start)
            }
            LaunchedEffect(pending) { pending?.let { handle(it, nav); pending = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pending = intent
    }

    override fun onResume() {
        super.onResume()
        homeVm.refresh()
        // QA C2Q-07: the font size changed while the process was not watching (or the widget was drawn before): redraw it
        if (CouponWidget.fontScale(this) != CouponWidget.drawnScale) lifecycleScope.launch { runCatching { CouponWidget().updateAll(applicationContext) } }
    }

    private fun handle(intent: Intent, nav: NavHostController) {
        // C2-10: that day's N1 was tapped, so the S1 "오늘·내일 끝나는 쿠폰" banner stays hidden today
        if (AppIntents.isInternal(intent) && intent.getBooleanExtra(EXTRA_FROM_REMINDER, false)) settings.hideExpiringBanner()
        when {
            intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE -> {
                val uris = sharedUris(intent)
                if (uris.isEmpty()) {
                    Toast.makeText(this, "이미지만 가져올 수 있어요", Toast.LENGTH_SHORT).show()
                    return
                }
                settings.onboarded = true
                importVm.start(uris, ImportOrigin.SHARE)
                nav.navigate(Route.Home) { popUpTo(0) }
                nav.navigate(Route.Import)
            }
            // S-08: internal extras only from the app's own non-exported alias, never from a launcher or another app
            !AppIntents.isInternal(intent) -> Unit
            intent.hasExtra(EXTRA_COUPON_ID) -> {
                settings.onboarded = true
                nav.navigate(Route.Home) { popUpTo(0) }
                nav.navigate(Route.Detail(intent.getLongExtra(EXTRA_COUPON_ID, 0), intent.getBooleanExtra(EXTRA_FROM_REMINDER, false)))
            }
            intent.getBooleanExtra(EXTRA_FROM_REMINDER, false) -> { // N1 with several coupons → S1
                if (!settings.onboarded) return
                intent.getLongArrayExtra(EXTRA_REMINDER_IDS)?.let(homeVm::openedFromReminder)
                nav.navigate(Route.Home) { popUpTo(0) }
            }
            intent.getBooleanExtra(EXTRA_FROM_TILE, false) -> lifecycleScope.launch {
                if (!settings.onboarded) return@launch
                nav.navigate(Route.Home) { popUpTo(0) }
                homeVm.tileTarget()?.let { nav.navigate(Route.Detail(it)) }
            }
        }
    }

    private fun sharedUris(intent: Intent): List<Uri> {
        val list: List<Uri?> = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        } else {
            listOf(if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM))
        }
        return list.filterNotNull().filter { u -> contentResolver.getType(u)?.startsWith("image/") ?: true }
    }
}
