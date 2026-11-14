package com.garickbox.jecwebgo.data.download

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
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
    private const val CHUNK_MIN = 64L * 1024L        // 64 KB — порог готовности TorrServer
    private const val CHUNK_MAX = 512L * 1024L       // 512 KB — целевой размер
    private const val IO_BUFFER = 64 * 1024          // 64 KB RAM
    private const val MAX_ATTEMPTS = 5
    private const val EMPTY_SHRINK = 2               // consecutive empty → shrink chunk
    private const val EMPTY_ABORT = 10               // consecutive empty → abort

    /** Отдельный client с readTimeout=60s — если TorrServer висит, не ждём 120s */
    private val chunkClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private data class ChunkResult(
        val written: Long,
        val expected: Long,
        val httpCode: Int,
        val emptyBody: Boolean,
        val error: String? = null
    )

    suspend fun download(
        ctx: Context,
        hash: String,
        fileIndex: Int,
        targetUri: Uri,
        totalBytes: Long,
        startOffset: Long = 0L,
        onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean = { false }
    ): Boolean = withContext(Dispatchers.IO) {
        val handle = MediaStoreWriter.openChannel(ctx, targetUri) ?: run {
            android.util.Log.e("JecWeb", "openChannel fail for $targetUri")
            return@withContext false
        }
        val channel = handle.channel
        val url = "http://127.0.0.1:8090/stream?link=$hash&index=$fileIndex&play"
        var downloaded = startOffset
        var chunkSize = if (startOffset == 0L) CHUNK_MIN else CHUNK_MAX
        var consecutiveEmpty = 0
        var lastReport = System.currentTimeMillis()
        var lastBytes = startOffset

        try {
            android.util.Log.d("JecWeb", "dl.start total=$totalBytes start=$startOffset chunk0=$chunkSize")
            onProgress(startOffset, totalBytes, 0L)
            if (startOffset >= totalBytes) {
                handle.close()
                MediaStoreWriter.finalizeFile(ctx, targetUri)
                android.util.Log.d("JecWeb", "dl.already_complete $totalBytes")
                return@withContext true
            }

            while (downloaded < totalBytes && !isCancelled()) {
                if (isPaused()) { delay(500); continue }
                val start = downloaded
                val end = min(start + chunkSize - 1, totalBytes - 1)
                val expected = end - start + 1
                val t0 = System.currentTimeMillis()

                val result = fetchChunk(url, start, end, channel, isCancelled)
                val tookMs = System.currentTimeMillis() - t0

                if (isCancelled()) {
                    android.util.Log.d("JecWeb", "dl.cancelled at $downloaded/$totalBytes")
                    handle.close()
                    return@withContext false
                }

                if (result.emptyBody) {
                    consecutiveEmpty++
                    android.util.Log.w("JecWeb", "dl.empty $start-$end emptyN=$consecutiveEmpty http=${result.httpCode} took=${tookMs}ms chunk=$chunkSize")
                    if (consecutiveEmpty >= EMPTY_SHRINK && chunkSize > CHUNK_MIN) {
                        chunkSize = (chunkSize / 2).coerceAtLeast(CHUNK_MIN)
                        android.util.Log.d("JecWeb", "dl.adapt shrink -> $chunkSize")
                        consecutiveEmpty = 0
                    }
                    if (consecutiveEmpty >= EMPTY_ABORT) {
                        android.util.Log.e("JecWeb", "dl.abort $EMPTY_ABORT empty in a row — TorrServer not ready")
                        handle.close()
                        // НЕ удаляем файл — сохраняем для resume
                        return@withContext false
                    }
                    delay(2000)
                    continue
                }

                if (result.written <= 0) {
                    android.util.Log.e("JecWeb", "dl.fail $start-$end error=${result.error} http=${result.httpCode}")
                    handle.close()
                    MediaStoreWriter.delete(ctx, targetUri)
                    return@withContext false
                }

                downloaded += result.written
                consecutiveEmpty = 0

                if (result.written < expected) {
                    android.util.Log.w("JecWeb", "dl.partial $start-$end got=${result.written}/$expected")
                    delay(500)
                } else if (chunkSize < CHUNK_MAX) {
                    chunkSize = (chunkSize * 2).coerceAtMost(CHUNK_MAX)
                    android.util.Log.d("JecWeb", "dl.adapt grow -> $chunkSize")
                }

                val now = System.currentTimeMillis()
                if (now - lastReport >= 500L) {
                    val speed = if (now > lastReport) ((downloaded - lastBytes) * 1000L) / (now - lastReport) else 0L
                    android.util.Log.d("JecWeb", "dl.progress $downloaded/$totalBytes spd=$speed chunk=$chunkSize")
                    onProgress(downloaded, totalBytes, speed)
                    lastReport = now
                    lastBytes = downloaded
                }
            }

            if (downloaded < totalBytes) {
                android.util.Log.e("JecWeb", "dl.incomplete $downloaded/$totalBytes cancelled=${isCancelled()}")
                handle.close()
                // при cancel файл НЕ удаляем — resume потом
                if (!isCancelled()) MediaStoreWriter.delete(ctx, targetUri)
                return@withContext false
            }

            onProgress(downloaded, totalBytes, 0L)
            handle.close()
            MediaStoreWriter.finalizeFile(ctx, targetUri)
            android.util.Log.d("JecWeb", "dl.complete $downloaded/$totalBytes")
            true
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "dl.exception ${e.message}")
            runCatching { handle.close() }
            if (!isCancelled()) runCatching { MediaStoreWriter.delete(ctx, targetUri) }
            false
        }
    }

    private suspend fun fetchChunk(
        url: String,
        start: Long,
        end: Long,
        channel: FileChannel,
        isCancelled: () -> Boolean
    ): ChunkResult {
        val expected = end - start + 1
        var lastHttpCode = 0
        var lastEmpty = false
        var lastError = ""
        for (attempt in 1..MAX_ATTEMPTS) {
            if (isCancelled()) return ChunkResult(0, expected, 0, false, "cancelled")
            try {
                val req = Request.Builder().url(url).header("Range", "bytes=$start-$end").build()
                chunkClient.newCall(req).execute().use { resp ->
                    lastHttpCode = resp.code
                    if (!resp.isSuccessful) {
                        lastError = "HTTP ${resp.code}"
                        throw RuntimeException(lastError)
                    }
                    val body = resp.body ?: throw RuntimeException("null body")
                    val serverLen = body.contentLength()
                    val written = body.byteStream().use { input ->
                        streamToChannel(input, channel, start, isCancelled)
                    }
                    if (written > 0) {
                        return ChunkResult(written, expected, resp.code, false)
                    }
                    lastEmpty = true
                    android.util.Log.w("JecWeb", "dl.fetch $start-$end a$attempt: body empty (serverLen=$serverLen)")
                    throw RuntimeException("empty body")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "unknown"
                android.util.Log.w("JecWeb", "dl.fetch $start-$end a$attempt/$MAX_ATTEMPTS: $lastError")
                if (attempt < MAX_ATTEMPTS) {
                    val backoff = 1000L shl (attempt - 1)   // 1s, 2s, 4s, 8s, 16s
                    delay(backoff)
                }
            }
        }
        return ChunkResult(0, expected, lastHttpCode, lastEmpty, lastError)
    }

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
                if (w < 0) throw RuntimeException("channel.write -1")
                if (w == 0) throw RuntimeException("channel.write 0")
                pos += w
                written += w
            }
        }
        return written
    }
}
