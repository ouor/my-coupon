package com.ouor.mycoupon.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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

    fun wipe() {
        imageDir.deleteRecursively(); imageDir.mkdirs()
        thumbDir.deleteRecursively(); thumbDir.mkdirs()
    }

    companion object {
        const val THUMB_PX = 320
    }
}
