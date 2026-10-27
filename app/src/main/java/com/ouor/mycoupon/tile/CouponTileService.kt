package com.ouor.mycoupon.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.ouor.mycoupon.ui.MainActivity

/**
 * T1 (F17). The activity decides S6 (one available coupon closest to expiry) or S1 (several), so the tile does
 * no database work. When locked, [unlockAndRun] asks for unlock first: no barcode on the lock screen (AC2).
 */
class CouponTileService : TileService() {
    override fun onClick() {
        if (isLocked) unlockAndRun { open() } else open()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_FROM_TILE, true)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
