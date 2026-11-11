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

    fun createFile(ctx: Context, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/JecWeb")
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

    fun finalizeFile(ctx: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        runCatching { ctx.contentResolver.update(uri, values, null, null) }
    }

    fun delete(ctx: Context, uri: Uri) {
        runCatching { ctx.contentResolver.delete(uri, null, null) }
    }
}
