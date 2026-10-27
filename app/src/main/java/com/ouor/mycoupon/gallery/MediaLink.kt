package com.ouor.mycoupon.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.ouor.mycoupon.data.ImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F1/F10/F9 (QA R1-06): finds the MediaStore image behind a photo-picker or share uri, so a coupon added that way
 * keeps `sourceUri` (trash request) and its media id (gallery scan skips it). A persistable picker grant would not
 * help: MediaStore.createTrashRequest only takes MediaStore uris.
 *
 * - MediaStore uri (`content://media/external/images/media/<id>`, common for gallery shares): used as is.
 * - Local photo-picker uri (`content://media/picker…/<user>/com.android.providers.media.photopicker/media/<id>`):
 *   the last segment is the MediaStore id. Cloud picker items have another authority segment and are skipped.
 * - Anything else (other apps' providers): with gallery permission, MediaStore rows with the same name and size.
 * When the MediaStore image is readable its bytes must hash to [hash], so a wrong guess never reaches the trash request.
 */
@Singleton
class MediaLink @Inject constructor(@ApplicationContext private val context: Context) {
    data class Media(val uri: Uri, val id: Long)

    fun resolve(source: Uri, hash: String): Media? {
        if (source.authority == MediaStore.AUTHORITY) {
            val id = idFromMediaUri(source) ?: return null
            val media = Media(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), id)
            return when (sameBytes(media.uri, hash)) {
                false -> null
                else -> media // true, or not readable without gallery permission: trust the id (the system trash dialog shows the photo)
            }
        }
        return byNameAndSize(source, hash)
    }

    private fun byNameAndSize(source: Uri, hash: String): Media? {
        val (name, size) = context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0) && !c.isNull(1)) c.getString(0) to c.getLong(1) else null } ?: return null
        val coll = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            coll, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.SIZE} = ?", arrayOf(name, size.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val uri = ContentUris.withAppendedId(coll, id)
                if (sameBytes(uri, hash) == true) return Media(uri, id)
            }
        }
        return null
    }

    /** null = cannot read it (no gallery permission). */
    private fun sameBytes(uri: Uri, hash: String): Boolean? = try {
        context.contentResolver.openInputStream(uri)?.use { ImageStore.sha256(it.readBytes()) == hash }
    } catch (_: SecurityException) {
        null
    } catch (_: java.io.FileNotFoundException) {
        false
    }

    companion object {
        /** MediaStore id from a MediaStore or local photo-picker uri; null for cloud picker items and other shapes. */
        fun idFromMediaUri(uri: Uri): Long? {
            val s = uri.pathSegments
            val id = s.lastOrNull()?.toLongOrNull() ?: return null
            return when {
                s.first().startsWith("picker") -> id.takeIf { PICKER_LOCAL in s }
                s.size >= 4 && s[1] == "images" && s[2] == "media" -> id
                else -> null
            }
        }

        private const val PICKER_LOCAL = "com.android.providers.media.photopicker"
    }
}
