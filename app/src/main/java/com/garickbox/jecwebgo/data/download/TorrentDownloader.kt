package com.garickbox.jecwebgo.data.download

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.garickbox.jecwebgo.data.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.Q)
object TorrentDownloader {
    private const val CHUNK_SIZE = 512L * 1024       // 512 KB Range
    private const val IO_BUFFER = 64 * 1024          // 64 KB RAM
    private const val MAX_ATTEMPTS = 5

    suspend fun download(
        ctx: Context,
        hash: String,
        fileIndex: Int,
        targetUri: Uri,
        totalBytes: Long,
        onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean = { false }
    ): Boolean = withContext(Dispatchers.IO) {
        val handle = MediaStoreWriter.openChannel(ctx, targetUri) ?: return@withContext false
        val channel = handle.channel
        val url = "http://127.0.0.1:8090/stream?link=$hash&index=$fileIndex&play"
        var downloaded = 0L
        var lastReport = System.currentTimeMillis()
        var lastBytes = 0L

        try {
            android.util.Log.d("JecWeb", "download: total=$totalBytes chunk=$CHUNK_SIZE buf=$IO_BUFFER")

            while (downloaded < totalBytes && !isCancelled()) {
                if (isPaused()) { delay(500); continue }
                val start = downloaded
                val end = min(start + CHUNK_SIZE - 1, totalBytes - 1)
                val expected = (end - start + 1)

                val written = fetchChunk(url, start, end, channel, isCancelled)

                if (written <= 0) {
                    android.util.Log.e("JecWeb", "chunk $start-$end fail after $MAX_ATTEMPTS attempts → abort")
                    handle.close()
                    MediaStoreWriter.delete(ctx, targetUri)
                    return@withContext false
                }

                downloaded += written

                if (written < expected) {
                    android.util.Log.w("JecWeb", "partial $start-$end got $written/$expected")
                    delay(500)
                }

                val now = System.currentTimeMillis()
                if (now - lastReport >= 500L) {
                    val speed = if (now > lastReport) ((downloaded - lastBytes) * 1000L) / (now - lastReport) else 0L
                    onProgress(downloaded, totalBytes, speed)
                    lastReport = now
                    lastBytes = downloaded
                }
            }

            if (downloaded < totalBytes) {
                android.util.Log.e("JecWeb", "incomplete $downloaded/$totalBytes")
                handle.close()
                MediaStoreWriter.delete(ctx, targetUri)
                return@withContext false
            }

            onProgress(downloaded, totalBytes, 0L)
            handle.close()
            MediaStoreWriter.finalizeFile(ctx, targetUri)
            android.util.Log.d("JecWeb", "download complete: $downloaded/$totalBytes")
            true
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "download error: ${e.message}")
            runCatching { handle.close() }
            runCatching { MediaStoreWriter.delete(ctx, targetUri) }
            false
        }
    }

    private suspend fun fetchChunk(
        url: String,
        start: Long,
        end: Long,
        channel: FileChannel,
        isCancelled: () -> Boolean
    ): Long {
        for (attempt in 1..MAX_ATTEMPTS) {
            if (isCancelled()) return 0L
            try {
                val req = Request.Builder().url(url).header("Range", "bytes=$start-$end").build()
                HttpClient.downloadClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                    val body = resp.body ?: throw RuntimeException("empty body")
                    val written = body.byteStream().use { input ->
                        streamToChannel(input, channel, start, isCancelled)
                    }
                    if (written > 0) return written
                    throw RuntimeException("0 bytes")
                }
            } catch (e: Exception) {
                android.util.Log.w("JecWeb", "chunk $start-$end a$attempt/$MAX_ATTEMPTS: ${e.message}")
                if (attempt < MAX_ATTEMPTS) delay(1500)
            }
        }
        return 0L
    }

    /**
     * Читает InputStream и пишет в FileChannel с гарантией полной записи (FUSE на Android
     * может резать write на 1-2 MB). Возвращает реально записанные байты.
     */
    private fun streamToChannel(
        input: InputStream,
        channel: FileChannel,
        startPos: Long,
        isCancelled: () -> Boolean
    ): Long {
        val buf = ByteArray(IO_BUFFER)
        var pos = startPos
        var written = 0L
        while (!isCancelled()) {
            val n = input.read(buf)
            if (n <= 0) break
            val bb = ByteBuffer.wrap(buf, 0, n)
            while (bb.hasRemaining()) {
                val w = channel.write(bb, pos)
                if (w < 0) throw RuntimeException("channel write returned -1")
                pos += w
                written += w
            }
        }
        return written
    }
}
