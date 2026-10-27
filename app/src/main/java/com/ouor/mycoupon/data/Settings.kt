package com.ouor.mycoupon.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    fun clear() = prefs.edit().clear().apply()

    private fun <T> observe(read: () -> T): Flow<T> = callbackFlow {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        trySend(read())
        prefs.registerOnSharedPreferenceChangeListener(l)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }

    private companion object {
        const val K_OFFSETS = "reminder_offsets"
        const val K_MINUTE = "reminder_minute"
        const val K_ONBOARDED = "onboarded"
        const val K_NOTIF_ASKED = "notification_asked"
        const val K_LAST_SCAN = "last_gallery_scan_sec"
        const val K_CRASH_SEEN = "crash_banner_seen"
        const val K_LAST_BACKUP = "last_backup_at"
    }
}
