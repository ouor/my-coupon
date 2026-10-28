package com.ouor.mycoupon.ui.coupon

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.data.UsageLog
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.domain.CouponActions
import com.ouor.mycoupon.domain.UsageInputException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** S6 / S5 (stored coupon) / S8 / S12. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CouponViewModel @Inject constructor(
    private val repo: CouponRepository,
    private val actions: CouponActions,
    private val settings: Settings,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val id = MutableStateFlow<Long?>(null)
    val coupon: StateFlow<Coupon?> = id.flatMapLatest { it?.let(repo::observe) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val usage: StateFlow<List<UsageLog>> = id.flatMapLatest { it?.let(repo::observeUsage) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val brands: StateFlow<List<String>> = repo.observeBrands().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** QA R1-09: an old notification/widget link to a deleted (or re-imported) coupon id */
    val missing = MutableStateFlow(false)

    /** The row as S5 opened it: [save] writes only what the user changed since (A-04). */
    private var editBase: Coupon? = null

    fun load(couponId: Long, fromReminder: Boolean = false) {
        id.value = couponId
        viewModelScope.launch { repo.get(couponId).let { editBase = it; missing.value = it == null } }
        // code review U-05: once per screen, not again on every return from S7/S5 (or after a process death)
        if (fromReminder && saved.get<Boolean>(KEY_REMINDER) != true) {
            saved[KEY_REMINDER] = true
            viewModelScope.launch { actions.openedFromReminder(couponId) }
        }
    }

    // code review A14: screens navigate away right after these writes; leaving clears this ViewModel, the write must still land
    // C2-04: writes run in tap order, so an undo tapped before [사용 완료] has landed cannot be overtaken by it
    // the result is handed back for undo (core D-07); a failure still crashes as before (it is not kept in the Deferred)
    private var lastAct: Job? = null
    private fun <T> act(block: suspend (Long) -> T): Deferred<T?> {
        val v = id.value ?: return CompletableDeferred(null)
        val prev = lastAct
        val out = CompletableDeferred<T?>()
        lastAct = viewModelScope.launch(NonCancellable) { prev?.join(); out.complete(block(v)) }
        return out
    }

    /** The usage record the last [markUsed] made; undo removes that one only, nothing if another came since (core D-07). */
    var useLog: Deferred<Long?>? = null
        private set

    /** [givenTo]: C2-25 "누구에게(선택)" for [UsedReason.GIVEN]; blank = nobody named. */
    fun markUsed(reason: UsedReason = UsedReason.USED, givenTo: String? = null) = act { actions.markUsed(it, reason, givenTo) }.also { useLog = it }
    /** C2-17: S6 closed after [shownMs] without a use; the core asks on S1 only past 15 s, once per coupon. */
    fun lingered(couponId: Long, shownMs: Long) = settings.recordLinger(couponId, shownMs)
    /** C2-15: the S8 input method the user picked last (false = "남은 금액", the first default). */
    var amountInputSpent: Boolean
        get() = settings.amountInputSpent
        set(v) { settings.amountInputSpent = v }
    /** [onError] gets the refusal (C1): `error` for the kind, `message` as Korean copy. Other failures are not swallowed. */
    fun recordRemaining(v: Long, onError: (UsageInputException) -> Unit = {}) =
        act { try { actions.recordRemaining(it, v) } catch (e: UsageInputException) { onError(e); null } }.also { if (v == 0L) useLog = it }
    fun recordSpent(v: Long, onError: (UsageInputException) -> Unit = {}) =
        act { try { actions.recordSpent(it, v) } catch (e: UsageInputException) { onError(e); null } }
    fun undoUse() = act { actions.undoUse(it, useLog?.await()) }
    /** [logId]: the record the user pointed at (S6 [기록 지우기], code review U-07); null = the latest */
    fun undoLastBalance(logId: Long? = null) = act { actions.undoLastBalance(it, logId) }
    /** S12: the app-wide default the sheet offers to follow (code review U-27: not read from the Activity) */
    val defaultOffsets: List<Int> get() = settings.app.reminderOffsets
    fun confirm(field: String) = act { actions.confirm(it, field) }
    fun setReminderOffsets(v: List<Int>?) = act { actions.setReminderOffsets(it, v) }
    fun trashAnswered() = act { actions.trashAnswered(it) }
    fun save(c: Coupon) = act { actions.edit(it, editBase ?: c, c) }
    fun delete() = viewModelScope.launch { id.value?.let { actions.delete(it) } }

    private companion object { const val KEY_REMINDER = "reminder_opened" }
}
