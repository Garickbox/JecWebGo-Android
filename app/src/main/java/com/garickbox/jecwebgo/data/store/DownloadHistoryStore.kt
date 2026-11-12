package com.garickbox.jecwebgo.data.store

import android.content.Context
import com.garickbox.jecwebgo.data.download.DownloadedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object DownloadHistoryStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private fun file(ctx: Context) = File(ctx.filesDir, "downloads.json")

    fun load(ctx: Context): List<DownloadedItem> {
        return try {
            val f = file(ctx)
            if (!f.exists()) emptyList()
            else json.decodeFromString<List<DownloadedItem>>(f.readText())
        } catch (e: Exception) { emptyList() }
    }

    suspend fun save(ctx: Context, items: List<DownloadedItem>) = withContext(Dispatchers.IO) {
        try { file(ctx).writeText(json.encodeToString(items)) } catch (_: Exception) {}
    }
}
