package com.garickbox.jecwebgo.data.download

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.garickbox.jecwebgo.data.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.Q)
object TorrentDownloader {
    private const val THREADS = 10
    private const val CHUNK_SIZE = 4L * 1024 * 1024

    suspend fun download(
        ctx: Context,
        hash: String,
        fileIndex: Int,
        targetUri: Uri,
        totalBytes: Long,
        onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit,
        isCancelled: () -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val handle = MediaStoreWriter.openChannel(ctx, targetUri) ?: return@withContext false
        val channel = handle.channel
        val url = "http://127.0.0.1:8090/stream?link=$hash&index=$fileIndex&play"
        val totalChunks = ((totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        val nextChunk = AtomicInteger(0)
        val downloaded = AtomicLong(0L)
        val lastTime = AtomicLong(System.currentTimeMillis())
        val lastBytes = AtomicLong(0L)

        try {
            val jobs = (0 until THREADS).map {
                async(Dispatchers.IO) {
                    while (!isCancelled()) {
                        val i = nextChunk.getAndIncrement()
                        if (i >= totalChunks) return@async
                        val start = i.toLong() * CHUNK_SIZE
                        val end = min(start + CHUNK_SIZE - 1, totalBytes - 1)
                        val bytes = fetchChunk(url, start, end, isCancelled) ?: return@async
                        channel.write(ByteBuffer.wrap(bytes), start)
                        val total = downloaded.addAndGet(bytes.size.toLong())
                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime.get()
                        val speed = if (elapsed >= 500L) {
                            val delta = total - lastBytes.get()
                            val sp = (delta * 1000L) / elapsed
                            lastBytes.set(total)
                            lastTime.set(now)
                            sp
                        } else 0L
                        // Прогресс каждый chunk — не по времени
                        onProgress(total, totalBytes, speed)
                    }
                }
            }
            jobs.awaitAll()
            handle.close()
            if (isCancelled()) {
                MediaStoreWriter.delete(ctx, targetUri)
                false
            } else {
                onProgress(downloaded.get(), totalBytes, 0L)
                MediaStoreWriter.finalizeFile(ctx, targetUri)
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "downloader fail: ${e.message}")
            runCatching { handle.close() }
            runCatching { MediaStoreWriter.delete(ctx, targetUri) }
            false
        }
    }

    private suspend fun fetchChunk(url: String, start: Long, end: Long, isCancelled: () -> Boolean): ByteArray? {
        var attempts = 0
        while (attempts < 3 && !isCancelled()) {
            attempts++
            try {
                val req = Request.Builder().url(url)
                    .header("Range", "bytes=$start-$end").build()
                return HttpClient.downloadClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                    resp.body?.bytes() ?: throw RuntimeException("empty body")
                }
            } catch (e: Exception) {
                if (attempts >= 3) {
                    android.util.Log.e("JecWeb", "chunk $start-$end failed after 3 attempts: ${e.message}")
                    return null
                }
                delay(2000)
            }
        }
        return null
    }
}
