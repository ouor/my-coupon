package com.ouor.mycoupon.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouor.mycoupon.backup.BackupAuthException
import com.ouor.mycoupon.backup.BackupFormatException
import com.ouor.mycoupon.backup.BackupManager
import com.ouor.mycoupon.backup.BackupPreview
import com.ouor.mycoupon.crash.CrashLog
import com.ouor.mycoupon.data.AppSettings
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.ListFilter
import com.ouor.mycoupon.data.ListSort
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.data.UsageLog
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.domain.CouponActions
import com.ouor.mycoupon.domain.ImportOrigin
import com.ouor.mycoupon.domain.ImportSession
import com.ouor.mycoupon.domain.ImportState
import com.ouor.mycoupon.domain.Stats
import com.ouor.mycoupon.domain.UsageInputException
import com.ouor.mycoupon.domain.UsageStats
import com.ouor.mycoupon.gallery.GalleryScanner
import com.ouor.mycoupon.gallery.ScanProgress
import com.ouor.mycoupon.gallery.ScanRange
import com.ouor.mycoupon.reminder.ReminderNotifier
import com.ouor.mycoupon.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** S1 banner, one at a time in this priority (screens.md D3). */
enum class HomeBanner { CRASH, NOTIFICATIONS_OFF, NEEDS_REVIEW, NEW_IMAGES }

data class HomeState(
    val coupons: List<Coupon> = emptyList(),
    val filter: ListFilter = ListFilter.AVAILABLE,
    val sort: ListSort = ListSort.EXPIRY,
    val query: String = "",
    val today: LocalDate = LocalDate.now(),
    val reviewCount: Int = 0,
    /** NEEDS_REVIEW banner target: the available coupon needing a check that expires first, whatever the filter (C7) */
    val firstReviewId: Long? = null,
    /** S1 chip "사용 가능 12" */
    val availableCount: Int = 0,
    /** false until the first DB emission (S1 shows no empty state before it) */
    val loaded: Boolean = false,
    /** any coupon at all (S1 first-run empty state vs "쓸 수 있는 쿠폰이 없어요") */
    val hasCoupons: Boolean = false,
    val newImages: Int = 0,
    val banner: HomeBanner? = null,
)

/** S1 / S13. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: CouponRepository,
    private val settings: Settings,
    private val crashLog: CrashLog,
    private val notifier: ReminderNotifier,
    private val scanner: GalleryScanner,
    private val actions: CouponActions,
) : ViewModel() {
    val filter = MutableStateFlow(ListFilter.AVAILABLE)
    val sort = MutableStateFlow(ListSort.EXPIRY)
    val query = MutableStateFlow("")
    private val newImages = MutableStateFlow(0)
    private val crashPending = MutableStateFlow(crashLog.files().firstOrNull()?.name?.let { it != settings.crashBannerSeen } ?: false)
    private val notificationsOff = MutableStateFlow(false)

    private val list = repo.observeList(filter, sort, query)
    private val all = repo.observeAll()

    val state: StateFlow<HomeState> = combine(
        combine(list, filter, sort, query, repo.today()) { l, f, s, q, t -> HomeState(l, f, s, q, t) },
        all, newImages, crashPending, notificationsOff,
    ) { base, everything, n, crash, notifOff ->
        val toReview = everything.filter { it.needsCheck && it.isAvailable(base.today) }
        val review = toReview.size
        val hasCoupons = everything.isNotEmpty()
        base.copy(
            reviewCount = review, firstReviewId = toReview.minByOrNull { it.expiryDate ?: LocalDate.MAX }?.id, newImages = n, loaded = true, hasCoupons = hasCoupons,
            availableCount = everything.count { it.isAvailable(base.today) },
            banner = when {
                crash -> HomeBanner.CRASH
                notifOff && hasCoupons -> HomeBanner.NOTIFICATIONS_OFF
                review > 0 -> HomeBanner.NEEDS_REVIEW
                n > 0 -> HomeBanner.NEW_IMAGES
                else -> null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    /** Call from onResume: permission state can change outside the app. Also runs the F9 new-image check. */
    fun refresh() {
        notificationsOff.value = !notifier.canPost()
        viewModelScope.launch { newImages.value = runCatching { scanner.checkNew() }.getOrDefault(0) }
    }

    fun crashBannerShown() {
        settings.crashBannerSeen = crashLog.files().firstOrNull()?.name
        crashPending.value = false
    }

    /** "새 쿠폰 이미지 N장" tap → S4 */
    fun reviewNewImages() {
        scanner.sendToReview()
        newImages.value = 0
    }

    /** N1 with several coupons opens S1: each notified coupon counts for F18 "알림 받고 쓴 쿠폰". */
    fun openedFromReminder(ids: LongArray) = viewModelScope.launch { ids.forEach { actions.openedFromReminder(it) } }

    /** Snackbar [되돌리기] after S8/F16 (S6's ViewModel is gone by then) and S13 card [되돌리기]. */
    fun undoUse(id: Long) = viewModelScope.launch { actions.undoUse(id) }
    fun undoLastBalance(id: Long) = viewModelScope.launch { actions.undoLastBalance(id) }

    /** D8: S1 shows the notification rationale sheet once, after the first registration. */
    val notificationRationale = MutableStateFlow(false)
    fun registered() { if (!settings.notificationAsked && !notifier.canPost()) notificationRationale.value = true }
    fun notificationsAsked() { settings.notificationAsked = true; notificationRationale.value = false }

    /** F19 banner [오류 기록 보내기] shares the newest scrubbed crash file. */
    fun crashFile(): File? = crashLog.files().firstOrNull()

    /** T1: the single available coupon closest to expiry, or null when there are 0 or several (→ S1). */
    suspend fun tileTarget(): Long? {
        val today = LocalDate.now()
        val avail = repo.all().filter { it.isAvailable(today) }
        return avail.singleOrNull()?.id
    }
}

