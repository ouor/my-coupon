package com.ouor.mycoupon.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** spec §6.2 settings. In the backup file as backup.SettingsV1 (F15). */
data class AppSettings(
    /** default reminder offsets in days before expiry (S11): D-7, D-1, the day */
    val reminderOffsets: List<Int> = listOf(7, 1, 0),
    /** minutes after midnight (S11, default 09:00) */
    val reminderMinute: Int = 9 * 60,
) {
    val reminderTime: LocalTime get() = LocalTime.of(reminderMinute / 60, reminderMinute % 60)

    /** Only values S11 can produce (security review S-04): a bad minute would crash reminderTime at every start. */
    fun valid() = AppSettings(
        reminderOffsets = reminderOffsets.filter { it in OFFSET_CHOICES }.distinct().sortedDescending(),
        reminderMinute = reminderMinute.takeIf { it in 0 until 24 * 60 } ?: AppSettings().reminderMinute,
    )

    companion object {
        /** S11 choices */
        val OFFSET_CHOICES = listOf(30, 14, 7, 3, 1, 0)
    }
}

/** Device-local state that is not backed up: first run, scan cursor, one-shot banners. */
@Singleton
class Settings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var app: AppSettings
        get() = AppSettings(
            reminderOffsets = prefs.getString(K_OFFSETS, null)?.let { s -> if (s.isEmpty()) emptyList() else s.split(",").mapNotNull(String::toIntOrNull) }
                ?: AppSettings().reminderOffsets,
            reminderMinute = prefs.getInt(K_MINUTE, AppSettings().reminderMinute),
        ).valid()
        set(v) {
            val ok = v.valid()
            prefs.edit().putString(K_OFFSETS, ok.reminderOffsets.joinToString(",")).putInt(K_MINUTE, ok.reminderMinute).apply()
        }

    fun observeApp(): Flow<AppSettings> = observe { app }

    /** S0 shown once */
    var onboarded: Boolean
        get() = prefs.getBoolean(K_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(K_ONBOARDED, v).apply()

    /** D8: notification permission rationale shown after the first registration */
    var notificationAsked: Boolean
        get() = prefs.getBoolean(K_NOTIF_ASKED, false)
        set(v) = prefs.edit().putBoolean(K_NOTIF_ASKED, v).apply()

    /** F9: MediaStore DATE_ADDED (seconds) of the newest image already looked at by the new-image check */
    var lastGalleryScanSec: Long
        get() = prefs.getLong(K_LAST_SCAN, 0)
        set(v) = prefs.edit().putLong(K_LAST_SCAN, v).apply()

    /** F19: newest crash file name the S1 banner already showed */
    var crashBannerSeen: String?
        get() = prefs.getString(K_CRASH_SEEN, null)
        set(v) = prefs.edit().putString(K_CRASH_SEEN, v).apply()

    /** S14 "마지막 백업 10월 3일": epoch ms of the last successful export, 0 = never */
    var lastBackupAt: Long
        get() = prefs.getLong(K_LAST_BACKUP, 0)
        set(v) = prefs.edit().putLong(K_LAST_BACKUP, v).apply()

    fun observeLastBackup(): Flow<Long> = observe { lastBackupAt }

    /** C2-10: epoch day the S1 "오늘·내일 끝나는 쿠폰" banner was closed or that day's N1 was tapped; hidden for that day only. */
    var expiringBannerHiddenDay: Long
        get() = prefs.getLong(K_EXPIRING_HIDDEN, Long.MIN_VALUE)
        set(v) = prefs.edit().putLong(K_EXPIRING_HIDDEN, v).apply()

    fun hideExpiringBanner(today: LocalDate = LocalDate.now()) { expiringBannerHiddenDay = today.toEpochDay() }

    fun observeExpiringBannerHiddenDay(): Flow<Long> = observe { expiringBannerHiddenDay }

    /** C2-15: S8 opens with the input the user used last; false = "남은 금액" (the first default). */
    var amountInputSpent: Boolean
        get() = prefs.getBoolean(K_AMOUNT_SPENT, false)
        set(v) = prefs.edit().putBoolean(K_AMOUNT_SPENT, v).apply()

    /**
     * C2-17 "아까 썼어요?": the last coupon S6 showed for [LINGER_MS]+ and left without a use. One pending at a time; a coupon is
     * asked about once ([answerUseAsk]); older than a day it is not asked.
     */
    fun recordLinger(id: Long, shownMs: Long, now: Long = System.currentTimeMillis()) {
        if (shownMs < LINGER_MS || id.toString() in asked()) return
        prefs.edit().putLong(K_LINGER_ID, id).putLong(K_LINGER_AT, now).apply()
    }

    /** The coupon id to ask about, or null. The caller still checks that the coupon is available. */
    fun pendingUseAsk(now: Long = System.currentTimeMillis()): Long? {
        val id = prefs.getLong(K_LINGER_ID, 0)
        val at = prefs.getLong(K_LINGER_AT, 0)
        return id.takeIf { it > 0 && now - at in 0..DAY_MS && it.toString() !in asked() }
    }

    fun observePendingUseAsk(): Flow<Long?> = observe { pendingUseAsk() }

    /** Banner answered ([썼어요] / [아니요] / closed): never ask about [id] again. */
    fun answerUseAsk(id: Long) {
        val set = (asked() + id.toString()).toList().takeLast(MAX_ASKED).toSet()
        prefs.edit().putStringSet(K_ASKED, set).remove(K_LINGER_ID).remove(K_LINGER_AT).apply()
    }

    private fun asked(): Set<String> = prefs.getStringSet(K_ASKED, null).orEmpty()

    /** F14 wipe (lead decision 2026-10-28: everything goes): reminder settings back to defaults, last backup time cleared. */
    fun resetForWipe() {
        app = AppSettings()
        lastBackupAt = 0
        prefs.edit().remove(K_EXPIRING_HIDDEN).remove(K_AMOUNT_SPENT).remove(K_LINGER_ID).remove(K_LINGER_AT).remove(K_ASKED).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private fun <T> observe(read: () -> T): Flow<T> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        trySend(read())
        prefs.registerOnSharedPreferenceChangeListener(l)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    companion object {
        /** C2-17: S6 time that counts as "was at the counter" */
        const val LINGER_MS = 15_000L
        private const val DAY_MS = 24 * 3600_000L
        /** ids are kept only to not ask twice; a short tail is enough (ids are never reused) */
        private const val MAX_ASKED = 200
        private const val K_EXPIRING_HIDDEN = "expiring_banner_hidden_day"
        private const val K_AMOUNT_SPENT = "amount_input_spent"
        private const val K_LINGER_ID = "linger_id"
        private const val K_LINGER_AT = "linger_at"
        private const val K_ASKED = "use_asked_ids"
        private const val K_OFFSETS = "reminder_offsets"
        private const val K_MINUTE = "reminder_minute"
        private const val K_ONBOARDED = "onboarded"
        private const val K_NOTIF_ASKED = "notification_asked"
        private const val K_LAST_SCAN = "last_gallery_scan_sec"
        private const val K_CRASH_SEEN = "crash_banner_seen"
        private const val K_LAST_BACKUP = "last_backup_at"
    }
}
