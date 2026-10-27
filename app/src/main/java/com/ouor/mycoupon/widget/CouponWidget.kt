package com.ouor.mycoupon.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponChangeListener
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.di.AppScope
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.ui.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** F11 data: the 3 available coupons closest to expiry (no-expiry ones last). */
object WidgetData {
    fun top(all: List<Coupon>, today: LocalDate, n: Int = 3): List<Coupon> = all
        .filter { it.isAvailable(today) }
        .sortedWith(compareBy<Coupon> { it.expiryDate == null }.thenBy { it.expiryDate })
        .take(n)

    fun dday(c: Coupon, today: LocalDate): String = when (val d = Stats.daysLeft(c, today)) {
        null -> "기간 없음"
        0L -> "오늘"
        else -> "D-$d"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): CouponRepository
}

/** W1. Tap a row → S6, tap the empty state → S1. */
class CouponWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val today = LocalDate.now()
        val top = WidgetData.top(repo.all(), today)
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(ColorProvider(androidx.compose.ui.graphics.Color.White)).padding(12.dp)) {
                if (top.isEmpty()) {
                    Text("쿠폰을 추가해 보세요", GlanceModifier.clickable(actionStartActivity(Intent(context, MainActivity::class.java))))
                }
                top.forEach { c ->
                    val open = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_COUPON_ID, c.id)
                        .setAction("open-${c.id}") // distinct intents per row
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(actionStartActivity(open))) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text(c.brand ?: "", style = TextStyle(fontSize = 12.sp))
                            Text(c.productName ?: "쿠폰", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        }
                        Text(WidgetData.dday(c, today), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.height(2.dp))
                }
            }
        }
    }
}

class CouponWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CouponWidget()
}

/** Refreshes W1 after any coupon change (F11 AC1); the daily reminder job also refreshes it for the D-day (AC2). */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val scope: CoroutineScope,
) : CouponChangeListener {
    override fun onCouponsChanged() {
        scope.launch { runCatching { CouponWidget().updateAll(context) } }
    }
}
