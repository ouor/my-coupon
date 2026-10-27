package com.ouor.mycoupon.tile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.ouor.mycoupon.AppIntents
import com.ouor.mycoupon.widget.WidgetData
import com.ouor.mycoupon.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T1 (F17). The activity decides S6 (one available coupon closest to expiry) or S1 (several).
 * When locked, [unlockAndRun] asks for unlock first: no barcode on the lock screen (AC2).
 * The subtitle names the most urgent coupon ("D-3 스타벅스", QA R1-13; D-day only while locked, S-13); it shows no number.
 */
class CouponTileService : TileService() {
    private val scope = MainScope()

    override fun onStartListening() {
        if (Build.VERSION.SDK_INT < 29) return
        scope.launch {
            val repo = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java).repository()
            val today = LocalDate.now()
            val top = WidgetData.top(repo.all(), today, 1).firstOrNull()
            val tile = qsTile ?: return@launch
            // security review S-13: on the lock screen the D-day only, no brand or product name
            tile.subtitle = top?.let { listOfNotNull(WidgetData.dday(it, today), if (isLocked) null else it.brand ?: it.productName).joinToString(" ") }
                ?.ifEmpty { null } ?: "쿠폰 열기"
            tile.updateTile()
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    override fun onClick() {
        if (isLocked) unlockAndRun { open() } else open()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open() {
        val intent = AppIntents.tile(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(AppIntents.pending(this, AppIntents.REQUEST_TILE, intent))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
