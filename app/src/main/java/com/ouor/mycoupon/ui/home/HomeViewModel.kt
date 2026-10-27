package com.ouor.mycoupon.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouor.mycoupon.crash.CrashLog
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.ListFilter
import com.ouor.mycoupon.data.ListSort
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.domain.CouponActions
import com.ouor.mycoupon.gallery.GalleryScanner
import com.ouor.mycoupon.reminder.ReminderNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    /** review U9: brand chips, most coupons first (available ones only) */
    val brands: List<String> = emptyList(),
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
    /** unreviewed gallery finds (F9 new-image check and S9 share one list); 0 again after review or wipe */
    private val newImages = scanner.progress.map { it.found.size }
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
            brands = everything.filter { it.isAvailable(base.today) }.mapNotNull { it.brand?.trim()?.ifEmpty { null } }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(10),
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
        viewModelScope.launch { runCatching { scanner.checkNew() } }
    }

    fun crashBannerShown() {
        settings.crashBannerSeen = crashLog.files().firstOrNull()?.name
        crashPending.value = false
    }

    /** "새 쿠폰 이미지 N장" tap → S4 */
    fun reviewNewImages() {
        scanner.sendToReview()
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
