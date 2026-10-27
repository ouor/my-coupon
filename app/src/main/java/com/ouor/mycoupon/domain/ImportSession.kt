package com.ouor.mycoupon.domain

import java.io.File
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import android.net.Uri
import com.ouor.mycoupon.data.Duplicate
import android.util.Log
import com.ouor.mycoupon.data.CodeSource
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.ImageStore
import com.ouor.mycoupon.data.ScanResult
import com.ouor.mycoupon.data.StoredImage
import com.ouor.mycoupon.di.AppScope
import com.ouor.mycoupon.data.MediaLink
import com.ouor.mycoupon.ocr.CouponRecognizer
import com.ouor.mycoupon.ocr.Recognition
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.ocr.core.Extraction
import com.ouor.mycoupon.ocr.core.CodeSource as OcrCodeSource
import com.ouor.mycoupon.ocr.core.CouponType as OcrCouponType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Where an import batch came from (S4 differs slightly for F9). */
enum class ImportOrigin { PICKER, SHARE, GALLERY_SCAN }

enum class DraftStage { READING, DONE, FAILED }


/**
 * One S4 card. [coupon] is the editable result (id = 0 until registered); UI edits go through
 * [ImportSession.edit]. Field review keys follow porting-spec (brand, productName, faceValue, code, expiryDate, sender).
 */
@Parcelize // kept in the S4 SavedState (code review A8)
data class Draft(
    val key: Int,
    val stage: DraftStage = DraftStage.READING,
    /** MediaStore uri when known (picker / gallery scan); used for F10 and F9 re-suggestion */
    val sourceUri: String? = null,
    val mediaId: Long? = null,
    val coupon: Coupon? = null,
    /** extract's coupon decision; false = "쿠폰이 아닌 것 같아요" */
    val isCoupon: Boolean = false,
    val duplicate: Duplicate? = null,
    val checked: Boolean = false,
    /** per-image timings for the benchmark / debug screen */
    val totalMs: Long = 0,
) : Parcelable

data class ImportState(
    val origin: ImportOrigin = ImportOrigin.PICKER,
    val drafts: List<Draft> = emptyList(),
    val running: Boolean = false,
    /** shared images beyond [MAX_IMAGES] were dropped (S4 Snackbar) */
    val truncated: Boolean = false,
) {
    val done get() = drafts.count { it.stage != DraftStage.READING }
    val total get() = drafts.size
    val checkedCount get() = drafts.count { it.checked && it.stage != DraftStage.READING }
}

/**
 * S3/S4 batch (F1-F4): reads images one by one, publishes each result as soon as it is ready,
 * and registers the checked ones. Lives in the app scope so leaving S4 for S5 keeps the batch.
 */
