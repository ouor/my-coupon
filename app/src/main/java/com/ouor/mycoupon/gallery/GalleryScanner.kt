package com.ouor.mycoupon.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.ImageStore
import com.ouor.mycoupon.data.ScanResult
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.domain.Draft
import com.ouor.mycoupon.domain.ImportSession
import com.ouor.mycoupon.ocr.Barcodes
import com.ouor.mycoupon.ocr.CouponRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** S9 range choices. */
enum class ScanRange(val months: Long) { MONTH_1(1), MONTH_3(3), MONTH_12(12) }

data class ScanProgress(
    /** range of the current/last run; null before the first run */
    val range: ScanRange? = null,
    /** images in the range (with partial access: all allowed images) */
    val inRange: Int = 0,
    /** of [inRange], images already checked by an earlier scan (not checked again) */
    val alreadyChecked: Int = 0,
    /** images this scan goes through; after a resume it includes [resumedFrom] */
    val total: Int = 0,
    /** images checked so far; continues from [resumedFrom] after a pause */
    val checked: Int = 0,
    /** > 0 when this run resumed a paused one: "380장까지 확인했어요. 이어서 찾을게요" */
    val resumedFrom: Int = 0,
    /** images that passed screening and full recognition says are coupons */
    val found: List<Draft> = emptyList(),
    val running: Boolean = false,
    val finished: Boolean = false,
)

/**
 * F9 in two stages: (1) cheap screening, a ZXing pass on a ~1/2-size decode; (2) full F2 recognition of the
 * survivors; "not a coupon" results are dropped (spec Q12). Progress is kept in ScanLog, so cancelling the
 * coroutine (leaving S9) is the pause, and calling [scan] again resumes without re-checking (AC4).
 */
@Singleton
class GalleryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: CouponRepository,
    private val import: ImportSession,
    private val recognizer: CouponRecognizer,
    private val settings: Settings,
) {
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    /** Full or partial (Android 14 "selected photos") access. */
    fun hasPermission(): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 34 -> granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES)
            else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /** Android 14+ "선택한 사진만 허용": only the selected photos are visible, and the range is not offered (S9 ②). */
    fun isPartialAccess(): Boolean = Build.VERSION.SDK_INT >= 34 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

    /** Lower DATE_ADDED bound of [range]; partial access ignores the range (every allowed photo). */
    private fun since(range: ScanRange): Long =
        if (isPartialAccess()) 0 else ZonedDateTime.now().minusMonths(range.months).toEpochSecond()

    /** S9 ② "사진 1,240장" / "허용한 사진 24장": images in [range]. */
    fun count(range: ScanRange): Int = runCatching { query(since(range)).size }.getOrDefault(0)

    /** Permissions S9 asks for. */
    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    data class Media(val id: Long, val uri: Uri, val addedSec: Long)

    /** Images added since [sinceSec], newest first. With partial access MediaStore returns only the selected ones. */
    fun query(sinceSec: Long): List<Media> {
        val out = ArrayList<Media>()
        val coll = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            coll, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED),
            "${MediaStore.Images.Media.DATE_ADDED} > ?", arrayOf(sinceSec.toString()),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += Media(id, ContentUris.withAppendedId(coll, id), c.getLong(1))
            }
        }
        return out
    }

    /** Stage 1, tens of ms per image. */
    fun screen(uri: Uri): Boolean {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return false
        // ~1200 px long side keeps 1D modules >= ~1.5 px on phone screenshots
        val bmp = ImageStore.decode(bytes, SCREEN_SIDE) ?: return false
        val img = CouponRecognizer.toBgr(bmp)
        bmp.recycle()
        return Barcodes.quickFind(Barcodes.gray(img), img.width, img.height)
    }

    /** S9 ③. Results accumulate in [progress]; [ImportSession.startWith] hands them to S4. */
    suspend fun scan(range: ScanRange) = withContext(Dispatchers.Default) {
        val done = repo.scannedIds()
        val prev = _progress.value
        val known = prev.found.mapNotNull { it.mediaId }.toSet()
        val all = query(since(range))
        val media = all.filter { it.id !in done }
        // shortcut: the resume point lives in memory only; after process death ScanLog still skips checked images, the count restarts
        val resumed = if (prev.range == range && !prev.finished && prev.checked > 0) prev.checked else 0
        _progress.update {
            it.copy(range = range, inRange = all.size, alreadyChecked = all.size - media.size, total = resumed + media.size,
                checked = resumed, resumedFrom = resumed, running = true, finished = false)
        }
        var checked = resumed
        try {
            for (m in media) {
                coroutineContext.ensureActive()
                if (m.id !in known) check(m)?.let { d -> _progress.update { it.copy(found = it.found + d) } }
                checked++
                _progress.update { it.copy(checked = checked) }
            }
            settings.lastGalleryScanSec = maxOf(settings.lastGalleryScanSec, media.maxOfOrNull { it.addedSec } ?: 0)
            _progress.update { it.copy(finished = true) }
        } finally {
            _progress.update { it.copy(running = false) }
        }
    }

    /** S1 "새 쿠폰 이미지 N장" (F9): only images newer than the last look; no notification. Returns the found count. */
    suspend fun checkNew(): Int = withContext(Dispatchers.Default) {
        if (!hasPermission()) return@withContext 0
        val last = settings.lastGalleryScanSec
        if (last == 0L) { // first time: start from now, do not suggest the whole gallery
            settings.lastGalleryScanSec = System.currentTimeMillis() / 1000
            return@withContext 0
        }
        val done = repo.scannedIds()
        val media = query(last).filter { it.id !in done }
        for (m in media) check(m)?.let { d -> _progress.update { it.copy(found = it.found + d) } }
        settings.lastGalleryScanSec = maxOf(last, media.maxOfOrNull { it.addedSec } ?: last)
        _progress.value.found.size
    }

    /** Hands found coupons to S4 and clears them here. */
    fun sendToReview() {
        import.startWith(_progress.value.found, com.ouor.mycoupon.domain.ImportOrigin.GALLERY_SCAN)
        _progress.value = ScanProgress()
    }

    private suspend fun check(m: Media): Draft? {
        val pass = runCatching { screen(m.uri) }.getOrDefault(false)
        if (!pass) {
            repo.markScan(m.id, ScanResult.NOT_COUPON)
            return null
        }
        val d = import.readDraft(0, ImportSession.Source(m.uri, m.uri.toString(), m.id), _progress.value.found.mapNotNull { it.coupon })
        if (!d.isCoupon || d.duplicate != null) { // already registered images are not suggested again
            import.dropFiles(d)
            repo.markScan(m.id, if (d.isCoupon) ScanResult.REGISTERED else ScanResult.NOT_COUPON)
            return null
        }
        repo.markScan(m.id, ScanResult.COUPON)
        return d
    }

    companion object {
        const val SCREEN_SIDE = 1200
    }
}
