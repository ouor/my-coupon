package com.ouor.mycoupon.ui.settings

import com.ouor.mycoupon.reminder.ReminderNotifier
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouor.mycoupon.AppData
import com.ouor.mycoupon.backup.BackupAuthException
import com.ouor.mycoupon.backup.BackupFormatException
import com.ouor.mycoupon.backup.BackupManager
import com.ouor.mycoupon.backup.BackupPreview
import com.ouor.mycoupon.crash.CrashLog
import com.ouor.mycoupon.data.AppSettings
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.domain.UsageStats
import com.ouor.mycoupon.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val app: AppSettings = AppSettings(),
    val stats: UsageStats = UsageStats(0, 0),
    val crashFiles: List<File> = emptyList(),
    /** S14 "쿠폰 12개", S10 wipe dialog */
    val coupons: Int = 0,
    /** S14 "마지막 백업 10월 3일": epoch ms, null = never backed up (C3) */
    val lastBackupAt: Long? = null,
)

/** C2: why a backup step failed. */
enum class BackupFailure {
    /** not a 내 쿠폰 backup, damaged, or a newer format */
    FORMAT,
    /** reading/writing failed (storage full, file gone) */
    IO,
    /** [SettingsViewModel.cancelBackup] or the screen went away */
    CANCELLED,
}

sealed interface BackupUi {
    data object Idle : BackupUi
    data object Working : BackupUi
    /** [missingImages]: coupons whose image file was gone, saved as data only (core D-05) */
    data class Exported(val count: Int, val missingImages: Int = 0) : BackupUi
    data class Preview(val p: BackupPreview) : BackupUi
    data class Imported(val count: Int) : BackupUi
    data object WrongPassword : BackupUi
    data class Failed(val reason: BackupFailure) : BackupUi
}

/** S10 / S11 / S14. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: Settings,
    private val repo: CouponRepository,
    private val crashLog: CrashLog,
    private val reminders: ReminderScheduler,
    private val backup: BackupManager,
    private val data: AppData,
    private val notifier: ReminderNotifier,
) : ViewModel() {
    /** C2-16 "앱이 쓰는 공간"; null while measuring. */
    val storage = MutableStateFlow<AppData.Storage?>(null)
    fun measureStorage() = viewModelScope.launch { storage.value = data.storage() }

    /** C2-08 [알림 소리 바꾸기] */
    fun channelSettingsIntent() = notifier.channelSettingsIntent()
    fun notificationsOff() = !notifier.canPost()

    private val crash = MutableStateFlow(crashLog.files())
    val state: StateFlow<SettingsState> = combine(settings.observeApp(), repo.observeAll(), crash, settings.observeLastBackup()) { a, all, c, last ->
        SettingsState(a, Stats.of(all), c, all.size, last.takeIf { it > 0 })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    val backupUi = MutableStateFlow<BackupUi>(BackupUi.Idle)

    fun setReminders(offsets: List<Int>, minute: Int) {
        settings.app = AppSettings(offsets.distinct().sortedDescending(), minute)
        reminders.reschedule()
    }

    /** F14 [모든 데이터 삭제] (after the 2-step confirmation in the UI). Gallery originals are untouched. */
    fun wipeAll(done: () -> Unit) = viewModelScope.launch {
        data.wipeAll()
        crash.value = emptyList()
        measureStorage()
        done()
    }

    /** A6: leaving S14 with a restore preview open must not leave the decrypted ZIP in the cache. */
    override fun onCleared() {
        (backupUi.value as? BackupUi.Preview)?.let { backup.cancel(it.p) }
    }

    fun export(target: Uri, password: CharArray) = backupJob { BackupUi.Exported(backup.export(target, password), backup.lastExportMissingImages) }
    fun open(source: Uri, password: CharArray) = backupJob { BackupUi.Preview(backup.open(source, password)) }
    fun import(p: BackupPreview) = backupJob { BackupUi.Imported(backup.import(p)) }
    fun cancelImport(p: BackupPreview) { backup.cancel(p); backupUi.value = BackupUi.Idle }

    private var backupWork: Job? = null

    /** Stops an export or open in progress (import is all-or-nothing and always finishes). */
    fun cancelBackup() { backupWork?.cancel() }

    private fun backupJob(block: suspend () -> BackupUi): Job = viewModelScope.launch {
        backupUi.value = BackupUi.Working
        backupUi.value = try {
            block()
        } catch (_: BackupAuthException) {
            BackupUi.WrongPassword
        } catch (_: BackupFormatException) {
            BackupUi.Failed(BackupFailure.FORMAT)
        } catch (e: CancellationException) {
            backupUi.value = BackupUi.Failed(BackupFailure.CANCELLED)
            throw e
        } catch (_: Exception) {
            BackupUi.Failed(BackupFailure.IO)
        }
    }.also { backupWork = it }
}
