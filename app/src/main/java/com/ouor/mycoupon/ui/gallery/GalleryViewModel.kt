package com.ouor.mycoupon.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouor.mycoupon.gallery.GalleryScanner
import com.ouor.mycoupon.gallery.ScanProgress
import com.ouor.mycoupon.gallery.ScanRange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
