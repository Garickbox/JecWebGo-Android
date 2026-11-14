package com.garickbox.jecwebgo.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.io.FileOutputStream
import java.nio.channels.FileChannel

@RequiresApi(Build.VERSION_CODES.Q)
object MediaStoreWriter {
    class ChannelHandle(
        val channel: FileChannel,
        private val fos: FileOutputStream,
        private val pfd: ParcelFileDescriptor
    ) : Closeable {
        override fun close() {
            runCatching { channel.close() }
            runCatching { fos.close() }
            runCatching { pfd.close() }
        }
    }

    fun createFile(ctx: Context, displayName: String, parentRelPath: String = ""): Uri? {
        val relPath = if (parentRelPath.isBlank()) {
            Environment.DIRECTORY_DOWNLOADS + "/JecWeb"
        } else {
            Environment.DIRECTORY_DOWNLOADS + "/JecWeb/" + parentRelPath.trim('/').trim()
        }
        // Не даём Android добавить .mp4 к имени с .avi/.mkv
        // MediaStore сам не добавляет суффикс если MIME соответствует расширению
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            val ext = displayName.substringAfterLast(".", "").lowercase()
            val mime = when (ext) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "wmv" -> "video/x-ms-wmv"
                "webm" -> "video/webm"
                "flv" -> "video/x-flv"
                "ts", "m2ts" -> "video/mp2t"
                else -> "video/mp4"
            }
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, relPath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return runCatching {
            ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }

    fun openChannel(ctx: Context, uri: Uri): ChannelHandle? {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "rw") ?: return null
        val fos = FileOutputStream(pfd.fileDescriptor)
        return ChannelHandle(fos.channel, fos, pfd)
    }

    fun getFileSize(ctx: Context, uri: Uri): Long {
        return try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun exists(ctx: Context, uri: Uri): Boolean {
        return try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) { false }
    }

    fun finalizeFile(ctx: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        runCatching { ctx.contentResolver.update(uri, values, null, null) }
    }

    fun delete(ctx: Context, uri: Uri) {
        runCatching { ctx.contentResolver.delete(uri, null, null) }
    }

    /** Найти файл в MediaStore Downloads/JecWeb[/parentRel] по DISPLAY_NAME.
     *  Возвращает uri первого найденного (включая IS_PENDING). */
    fun findByName(ctx: Context, displayName: String, parentRelPath: String = ""): Uri? {
        return try {
            val rel = if (parentRelPath.isBlank()) {
                Environment.DIRECTORY_DOWNLOADS + "/JecWeb"
            } else {
                Environment.DIRECTORY_DOWNLOADS + "/JecWeb/" + parentRelPath.trim('/').trim()
            }
            val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
            val args = arrayOf(displayName, rel)
            val cursor = ctx.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(0)
                    android.util.Log.d("JecWeb", "findByName: $displayName in $rel -> id=$id")
                    return android.content.ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                    )
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "findByName fail: ${e.message}")
            null
        }
    }

    /** Снять IS_PENDING чтобы файл не удалялся системой при паузе/убийстве процесса */
    fun unfinalizeFile(ctx: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 1) }
        runCatching { ctx.contentResolver.update(uri, values, null, null) }
    }
}
