package com.garickbox.jecwebgo.data.download

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.garickbox.jecwebgo.data.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

@RequiresApi(Build.VERSION_CODES.Q)
object TorrentDownloader {
    private const val THREADS = 6
    private const val CHUNK_SIZE = 8L * 1024 * 1024

    suspend fun download(
        ctx: Context,
        hash: String,
        fileIndex: Int,
        targetUri: Uri,
        totalBytes: Long,
        onProgress: (downloaded: Long, speedBps: Long) -> Unit,
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
                        val req = Request.Builder().url(url)
                            .header("Range", "bytes=$start-$end").build()
                        val bytes = HttpClient.client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
                            resp.body?.bytes() ?: throw RuntimeException("empty body")
                        }
                        channel.write(ByteBuffer.wrap(bytes), start)
                        val now = System.currentTimeMillis()
                        val total = downloaded.addAndGet(bytes.size.toLong())
                        val elapsed = now - lastTime.get()
                        if (elapsed >= 1000L) {
                            val delta = total - lastBytes.get()
                            val speed = (delta * 1000L) / elapsed
                            lastBytes.set(total)
                            lastTime.set(now)
                            onProgress(total, speed)
                        }
                    }
                }
            }
            jobs.awaitAll()
            handle.close()
            if (isCancelled()) {
                MediaStoreWriter.delete(ctx, targetUri)
                false
            } else {
                onProgress(downloaded.get(), 0L)
                MediaStoreWriter.finalizeFile(ctx, targetUri)
                true
            }
        } catch (e: Exception) {
            runCatching { handle.close() }
            runCatching { MediaStoreWriter.delete(ctx, targetUri) }
            false
        }
    }
}
