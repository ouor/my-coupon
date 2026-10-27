package com.ouor.mycoupon.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.domain.ImportOrigin
import com.ouor.mycoupon.ui.theme.MyCouponTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Minimal navigation (no navigation library): a back stack of screens. The UI developer replaces the screens. */
sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Import : Screen
    /** S5 for an import draft */
    data class EditDraft(val key: Int) : Screen
    /** S5 for a stored coupon */
    data class EditCoupon(val id: Long) : Screen
    data class Detail(val id: Long, val fromReminder: Boolean = false) : Screen
    data object Settings : Screen
    data object Gallery : Screen
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: Settings
    private val importVm: ImportViewModel by viewModels()
    private val homeVm: HomeViewModel by viewModels()
    private val stack = mutableStateListOf<Screen>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        stack += if (settings.onboarded) Screen.Home else Screen.Onboarding
        if (savedInstanceState == null) handle(intent)
        setContent {
            val top = stack.last()
            MyCouponTheme(forceLight = top is Screen.Detail) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    BackHandler(stack.size > 1) { pop() }
                    AppScreens(top, ::push, ::pop, ::replaceAll)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        homeVm.refresh()
    }

    fun push(s: Screen) { stack += s }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish() }
    fun replaceAll(s: Screen) { stack.clear(); stack += s }

    private fun handle(intent: Intent?) {
        intent ?: return
        when {
            intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE -> {
                val uris = sharedUris(intent)
                if (uris.isEmpty()) {
                    Toast.makeText(this, "이미지만 가져올 수 있어요", Toast.LENGTH_SHORT).show()
                    return
                }
                settings.onboarded = true
                importVm.start(uris, ImportOrigin.SHARE)
                replaceAll(Screen.Home); push(Screen.Import)
            }
            intent.hasExtra(EXTRA_COUPON_ID) -> {
                replaceAll(Screen.Home)
                push(Screen.Detail(intent.getLongExtra(EXTRA_COUPON_ID, 0), intent.getBooleanExtra(EXTRA_FROM_REMINDER, false)))
            }
            intent.getBooleanExtra(EXTRA_FROM_TILE, false) -> lifecycleScope.launch {
                replaceAll(Screen.Home)
                homeVm.tileTarget()?.let { push(Screen.Detail(it)) }
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

    companion object {
        const val EXTRA_COUPON_ID = "coupon_id"
        const val EXTRA_FROM_REMINDER = "from_reminder"
        const val EXTRA_FROM_TILE = "from_tile"
    }
}
