package com.garickbox.jecwebgo.data.store

import android.content.Context
import com.garickbox.jecwebgo.data.download.DownloadRequest
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
    private fun file(ctx: Context) = File(ctx.filesDir, "downloads_state.json")

    fun load(ctx: Context): List<PersistedDownload> = try {
        val f = file(ctx)
        if (!f.exists()) emptyList() else json.decodeFromString<List<PersistedDownload>>(f.readText())
    } catch (_: Exception) { emptyList() }

    fun save(ctx: Context, items: List<PersistedDownload>) {
        try { file(ctx).writeText(json.encodeToString(items)) } catch (_: Exception) {}
    }
}
