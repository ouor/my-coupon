package com.ouor.mycoupon.ui.importing

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.domain.Draft
import com.ouor.mycoupon.domain.DraftStage
import com.ouor.mycoupon.domain.ImportOrigin
import com.ouor.mycoupon.domain.ImportSession
import com.ouor.mycoupon.domain.ImportState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** S3/S4 + S5 for drafts. */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val session: ImportSession,
    repo: CouponRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val state: StateFlow<ImportState> = session.state
    val brands: StateFlow<List<String>> = repo.observeBrands().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Code review A8: images that were still being read when the process died (S4 asks to pick them again). */
    var lostOnRestore = 0
        private set
    fun lostShown() { lostOnRestore = 0 }

    init {
        // the batch lives in the app-scoped session; after a process death it comes back from this activity's SavedState
        if (session.state.value.drafts.isEmpty()) saved.get<ArrayList<Draft>>(KEY_DRAFTS)?.let { drafts ->
            val origin = saved.get<String>(KEY_ORIGIN)?.let { runCatching { ImportOrigin.valueOf(it) }.getOrNull() } ?: ImportOrigin.PICKER
            session.restore(origin, drafts)
            lostOnRestore = saved.get<Int>(KEY_READING) ?: 0
        }
        viewModelScope.launch {
            session.state.collect { st ->
                val finished = st.drafts.filter { it.stage != DraftStage.READING }
                saved[KEY_DRAFTS] = if (st.drafts.isEmpty()) null else ArrayList(finished)
                saved[KEY_ORIGIN] = st.origin.name
                saved[KEY_READING] = st.drafts.size - finished.size
            }
        }
    }

    fun start(uris: List<Uri>, origin: ImportOrigin) = session.start(uris.map { ImportSession.Source(it) }, origin)
    fun setChecked(key: Int, v: Boolean) = session.setChecked(key, v)
    fun exclude(key: Int) = session.exclude(key)
    fun edit(key: Int, c: Coupon) = session.edit(key, c)
    fun stop() = session.stopReading()
    fun discard() = session.discard()

    /** Returns registered ids (caller then shows "쿠폰 N개를 등록했어요" and, the first time, the notification rationale). */
    fun register(done: (List<Long>) -> Unit) = viewModelScope.launch { done(session.register()) }

    private companion object {
        const val KEY_DRAFTS = "import.drafts"
        const val KEY_ORIGIN = "import.origin"
        const val KEY_READING = "import.reading"
    }
}
