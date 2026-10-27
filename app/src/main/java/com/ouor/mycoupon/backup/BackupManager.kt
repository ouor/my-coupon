package com.ouor.mycoupon.backup

import android.content.Context
import android.net.Uri
import com.ouor.mycoupon.data.AppSettings
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.CouponRepository
import com.ouor.mycoupon.data.ImageStore
import com.ouor.mycoupon.data.Settings
import com.ouor.mycoupon.data.UsageLog
import com.ouor.mycoupon.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Plaintext inside the encrypted file: a ZIP with manifest.json + images/<n>. */
@Serializable
data class BackupManifest(
    val formatVersion: Int = FORMAT_VERSION,
    val createdAt: Long,
    val settings: AppSettings,
    val coupons: List<BackupCoupon>,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

/** [coupon].imagePath holds the ZIP entry name; ids are re-assigned on import. */
@Serializable
data class BackupCoupon(val coupon: Coupon, val usage: List<UsageLog>)

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
        try {
            val coupons = repo.all()
            val usage = repo.allUsage().groupBy { it.couponId }
            ZipOutputStream(zip.outputStream().buffered()).use { z ->
                val entries = coupons.mapIndexed { i, c ->
                    val name = "images/$i"
                    z.putNextEntry(ZipEntry(name))
                    File(c.imagePath).inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                    BackupCoupon(c.copy(imagePath = name, thumbPath = "", sourceUri = null), usage[c.id].orEmpty())
                }
                z.putNextEntry(ZipEntry(MANIFEST))
                z.write(json.encodeToString(BackupManifest.serializer(), BackupManifest(createdAt = System.currentTimeMillis(), settings = settings.app, coupons = entries)).toByteArray())
                z.closeEntry()
            }
            val out = context.contentResolver.openOutputStream(target, "wt") ?: error("cannot open target")
            out.use { o -> zip.inputStream().buffered().use { BackupCrypto.encrypt(it, o, password) } }
            settings.lastBackupAt = System.currentTimeMillis()
            coupons.size
        } finally {
            zip.delete()
        }
    }

    /** Decrypts and parses; throws [BackupAuthException] (wrong password / damaged) or [BackupFormatException]. */
    suspend fun open(source: Uri, password: CharArray): BackupPreview = withContext(Dispatchers.IO) {
        val zip = File.createTempFile("import", ".zip", tmpDir)
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw BackupFormatException("cannot open file")
            input.use { i -> zip.outputStream().buffered().use { BackupCrypto.decrypt(i.buffered(), it, password) } }
            val manifest = ZipFile(zip).use { z ->
                val e = z.getEntry(MANIFEST) ?: throw BackupFormatException("no manifest")
                json.decodeFromString(BackupManifest.serializer(), z.getInputStream(e).readBytes().decodeToString())
            }
            if (manifest.formatVersion > BackupManifest.FORMAT_VERSION) throw BackupFormatException("newer format")
            val skipped = manifest.coupons.count { repo.findDuplicate(it.coupon.copy(codeKey = Coupon.codeKey(it.coupon.code))) != null }
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
                val todo = p.manifest.coupons.filter { bc ->
                    val c = bc.coupon.copy(codeKey = Coupon.codeKey(bc.coupon.code))
                    val k = c.codeKey ?: "img:" + c.imageHash
                    repo.findDuplicate(c) == null && seenKeys.add(k)
                }
                val prepared = todo.map { bc ->
                    val bytes = z.getInputStream(z.getEntry(bc.coupon.imagePath) ?: throw BackupFormatException("missing image")).readBytes()
                    val stored = images.save(bytes)
                    copied += stored.path
                    val thumb = images.saveThumb(bytes)
                    copied += thumb
                    bc.coupon.copy(id = 0, codeKey = Coupon.codeKey(bc.coupon.code), imagePath = stored.path, thumbPath = thumb, sourceUri = null) to bc.usage
                }
                repo.transaction {
                    for ((c, logs) in prepared) {
                        val id = repo.insertAll(listOf(c)).single()
                        logs.forEach { repo.addUsage(it.copy(id = 0, couponId = id)) }
                    }
                }
                prepared.size
            }
            settings.app = p.manifest.settings
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

    private companion object {
        const val MANIFEST = "manifest.json"
    }
}
