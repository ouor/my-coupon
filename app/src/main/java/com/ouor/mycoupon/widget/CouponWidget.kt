package com.ouor.mycoupon.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
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
        val open = Intent(context, MainActivity::class.java)
        val initial = repo.all()
        provideContent {
            // collected inside the session: updateAll() on a live session only recomposes, it does not re-run provideGlance (QA R1-02)
            val all by repo.observeAll().collectAsState(initial)
            val today = LocalDate.now()
            val top = WidgetData.top(all, today)
            val more = all.count { it.isAvailable(today) } - top.size
            // W1 (screens.md): system day/night colors from the design tokens; never a barcode or number here
            val bg = ColorProvider(day = Color.White, night = Color(0xFF121417))
            val fg = ColorProvider(day = Color(0xFF191F28), night = Color(0xFFE9ECF1))
            val sub = ColorProvider(day = Color(0xFF4E5968), night = Color(0xFFAEB6C2))
            val soon = ColorProvider(day = Color(0xFFC62828), night = Color(0xFFFF9C94))
            Column(GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp).background(bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("내 쿠폰", GlanceModifier.clickable(actionStartActivity(open)), style = TextStyle(fontSize = 12.sp, color = sub, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(4.dp))
                if (top.isEmpty()) {
                    Text("쿠폰을 추가해 보세요", GlanceModifier.fillMaxWidth().padding(vertical = 12.dp).clickable(actionStartActivity(open)),
                        style = TextStyle(fontSize = 15.sp, color = fg))
                }
                top.forEach { c ->
                    val row = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_COUPON_ID, c.id)
                        .setAction("open-${c.id}") // distinct intents per row
                    val d = Stats.daysLeft(c, today)
                    Row(GlanceModifier.fillMaxWidth().height(44.dp).clickable(actionStartActivity(row)), verticalAlignment = Alignment.CenterVertically) {
                        Text(listOfNotNull(c.brand, c.productName).joinToString(" ").ifEmpty { "쿠폰" }, GlanceModifier.defaultWeight(),
                            style = TextStyle(fontSize = 14.sp, color = fg), maxLines = 1)
                        Text(WidgetData.dday(c, today), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (d != null && d <= 7) soon else fg))
                    }
                }
                if (more > 0) Text("외 ${more}개", GlanceModifier.clickable(actionStartActivity(open)), style = TextStyle(fontSize = 12.sp, color = sub))
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
