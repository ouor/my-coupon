package com.ouor.mycoupon.backup

import android.content.Context
import com.ouor.mycoupon.image.ImageCodec
import android.net.Uri
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.ImageStore
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** S14 preview: "쿠폰 N개, 그중 이미 있는 쿠폰 M개는 건너뜀". */
data class BackupPreview(val total: Int, val skipped: Int, internal val zip: File, internal val manifest: BackupManifest)

/**
 * F15. Files go only where the user picks with SAF (CreateDocument / OpenDocument). Import is all-or-nothing:
 * the file is decrypted and parsed fully into a temp file first, images are copied, then one DB transaction
 * inserts; any failure removes the copied images and leaves the data as it was.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: CouponRepository,
    private val images: ImageStore,
    private val settings: Settings,
    private val reminders: ReminderScheduler,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val tmpDir get() = File(context.cacheDir, "backup").apply { mkdirs() }

    /** Coupons of the last [export] whose image copy was missing; they were backed up without an image (D-05). */
    @Volatile var lastExportMissingImages = 0
        private set

    /** Writes an encrypted backup of every coupon (fields, image copies, history, per-coupon reminders) and settings. Returns the coupon count. */
    suspend fun export(target: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val zip = File.createTempFile("export", ".zip", tmpDir)
        val enc = File.createTempFile("export", ".enc", tmpDir)
        val job = coroutineContext.job
        // D-04: a file the user picked to replace is deleted on failure only if it was empty, i.e. SAF made it for this export
        val wasEmpty = runCatching {
            context.contentResolver.query(target, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> c.moveToFirst() && c.getLong(0) == 0L }
        }.getOrNull() == true
        try {
            // D-14: coupons and history from one snapshot
            val (coupons, usage) = repo.transaction { repo.all() to repo.allUsage().groupBy { it.couponId } }
            var missing = 0
            ZipOutputStream(zip.outputStream().buffered()).use { z ->
                val entries = coupons.mapIndexed { i, c ->
                    job.ensureActive()
                    val img = File(c.imagePath)
                    // D-05: a lost image copy must not stop the backup of every coupon; the fields still go
                    val name = if (img.isFile) "images/$i" else "".also { missing++ }
                    if (name.isNotEmpty()) {
                        z.putNextEntry(ZipEntry(name))
                        img.inputStream().use { it.copyTo(z) }
                        z.closeEntry()
                    }
                    BackupCoupon(BackupFormat.toFile(c, name), usage[c.id].orEmpty().map(BackupFormat::toFile))
                }
                z.putNextEntry(ZipEntry(MANIFEST))
                z.write(json.encodeToString(BackupManifest.serializer(), BackupManifest(createdAt = System.currentTimeMillis(), settings = BackupFormat.toFile(settings.app), coupons = entries)).toByteArray())
                z.closeEntry()
            }
            job.ensureActive()
            // D-04: the whole encrypted file is made first; the target is touched only once nothing can fail but the copy
            enc.outputStream().buffered().use { o -> zip.inputStream().buffered().use { BackupCrypto.encrypt(it, o, password, checkpoint = job::ensureActive) } }
            job.ensureActive()
            val out = context.contentResolver.openOutputStream(target, "wt") ?: error("cannot open target")
            out.use { o -> enc.inputStream().use { it.copyTo(o) } }
            settings.lastBackupAt = System.currentTimeMillis()
            lastExportMissingImages = missing
            coupons.size
        } catch (e: Exception) {
            // [그만두기] or a write error: no half-written new file (QA R1-04); a file that held an older backup stays (D-04)
            if (wasEmpty) runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
            throw e
        } finally {
            zip.delete()
            enc.delete()
        }
    }

    /** Decrypts and parses; throws [BackupAuthException] (wrong password / damaged) or [BackupFormatException]. */
    suspend fun open(source: Uri, password: CharArray): BackupPreview = withContext(Dispatchers.IO) {
        val zip = File.createTempFile("import", ".zip", tmpDir)
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw BackupFormatException("cannot open file")
            val job = coroutineContext.job
            input.use { i -> zip.outputStream().buffered().use { BackupCrypto.decrypt(i.buffered(), it, password, checkpoint = job::ensureActive) } }
            val manifest = ZipFile(zip).use { z ->
                json.decodeFromString(BackupManifest.serializer(), read(z, MANIFEST, MAX_MANIFEST).decodeToString())
            }
            if (manifest.formatVersion > BackupManifest.FORMAT_VERSION) throw BackupFormatException("newer format")
            if (manifest.coupons.size > MAX_COUPONS) throw BackupFormatException("too many coupons")
            // validates every coupon now (unknown enum, bad date): the preview never shows a file that import would refuse
            val skipped = manifest.coupons.count { repo.findDuplicate(BackupFormat.toEntity(it.coupon, "", "", it.coupon.imageHash)) != null }
            BackupPreview(manifest.coupons.size, skipped, zip, manifest)
        } catch (e: Exception) {
            zip.delete()
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e is BackupAuthException || e is BackupFormatException) throw e
            throw BackupFormatException("damaged: ${e.javaClass.simpleName}")
        }
    }

    /** Merges the preview into the app (spec F15): same number (or same image without a number) is skipped. Returns imported count. */
    suspend fun import(p: BackupPreview): Int = withContext(Dispatchers.IO + NonCancellable) { // a cancel after the commit must not delete the copied images
        val copied = ArrayList<String>()
        var wasEmpty = false
        try {
            val count = ZipFile(p.zip).use { z ->
                // decide skips once, also within the file itself
                val seenKeys = HashSet<String>()
                val prepared = ArrayList<Pair<Coupon, List<UsageV1>>>()
                for (bc in p.manifest.coupons) {
                    // D-05: a coupon saved without its image (the copy was lost) comes back with fields only
                    val bytes = if (bc.coupon.imagePath.isEmpty()) null else read(z, bc.coupon.imagePath, MAX_IMAGE)
                    // S-04: the hash comes from the bytes, never from the file's claim (duplicate check)
                    val hash = bytes?.let(ImageCodec::sha256) ?: ("none:" + java.util.UUID.randomUUID())
                    val c = BackupFormat.toEntity(bc.coupon, "", "", hash)
                    if (repo.findDuplicate(c) != null || !seenKeys.add(c.codeKey ?: ("img:" + c.imageHash))) continue
                    if (bytes == null) { prepared += c to bc.usage; continue }
                    val stored = images.save(bytes)
                    copied += stored.path
                    val thumb = images.saveThumb(bytes)
                    copied += thumb
                    prepared += c.copy(imagePath = stored.path, thumbPath = thumb) to bc.usage
                }
                wasEmpty = repo.all().isEmpty()
                repo.transaction {
                    for ((c, logs) in prepared) {
                        val id = repo.insertAll(listOf(c)).single()
                        logs.forEach { repo.addUsage(BackupFormat.toEntity(it, id)) }
                    }
                }
                prepared.size
            }
            // D-15: a merge into a phone already in use keeps its reminder settings; a new phone takes the file's
            if (wasEmpty) settings.app = BackupFormat.toSettings(p.manifest.settings)
            reminders.reschedule()
            count
        } catch (e: Exception) {
            images.delete(*copied.toTypedArray())
            throw e
        } finally {
            p.zip.delete()
        }
    }

    fun cancel(p: BackupPreview) {
        p.zip.delete()
    }

    /**
     * Plaintext export/import ZIPs left by a killed process (S-03/A6). Called at app start and by [모든 데이터 삭제];
     * a preview never outlives its process, so cleaning at start cannot hit a live one.
     */
    fun clearTemp() {
        File(context.cacheDir, "backup").deleteRecursively()
    }

    /** One ZIP entry with a cap on what is actually inflated (S-10: a deflate bomb must not OOM). */
    private fun read(z: ZipFile, name: String, max: Int): ByteArray {
        val e = z.getEntry(name) ?: throw BackupFormatException("missing entry")
        if (e.size > max) throw BackupFormatException("entry too large")
        return z.getInputStream(e).use { ImageCodec.readCapped(it, max) } ?: throw BackupFormatException("entry too large")
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val MAX_MANIFEST = 16 shl 20
        const val MAX_IMAGE = ImageCodec.MAX_BYTES
        const val MAX_COUPONS = 10_000
    }
}
