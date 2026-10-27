package com.ouor.mycoupon.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ouor.mycoupon.R
import com.ouor.mycoupon.data.AppSettings
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponChangeListener
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.AppIntents
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Pure F8 rules (unit-tested): who is due today and what the notification says. */
object ReminderPlanner {
    /** Coupons whose reminder day is [today], ordered amount desc then expiry asc (spec F8). */
    fun due(coupons: List<Coupon>, defaults: List<Int>, today: LocalDate): List<Coupon> = coupons
        .filter { c ->
            val exp = c.expiryDate ?: return@filter false
            if (c.status != CouponStatus.ACTIVE || exp < today) return@filter false
            ChronoUnit.DAYS.between(today, exp).toInt() in (c.reminderOffsets ?: defaults)
        }
        .sortedWith(compareByDescending<Coupon> { amount(it) }.thenBy { it.expiryDate })

    private fun amount(c: Coupon) = if (c.type == CouponType.AMOUNT) c.balance ?: c.faceValue ?: 0 else 0

    fun won(v: Long): String = NumberFormat.getNumberInstance(Locale.KOREA).format(v) + "원"

    fun title(c: Coupon): String = listOfNotNull(c.brand, shortName(c.brand, c.productName)).joinToString(" ").ifEmpty { "쿠폰" }

    private val leadingBracket = Regex("""^\s*[(\[（【]([^)\]）】]*)[)\]）】]\s*""")
    private fun norm(s: String) = s.filterNot(Char::isWhitespace).lowercase()

    /**
     * Display only (review U1): issuers repeat the brand in front of the name, "(메가MGC커피)팥빙 젤라또" with brand 메가MGC커피
     * → "팥빙 젤라또", "[CU] 30,000원권" → "30,000원권". Stored data and recognition stay as they are.
     */
    fun shortName(brand: String?, name: String?): String? {
        if (name == null || brand.isNullOrBlank()) return name
        val m = leadingBracket.find(name) ?: return name
        val inner = norm(m.groupValues[1])
        val b = norm(brand)
        val rest = name.substring(m.range.last + 1).trim()
        return if (rest.isNotEmpty() && inner.isNotEmpty() && (inner == b || inner.contains(b) || b.contains(inner))) rest else name
    }

    /** N1 single: "3일 남았어요 · 11월 3일까지", amount: "잔액 12,400원 · 3일 남았어요", the day: "오늘까지예요". */
    fun body(c: Coupon, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, c.expiryDate)
        val left = if (days == 0L) "오늘까지예요" else "${days}일 남았어요"
        return if (c.type == CouponType.AMOUNT && c.balance != null) "잔액 ${won(c.balance)} · $left"
        else if (days == 0L) left else "$left · ${c.expiryDate!!.monthValue}월 ${c.expiryDate.dayOfMonth}일까지"
    }

    /** N1 multi line: "CU 모바일상품권 30,000원 · 3일 남음" */
    fun line(c: Coupon, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, c.expiryDate)
        val money = if (c.type == CouponType.AMOUNT && c.balance != null) " ${won(c.balance)}" else ""
        return "${title(c)}$money · " + if (days == 0L) "오늘까지" else "${days}일 남음"
    }

    /** Next run at [time]: today if still ahead, else tomorrow. */
    fun nextRun(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val t = now.toLocalDate().atTime(time)
        return if (t.isAfter(now)) t else t.plusDays(1)
    }
}

/**
 * F8: one unique WorkManager job per day at the reminder time; it evaluates the coupons when it runs,
 * so edits (S5 expiry, S12 offsets, use/undo) need no rescheduling. WorkManager restores it after reboot
 * and app update (spec F8 AC3). No exact alarms (spec: ±15 min is fine).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings,
    private val repo: dagger.Lazy<CouponRepository>, // Lazy: the repository holds this listener
    @AppScope private val scope: CoroutineScope,
) : CouponChangeListener {
    private val wm get() = WorkManager.getInstance(context)

    /** Keeps the pending job; call on app start and after coupon changes. */
    fun ensure() = enqueue(ExistingWorkPolicy.KEEP)

    /** After S11 changes the time. */
    fun reschedule() = enqueue(ExistingWorkPolicy.REPLACE)

    fun cancel() = wm.cancelUniqueWork(WORK)

    override fun onCouponsChanged() = ensure()

    /** The daily job exists only while there is an active coupon (F14 AC1: none left after wipe, QA R1-18). */
    internal fun enqueue(policy: ExistingWorkPolicy) {
        scope.launch { if (repo.get().active().isEmpty()) cancel() else enqueueNow(policy) }
    }

    private fun enqueueNow(policy: ExistingWorkPolicy) {
        val now = LocalDateTime.now()
        val next = ReminderPlanner.nextRun(now, settings.app.reminderTime)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK, policy, req)
    }

    companion object {
        const val WORK = "expiry-reminder"
    }
}

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: CouponRepository,
    private val settings: Settings,
    private val notifier: ReminderNotifier,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        val prefs = applicationContext.getSharedPreferences("reminder", Context.MODE_PRIVATE)
        // at most one notification a day (spec F8), even if the job runs twice
        if (prefs.getLong("last_day", -1) != today.toEpochDay()) {
            val due = ReminderPlanner.due(repo.active(), settings.app.reminderOffsets, today)
            if (due.isNotEmpty()) notifier.show(due, today)
            prefs.edit().putLong("last_day", today.toEpochDay()).apply()
        }
        // shortcut: W1 D-day refreshes here (reminder time) and on app open, not exactly at midnight
        runCatching { com.ouor.mycoupon.widget.CouponWidget().updateAll(applicationContext) }
        // chain the next day; REPLACE from inside the running work is safe for a unique OneTimeWork. No active coupon: stop the chain
        if (repo.active().isEmpty()) return Result.success()
        val now = LocalDateTime.now()
        val next = ReminderPlanner.nextRun(now.plusMinutes(1), settings.app.reminderTime)
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            ReminderScheduler.WORK, ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build(),
        )
        return Result.success()
    }
}

/** N1. Channel "만료 알림" only for expiry reminders (spec F8). */
@Singleton
class ReminderNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    fun createChannel() {
        val ch = NotificationChannel(CHANNEL, context.getString(R.string.channel_expiry), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canPost(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun show(due: List<Coupon>, today: LocalDate) {
        if (!canPost()) return
        val single = due.size == 1
        val intent = AppIntents.reminder(context, due.map { it.id }.toLongArray())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = AppIntents.pending(context, AppIntents.REQUEST_REMINDER, intent)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("만료가 다가오는 쿠폰이 있어요")
            .build()
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
        if (single) {
            b.setContentTitle(ReminderPlanner.title(due[0])).setContentText(ReminderPlanner.body(due[0], today))
        } else {
            val style = NotificationCompat.InboxStyle()
            due.take(5).forEach { style.addLine(ReminderPlanner.line(it, today)) }
            b.setContentTitle("오늘 챙길 쿠폰 ${due.size}개")
                .setContentText("${ReminderPlanner.title(due[0])} 외 ${due.size - 1}개")
                .setStyle(style)
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, b.build())
        } catch (_: SecurityException) {
            // permission revoked between the check and the call
        }
    }

    /** F14 wipe: posted N1 notifications (brand, name, balance) and the once-a-day state go too (S-03). */
    fun clear() {
        NotificationManagerCompat.from(context).cancelAll()
        context.getSharedPreferences("reminder", Context.MODE_PRIVATE).edit().clear().apply()
    }

    companion object {
        const val CHANNEL = "expiry"
        const val NOTIFICATION_ID = 1
    }
}
