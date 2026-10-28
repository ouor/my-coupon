package com.ouor.mycoupon.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.ouor.mycoupon.image.ImageCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A copied original (F1 AC3: survives deletion in the gallery). */
data class StoredImage(val path: String, val hash: String, val bytes: ByteArray)

/**
 * App-internal image copies (spec §7.1-4: internal storage only). Originals are kept byte for byte;
 * thumbnails are small JPEGs for the list (F5 AC2).
 */
@Singleton
class ImageStore @Inject constructor(@ApplicationContext private val context: Context) {
    val imageDir = File(context.filesDir, "images").apply { mkdirs() }
    val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }

    /**
     * Reads [uri] fully; null when unreadable, larger than [ImageCodec.MAX_BYTES], or not another app's content uri
     * (security review S-09: a shared file:// or our own provider uri must not copy app-private files).
     */
    fun read(uri: Uri): ByteArray? = runCatching {
        if (uri.scheme != "content" || uri.authority.orEmpty().startsWith(context.packageName)) return null
        context.contentResolver.openInputStream(uri)?.use { ImageCodec.readCapped(it, ImageCodec.MAX_BYTES) }
    }.getOrNull()

    /**
     * C2-24: a new empty file for the system camera app plus its FileProvider uri (pass it to
     * ActivityResultContracts.TakePicture). The photo never reaches the gallery; [readCapture] reads and deletes it.
     */
    fun newCapture(): Pair<File, Uri> {
        val dir = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // one capture at a time; an abandoned one goes now
        val f = File(dir, UUID.randomUUID().toString() + ".jpg")
        return f to FileProvider.getUriForFile(context, context.packageName + ".files", f)
    }

    /** Bytes of a [newCapture] file (null if the camera wrote nothing or too much); the file is deleted either way. */
    fun readCapture(file: File): ByteArray? = try {
        if (file.parentFile?.name != CAPTURE_DIR || !file.isFile || file.length() == 0L) null
        else file.inputStream().use { ImageCodec.readCapped(it, ImageCodec.MAX_BYTES) }
    } catch (_: java.io.IOException) {
        null
    } finally {
        file.delete()
    }

    /** Copies bytes into the image folder. */
    fun save(bytes: ByteArray): StoredImage {
        val f = File(imageDir, UUID.randomUUID().toString())
        f.writeBytes(bytes)
        return StoredImage(f.absolutePath, ImageCodec.sha256(bytes), bytes)
    }

    /** Writes a list thumbnail (long side [THUMB_PX]) and returns its path. */
    fun saveThumb(bytes: ByteArray): String {
        val f = File(thumbDir, UUID.randomUUID().toString() + ".jpg")
        ImageCodec.decode(bytes, THUMB_PX)?.let { b ->
            f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            b.recycle()
        }
        return f.absolutePath
    }

    fun delete(vararg paths: String?) = paths.filterNotNull().forEach { File(it).delete() }

    /** DB file name (schema v3) -> absolute path; an empty or already absolute value is kept. */
    fun image(name: String) = resolve(imageDir, name)
    fun thumb(name: String) = resolve(thumbDir, name)
    private fun resolve(dir: File, name: String) = if (name.isEmpty() || name.contains('/')) name else File(dir, name).path

    /**
     * Deletes copies whose file name is not in [keep] and last modified before [before] (code review A4). Compared by name,
     * not path (D-18); when there are copies to keep but none of them is on disk, the names are not ours and nothing is
     * deleted. Returns how many.
     */
    fun removeOrphans(keep: Set<String>, before: Long): Int {
        val names = keep.map { File(it).name }.filter { it.isNotEmpty() }.toSet()
        val files = listOf(imageDir, thumbDir).flatMap { it.listFiles().orEmpty().asList() }
        if (names.isNotEmpty() && files.isNotEmpty() && files.none { it.name in names }) return 0
        return files.filter { it.name !in names && it.lastModified() < before }.count { it.delete() }
    }

    fun wipe() {
        imageDir.deleteRecursively(); imageDir.mkdirs()
        thumbDir.deleteRecursively(); thumbDir.mkdirs()
    }

    companion object {
        const val THUMB_PX = 320
        const val CAPTURE_DIR = "capture"
    }
}