@Singleton
class ImportSession @Inject constructor(
    private val recognizer: CouponRecognizer,
    private val images: ImageStore,
    private val repo: CouponRepository,
    private val registrar: CouponRegistrar,
    private val media: MediaLink,
    @AppScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()
    private var job: Job? = null
    private val registering = Mutex()

    /** Input image: content uri (+ MediaStore id when it is a gallery image). */
    data class Source(val uri: Uri, val mediaUri: String? = null, val mediaId: Long? = null)

    /** Starts a new batch, discarding any unregistered previous one. */
    fun start(sources: List<Source>, origin: ImportOrigin) {
        clear()
        val list = sources.take(MAX_IMAGES)
        _state.value = ImportState(origin, list.indices.map { Draft(it, sourceUri = list[it].mediaUri, mediaId = list[it].mediaId) }, true, sources.size > MAX_IMAGES)
        job = scope.launch {
            for ((i, s) in list.withIndex()) {
                val earlier = _state.value.drafts.filter { it.key < i }.mapNotNull { it.coupon }
                val d = readDraft(i, s, earlier)
                _state.update { st -> st.copy(drafts = st.drafts.map { if (it.key == i) d else it }) }
            }
            _state.update { it.copy(running = false) }
        }
    }

    /**
     * Code review A8: after the process died, S4's SavedState hands the finished drafts back. Only when no batch is open;
     * drafts whose image copy is gone are dropped. Returns how many were restored.
     */
    fun restore(origin: ImportOrigin, drafts: List<Draft>): Int {
        if (_state.value.drafts.isNotEmpty()) return 0
        val ok = drafts.filter { d -> d.stage != DraftStage.READING && d.coupon?.let { File(it.imagePath).exists() } != false }
        _state.value = ImportState(origin, ok, false)
        return ok.size
    }

    /** Image copies of the open batch, listed for [com.ouor.mycoupon.AppData.cleanUp] so a restored batch keeps its files (A8). Paths only, no coupon data. */
    private val pending = File(images.imageDir.parentFile, "import_batch_paths")

    init {
        scope.launch {
            _state.collect { st ->
                val paths = st.drafts.mapNotNull { it.coupon }.flatMap { listOf(it.imagePath, it.thumbPath) }
                runCatching { if (paths.isEmpty()) pending.delete() else pending.writeText(paths.joinToString("\n")) }
            }
        }
    }

    /** Paths [pending] lists when it was written within [maxAgeMs]; an older list is dropped (the batch was never reopened). */
    fun pendingPaths(maxAgeMs: Long = 24 * 3600_000L): Set<String> =
        if (pending.exists() && System.currentTimeMillis() - pending.lastModified() < maxAgeMs) pending.readLines().toSet()
        else { pending.delete(); emptySet() }

    /** Adds already-recognised gallery results (F9) as a DONE batch. */
    fun startWith(drafts: List<Draft>, origin: ImportOrigin) {
        clear()
        _state.value = ImportState(origin, drafts.mapIndexed { i, d -> d.copy(key = i) }, false)
    }

    /** [그만 읽기]: keep what is read, drop the rest. */
    fun stopReading() {
        job?.cancel()
        _state.update { st -> st.copy(running = false, drafts = st.drafts.filter { it.stage != DraftStage.READING }) }
    }

    fun setChecked(key: Int, checked: Boolean) = _state.update { st ->
        st.copy(drafts = st.drafts.map { if (it.key == key) it.copy(checked = checked) else it })
    }

    /** Review U2 "쿠폰 아님 · 빼기": the user says it is not a coupon (receipt etc.); it joins the folded not-a-coupon group. */
    fun exclude(key: Int) = _state.update { st ->
        st.copy(drafts = st.drafts.map { if (it.key == key) it.copy(isCoupon = false, checked = false) else it })
    }

    /**
     * S5 save for a draft (also "직접 입력" on a failed card: its coupon holds the image only). Checks the card.
     * A changed number re-runs the F4 duplicate check (the card stays checked: the user chose to save it).
     */
    fun edit(key: Int, coupon: Coupon) {
        val c = coupon.copy(codeKey = Coupon.codeKey(coupon.code))
        val before = _state.value.drafts.firstOrNull { it.key == key }?.coupon?.codeKey
        _state.update { st ->
            st.copy(drafts = st.drafts.map { if (it.key == key) it.copy(coupon = c, stage = DraftStage.DONE, checked = true) else it })
        }
        if (c.codeKey != before) scope.launch {
            val earlier = _state.value.drafts.filter { it.key < key }.mapNotNull { it.coupon }
            val dup = duplicateOf(c, earlier)
            _state.update { st -> st.copy(drafts = st.drafts.map { if (it.key == key && it.coupon == c) it.copy(duplicate = dup) else it }) }
        }
    }

    /**
     * Registers checked drafts; returns the new coupon ids. Unchecked copies are deleted.
     * Calls are serialised and the batch is cleared inside the lock, so a second tap registers nothing (C10).
     */
    suspend fun register(): List<Long> = withContext(NonCancellable) { registering.withLock { registerLocked() } }

    private suspend fun registerLocked(): List<Long> {
        job?.cancel()
        val st = _state.value
        val chosen = st.drafts.filter { it.checked && it.stage != DraftStage.READING && it.coupon != null }
        val ids = registrar.register(chosen.map { it.coupon!! })
        for (d in st.drafts) {
            if (d in chosen) d.mediaId?.let { repo.markScan(it, ScanResult.REGISTERED) }
            else {
                d.coupon?.let { images.delete(it.imagePath, it.thumbPath) }
                if (st.origin == ImportOrigin.GALLERY_SCAN) d.mediaId?.let { repo.markScan(it, ScanResult.DISMISSED) }
            }
        }
        _state.value = ImportState()
        scope.launch { recognizer.release() } // O-10: the sessions (~70 MB) are not kept once the batch is over; the next batch reloads them (~200 ms)
        return ids
    }

    /** [닫기] → [버리기] */
    fun discard() {
        clear()
        scope.launch { recognizer.release() }
    }

    private fun clear() {
        job?.cancel()
        _state.value.drafts.forEach { d -> d.coupon?.let { images.delete(it.imagePath, it.thumbPath) } }
        _state.value = ImportState()
    }

    /** Copies and recognises one image; [earlier] = coupons met before it in the same batch (F4 AC3). */
    suspend fun readDraft(key: Int, s: Source, earlier: List<Coupon> = emptyList()): Draft {
        val bytes = images.read(s.uri) ?: return Draft(key, sourceUri = s.mediaUri, mediaId = s.mediaId, stage = DraftStage.FAILED)
        val stored = images.save(bytes)
        val thumb = images.saveThumb(bytes)
        try {
            return readStored(key, s, earlier, bytes, stored, thumb)
        } catch (e: CancellationException) {
            images.delete(stored.path, thumb) // A4: a cancelled read leaves no orphan copy
            throw e
        }
    }

    private suspend fun readStored(key: Int, s: Source, earlier: List<Coupon>, bytes: ByteArray, stored: StoredImage, thumb: String): Draft {
        // picker / share: find the gallery original while the uri grant is valid (F10 trash, F9 skip; QA R1-06)
        val link = if (s.mediaId == null) runCatching { media.resolve(s.uri, stored.hash) }.getOrNull() else null
        val src = s.mediaUri ?: link?.uri?.toString()
        val base = Draft(key, sourceUri = src, mediaId = s.mediaId ?: link?.id)
        val r = try {
            recognizer.recognize(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // O-12: class name only (never the message: it may carry text); the card still shows "읽지 못했어요"
            Log.w("ImportSession", "recognition failed: ${e.javaClass.name}")
            null
        }
        val coupon = if (r == null) {
            Coupon(imagePath = stored.path, thumbPath = thumb, imageHash = stored.hash, sourceUri = src)
        } else {
            toCoupon(r, stored.path, thumb, stored.hash, src)
        }
        val dup = duplicateOf(coupon, earlier)
        val isCoupon = r?.extraction?.isCoupon == true
        return base.copy(
            stage = if (r == null) DraftStage.FAILED else DraftStage.DONE,
            coupon = coupon,
            isCoupon = isCoupon,
            duplicate = dup,
            // spec F3/F4: not-a-coupon and duplicates start unchecked (forced registration stays possible); QA R1-16: so do expired ones
            checked = isCoupon && dup == null && coupon.expiryDate?.isBefore(java.time.LocalDate.now()) != true,
            totalMs = r?.timings?.totalMs ?: 0,
        )
    }

    /** Deletes the image copies of a draft that will not be registered. */
    fun dropFiles(d: Draft) {
        d.coupon?.let { images.delete(it.imagePath, it.thumbPath) }
    }

    private suspend fun duplicateOf(c: Coupon, earlier: List<Coupon>): Duplicate? {
        repo.findDuplicate(c)?.let { return it }
        val same = earlier.any { o -> if (c.codeKey != null) o.codeKey == c.codeKey else o.imageHash == c.imageHash }
        return if (same) Duplicate.IN_BATCH else null
    }

    companion object {
        /** F1: up to 30 images at once */
        const val MAX_IMAGES = 30

        fun toCoupon(r: Recognition, path: String, thumb: String, hash: String, sourceUri: String?): Coupon {
            val x: Extraction = r.extraction
            return Coupon(
                brand = x.brand,
                productName = x.productName,
                type = when (x.type) { OcrCouponType.EXCHANGE -> CouponType.EXCHANGE; OcrCouponType.AMOUNT -> CouponType.AMOUNT },
                faceValue = x.faceValue,
                balance = if (x.type == OcrCouponType.AMOUNT) x.faceValue else null,
                code = x.code,
                codeKey = Coupon.codeKey(x.code),
                codeFormat = x.codeFormat,
                codeSource = when (x.codeSource) { OcrCodeSource.DECODED -> CodeSource.DECODED; OcrCodeSource.OCR -> CodeSource.OCR; null -> null }, // A-09: explicit, not by name
                expiryDate = x.expiryDate,
                expiryCandidates = x.expiryCandidates,
                issuer = x.issuer,
                notes = x.notes,
                memo = x.sender,
                needsReview = x.needsReview.entries.associate { (k, v) -> k.key to v.map { it.name } },
                fieldBoxes = x.fieldBoxes.entries.associate { (k, b) -> k.key to listOf(b.x0, b.y0, b.x1, b.y1) },
                imagePath = path,
                thumbPath = thumb,
                imageWidth = r.width,
                imageHeight = r.height,
                imageHash = hash,
                sourceUri = sourceUri,
            )
        }
    }
}
