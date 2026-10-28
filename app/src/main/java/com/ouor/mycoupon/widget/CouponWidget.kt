package com.ouor.mycoupon.widget

import android.database.ContentObserver
import android.provider.Settings
import com.ouor.mycoupon.ui.theme.DarkColors
import com.ouor.mycoupon.ui.theme.DarkStatus
import com.ouor.mycoupon.ui.theme.LightColors
import com.ouor.mycoupon.ui.theme.LightStatus
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.ouor.mycoupon.ui.component.ddaySpoken
import com.ouor.mycoupon.data.CouponType
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import android.content.Context
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
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponChangeListener
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.di.AppScope
import com.ouor.mycoupon.domain.CouponText
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.AppIntents
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

    fun dday(c: Coupon, today: LocalDate): String = CouponText.dday(c, today)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): CouponRepository
}

/** W1. Tap a row → S6, tap the empty state → S1. */
class CouponWidget : GlanceAppWidget() {
    // review U14: as many rows as the widget is tall, no empty lower half
    override val sizeMode = SizeMode.Exact

    companion object {
        /** QA C2Q-07: the system value now; a long-lived process's resources keep the old scale */
        fun fontScale(context: Context) = Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE, 1f)
        /** the scale the widget was last drawn with in this process (0 = not yet) */
        @Volatile var drawnScale = 0f
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val open = AppIntents.home(context)
        val initial = repo.all()
        provideContent {
            // collected inside the session: updateAll() on a live session only recomposes, it does not re-run provideGlance (QA R1-02)
            val all by repo.observeAll().collectAsState(initial)
            val today = LocalDate.now()
            // C2Q-01: rows grow with the system font size (200 %: two-line rows overlapped at a fixed 52 dp), so fewer fit
            val fs = fontScale(context).also { drawnScale = it }.coerceAtLeast(1f)
            // round-2 V2: a two-line row needs about 26 dp more per +100 % (52 × scale left 2/3 of the widget empty)
            val rowH = 52f + 26f * (fs - 1f)
            val rows = ((LocalSize.current.height.value - 20f - 24f * fs) / rowH).toInt().coerceIn(1, 6)
            val top = WidgetData.top(all, today, rows)
            val more = all.count { it.isAvailable(today) } - top.size
            // W1 (screens.md): system day/night colors from the design tokens; never a barcode or number here
            // code review U-25: the app theme's own values, not copies
            val bg = ColorProvider(day = LightColors.surface, night = DarkColors.surface)
            val fg = ColorProvider(day = LightColors.onSurface, night = DarkColors.onSurface)
            val sub = ColorProvider(day = LightColors.onSurfaceVariant, night = DarkColors.onSurfaceVariant)
            val soon = ColorProvider(day = LightStatus.soon, night = DarkStatus.soon)
            Column(GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp).background(bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open))) {
                    Text("내 쿠폰", GlanceModifier.defaultWeight(), style = TextStyle(fontSize = 13.sp, color = sub, fontWeight = FontWeight.Medium))
                    if (more > 0) Text("외 ${more}개", style = TextStyle(fontSize = 13.sp, color = sub))
                }
                Spacer(GlanceModifier.height(4.dp))
                if (top.isEmpty()) {
                    Text("쿠폰을 추가해 보세요", GlanceModifier.fillMaxWidth().padding(vertical = 12.dp).clickable(actionStartActivity(open)),
                        style = TextStyle(fontSize = 15.sp, color = fg))
                }
                top.forEach { c ->
                    val row = AppIntents.coupon(context, c.id)
                    val d = Stats.daysLeft(c, today)
                    // review U14: name on its own larger line, brand (+ balance) under it; U1 short name
                    val name = CouponText.shortName(c.brand, c.productName) ?: c.brand ?: "쿠폰"
                    val line2 = listOfNotNull(c.brand?.takeIf { it != name },
                        c.balance?.takeIf { c.type == CouponType.AMOUNT }?.let { "잔액 ${CouponText.won(it)}" }).joinToString(" · ")
                    // C2-05: one sentence like the list card, not "D 마이너스 3"
                    val spoken = listOf(name, line2, ddaySpoken(c, today), "누르면 바코드").filter { it.isNotEmpty() }.joinToString(", ")
                    Row(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionStartActivity(row)).semantics { contentDescription = spoken },
                        verticalAlignment = Alignment.Top) { // round-2 V15: D-day on the name line, not between the two lines
                        Column(GlanceModifier.defaultWeight()) {
                            Text(name, style = TextStyle(fontSize = 16.sp, color = fg, fontWeight = FontWeight.Medium), maxLines = 1)
                            if (line2.isNotEmpty()) Text(line2, style = TextStyle(fontSize = 13.sp, color = sub), maxLines = 1)
                        }
                        val near = d != null && d <= 7
                        // code review U-25 (P4): soon is said in words too, not by red alone;
                        // QA C2Q-06: not at 150 %+, where it left the name 4-5 letters (red + the spoken sentence remain)
                        if (near && fs < 1.5f) Text("곧 ", style = TextStyle(fontSize = 13.sp, color = soon, fontWeight = FontWeight.Medium))
                        Text(WidgetData.dday(c, today), style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = if (near) soon else fg))
                    }
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
    init {
        // QA C2Q-07: a font size change redraws the rows while the app process is alive (no broadcast reaches a manifest receiver)
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.FONT_SCALE), false,
            object : ContentObserver(null) { override fun onChange(selfChange: Boolean) = onCouponsChanged() })
    }

    override fun onCouponsChanged() {
        scope.launch { runCatching { CouponWidget().updateAll(context) } }
    }
}
