package com.ouor.mycoupon.ui.importing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.domain.ImportOrigin
import com.ouor.mycoupon.domain.ImportSession
import com.ouor.mycoupon.domain.ImportState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