/** S3/S4 + S5 for drafts. */
@HiltViewModel
class ImportViewModel @Inject constructor(private val session: ImportSession, repo: CouponRepository) : ViewModel() {
    val state: StateFlow<ImportState> = session.state
    val brands: StateFlow<List<String>> = repo.observeBrands().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun start(uris: List<Uri>, origin: ImportOrigin) = session.start(uris.map { ImportSession.Source(it) }, origin)
    fun setChecked(key: Int, v: Boolean) = session.setChecked(key, v)
    fun exclude(key: Int) = session.exclude(key)
    fun edit(key: Int, c: Coupon) = session.edit(key, c)
    fun stop() = session.stopReading()
    fun discard() = session.discard()

    /** Returns registered ids (caller then shows "쿠폰 N개를 등록했어요" and, the first time, the notification rationale). */
    fun register(done: (List<Long>) -> Unit) = viewModelScope.launch { done(session.register()) }
}

/** S6 / S5 (stored coupon) / S8 / S12. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CouponViewModel @Inject constructor(
    private val repo: CouponRepository,
    private val actions: CouponActions,
) : ViewModel() {
    private val id = MutableStateFlow<Long?>(null)
    val coupon: StateFlow<Coupon?> = id.flatMapLatest { it?.let(repo::observe) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val usage: StateFlow<List<UsageLog>> = id.flatMapLatest { it?.let(repo::observeUsage) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val brands: StateFlow<List<String>> = repo.observeBrands().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** QA R1-09: an old notification/widget link to a deleted (or re-imported) coupon id */
    val missing = MutableStateFlow(false)

    fun load(couponId: Long, fromReminder: Boolean = false) {
        id.value = couponId
        viewModelScope.launch { missing.value = repo.get(couponId) == null }
        if (fromReminder) viewModelScope.launch { actions.openedFromReminder(couponId) }
    }

    private fun act(block: suspend (Long) -> Unit) { id.value?.let { viewModelScope.launch { block(it) } } }

    fun markUsed(reason: UsedReason = UsedReason.USED) = act { actions.markUsed(it, reason) }
    /** [onError] gets the refusal (C1): `error` for the kind, `message` as Korean copy. Other failures are not swallowed. */
    fun recordRemaining(v: Long, onError: (UsageInputException) -> Unit = {}) = act { try { actions.recordRemaining(it, v) } catch (e: UsageInputException) { onError(e) } }
    fun recordSpent(v: Long, onError: (UsageInputException) -> Unit = {}) = act { try { actions.recordSpent(it, v) } catch (e: UsageInputException) { onError(e) } }
    fun undoUse() = act { actions.undoUse(it) }
    fun undoLastBalance() = act { actions.undoLastBalance(it) }
    fun confirm(field: String) = act { actions.confirm(it, field) }
    fun setReminderOffsets(v: List<Int>?) = act { actions.setReminderOffsets(it, v) }
    fun trashAnswered() = act { actions.trashAnswered(it) }
    fun save(c: Coupon) = viewModelScope.launch { repo.update(c) }
    fun delete() = viewModelScope.launch { coupon.value?.let { repo.delete(it) } }
}

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
    data class Exported(val count: Int) : BackupUi
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
    private val session: ImportSession,
) : ViewModel() {
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
        session.discard()
        repo.wipe()
        reminders.cancel()
        crashLog.clear()
        crash.value = emptyList()
        settings.app = AppSettings()
        reminders.reschedule()
        done()
    }

    fun export(target: Uri, password: CharArray) = backupJob { BackupUi.Exported(backup.export(target, password)) }
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

/** S9. Leaving the screen clears the ViewModel and cancels the scan = pause (F9). */
@HiltViewModel
class GalleryViewModel @Inject constructor(private val scanner: GalleryScanner) : ViewModel() {
    val progress: StateFlow<ScanProgress> = scanner.progress
    private var job: Job? = null

    fun hasPermission() = scanner.hasPermission()
    fun permissions() = scanner.permissions()
    fun scan(range: ScanRange) {
        job?.cancel()
        job = viewModelScope.launch { scanner.scan(range) }
    }
    fun pause() = job?.cancel()
    fun sendToReview() = scanner.sendToReview()

    /** S9 ② "사진 1,240장": images in the range (partial access: all allowed images, see [isPartialAccess]). */
    suspend fun count(range: ScanRange): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { scanner.count(range) }

    /** C5: "선택한 사진만 허용" → S9 ② shows "허용한 사진 N장을 찾아볼게요" instead of the range. */
    fun isPartialAccess() = scanner.isPartialAccess()
}
