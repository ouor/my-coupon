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

    /** Writes an encrypted backup of every coupon (fields, image copies, history, per-coupon reminders) and settings. Returns the coupon count. */
    suspend fun export(target: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val zip = File.createTempFile("export", ".zip", tmpDir)
        val job = coroutineContext.job
        try {
            val coupons = repo.all()
            val usage = repo.allUsage().groupBy { it.couponId }
            ZipOutputStream(zip.outputStream().buffered()).use { z ->
                val entries = coupons.mapIndexed { i, c ->
                    job.ensureActive()
                    val name = "images/$i"
                    z.putNextEntry(ZipEntry(name))
                    File(c.imagePath).inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                    BackupCoupon(BackupFormat.toFile(c, name), usage[c.id].orEmpty().map(BackupFormat::toFile))
                }
                z.putNextEntry(ZipEntry(MANIFEST))
                z.write(json.encodeToString(BackupManifest.serializer(), BackupManifest(createdAt = System.currentTimeMillis(), settings = BackupFormat.toFile(settings.app), coupons = entries)).toByteArray())
                z.closeEntry()
            }
            job.ensureActive()
            val out = context.contentResolver.openOutputStream(target, "wt") ?: error("cannot open target")
            out.use { o -> zip.inputStream().buffered().use { BackupCrypto.encrypt(it, o, password, checkpoint = job::ensureActive) } }
            settings.lastBackupAt = System.currentTimeMillis()
            coupons.size
        } catch (e: Exception) {
            // [그만두기] or a write error: never leave a half-written backup behind (QA R1-04)
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) } // the empty file SAF created for this export
            throw e
        } finally {
            zip.delete()
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
        try {
            val count = ZipFile(p.zip).use { z ->
                // decide skips once, also within the file itself
                val seenKeys = HashSet<String>()
                val prepared = ArrayList<Pair<Coupon, List<UsageV1>>>()
                for (bc in p.manifest.coupons) {
                    val bytes = read(z, bc.coupon.imagePath, MAX_IMAGE)
                    // S-04: the hash comes from the bytes, never from the file's claim (duplicate check)
                    val c = BackupFormat.toEntity(bc.coupon, "", "", ImageCodec.sha256(bytes))
                    if (repo.findDuplicate(c) != null || !seenKeys.add(c.codeKey ?: ("img:" + c.imageHash))) continue
                    val stored = images.save(bytes)
                    copied += stored.path
                    val thumb = images.saveThumb(bytes)
                    copied += thumb
                    prepared += c.copy(imagePath = stored.path, thumbPath = thumb) to bc.usage
                }
                repo.transaction {
                    for ((c, logs) in prepared) {
                        val id = repo.insertAll(listOf(c)).single()
                        logs.forEach { repo.addUsage(BackupFormat.toEntity(it, id)) }
                    }
                }
                prepared.size
            }
            settings.app = BackupFormat.toSettings(p.manifest.settings)
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
