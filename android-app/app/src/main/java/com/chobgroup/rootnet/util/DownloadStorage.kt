package com.chobgroup.rootnet.util

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Where downloaded VPN files (.npvt / .sip / ...) are saved so the user can
 * actually reach them — the public **Downloads** folder, not just app-private
 * storage.
 *
 *  - **API 29+** → `MediaStore.Downloads` (no permission needed; files appear
 *    in the system Downloads app / file managers).
 *  - **API 23–28** → classic public Downloads dir, which needs the
 *    `WRITE_EXTERNAL_STORAGE` runtime permission ([needsStoragePermission]).
 *    When it's missing the caller can request it and retry.
 *
 * Every method returns null instead of throwing, so a save that can't happen
 * falls back to app-private storage without breaking the download.
 */
object DownloadStorage {

    /** True when public saving on this device needs a runtime permission. */
    fun needsStoragePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT in 23..28 &&
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    /**
     * Writes [bytes] to the public Downloads location. Returns a location key
     * (`content://…` on API 29+, an absolute path on 23–28) that
     * [readBytesFromLocation] understands, or null when the save failed / the
     * permission isn't granted.
     */
    fun saveToPublicDownloads(context: Context, filename: String, bytes: ByteArray): String? =
        if (Build.VERSION.SDK_INT >= 29) saveViaMediaStore(context, filename, bytes)
        else if (!needsStoragePermission(context)) saveToPublicDir(filename, bytes)
        else null

    /** Reads a previously saved download back, by location key. */
    fun readBytesFromLocation(context: Context, location: String): ByteArray? = runCatching {
        if (location.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(location))?.use { it.readBytes() }
        } else {
            File(location).takeIf { it.exists() }?.readBytes()
        }
    }.getOrNull()

    /** Safe filename for the filesystem (no separators / control chars). */
    fun sanitizeFilename(filename: String): String =
        filename.replace(Regex("[^A-Za-z0-9._\\-]"), "_")

    private fun guessMime(filename: String): String = when (filename.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"
        "sip", "txt", "conf" -> "text/plain"
        else -> "application/octet-stream"
    }

    @android.annotation.SuppressLint("NewApi")
    private fun saveViaMediaStore(context: Context, filename: String, bytes: ByteArray): String? =
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, guessMime(filename))
                put(MediaStore.Downloads.SIZE, bytes.size)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            val wrote = resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
            if (!wrote) {
                resolver.delete(uri, null, null)
                return@runCatching null
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        }.getOrNull()

    @Suppress("DEPRECATION") // public dir is the API 23-28 path (guarded above)
    private fun saveToPublicDir(filename: String, bytes: ByteArray): String? = runCatching {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, sanitizeFilename(filename))
        target.writeBytes(bytes)
        target.absolutePath
    }.getOrNull()
}
