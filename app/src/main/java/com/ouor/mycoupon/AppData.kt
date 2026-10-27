package com.ouor.mycoupon

import android.content.Context
import com.ouor.mycoupon.backup.BackupManager
import com.ouor.mycoupon.crash.CrashLog
import com.ouor.mycoupon.data.AppSettings
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.ImageStore
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.domain.ImportSession
import com.ouor.mycoupon.gallery.GalleryScanner
import com.ouor.mycoupon.reminder.ReminderNotifier
import com.ouor.mycoupon.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide data lifecycle: F14 wipe and start-up cleaning. Lives at the root because it touches every layer. */
@Singleton
class AppData @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: ImportSession,
    private val scanner: GalleryScanner,
    private val repo: CouponRepository,
    private val images: ImageStore,
    private val reminders: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val crashLog: CrashLog,
    private val settings: Settings,
    private val backup: BackupManager,
) {
    /**
     * F14 [모든 데이터 삭제] (code review A5/S-03): DB rows (secure_delete, WAL truncated), image copies, unreviewed scan
     * finds, the last shared copy, plaintext backup temp files, posted reminders and their state, crash reports, settings.
     * Gallery originals and backup files the user saved elsewhere are untouched.
     */
    suspend fun wipeAll() {
        session.discard()
        scanner.reset()
        repo.wipe()
        reminders.cancel()
        notifier.clear()
        crashLog.clear()
        backup.clearTemp()
        File(context.cacheDir, "share").deleteRecursively()
        settings.app = AppSettings()
        reminders.reschedule()
    }

    /**
     * At start (code review A4, A6/S-03): plaintext backup ZIPs and image copies no coupon refers to, left by a process
     * that died during an import, a scan or a restore preview. Only files older than [before] (this process's start):
     * a batch started meanwhile is never touched.
     */
    suspend fun cleanUp(before: Long) {
        backup.clearTemp()
        val keep = repo.all().flatMap { listOf(it.imagePath, it.thumbPath) }.toSet()
        images.removeOrphans(keep, before)
    }
}
