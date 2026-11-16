package com.garickbox.jecwebgo.data.store

import android.content.Context
import com.garickbox.jecwebgo.data.download.DownloadRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PersistedDownload(
    val id: String,
    val request: DownloadRequest,
    val uri: String = "",
    val downloadedBytes: Long = 0L
)

object DownloadStateStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private fun file(ctx: Context) = File(ctx.filesDir, "downloads_state.json")
    private fun tmpFile(ctx: Context) = File(ctx.filesDir, "downloads_state.json.tmp")

    fun load(ctx: Context): List<PersistedDownload> = try {
        val f = file(ctx)
        if (!f.exists()) emptyList()
        else {
            val txt = f.readText()
            if (txt.isBlank()) emptyList()
            else json.decodeFromString<List<PersistedDownload>>(txt)
        }
    } catch (e: Exception) {
        android.util.Log.e("JecWeb", "state.load fail: ${e.message}")
        emptyList()
    }

    /** НЕ suspend — вызывается из persist() на любом потоке. Mutex синхронный. */
    fun save(ctx: Context, items: List<PersistedDownload>) {
        try {
            val tmp = tmpFile(ctx)
            tmp.writeText(json.encodeToString(items))
            if (!tmp.renameTo(file(ctx))) {
                file(ctx).writeText(json.encodeToString(items))
                tmp.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "state.save fail: ${e.message}")
        }
    }
}
