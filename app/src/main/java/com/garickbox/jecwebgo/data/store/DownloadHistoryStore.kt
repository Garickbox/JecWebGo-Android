package com.garickbox.jecwebgo.data.store

import android.content.Context
import com.garickbox.jecwebgo.data.download.DownloadedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object DownloadHistoryStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val mutex = Mutex()  // сериализует save() — два параллельных writeText ломали файл

    private fun file(ctx: Context) = File(ctx.filesDir, "downloads.json")
    private fun tmpFile(ctx: Context) = File(ctx.filesDir, "downloads.json.tmp")

    fun load(ctx: Context): List<DownloadedItem> {
        return try {
            val f = file(ctx)
            if (!f.exists()) emptyList()
            else {
                val txt = f.readText()
                if (txt.isBlank()) emptyList()
                else json.decodeFromString<List<DownloadedItem>>(txt)
            }
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "history.load fail: ${e.message}")
            emptyList()
        }
    }

    suspend fun save(ctx: Context, items: List<DownloadedItem>): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val tmp = tmpFile(ctx)
                tmp.writeText(json.encodeToString(items))
                if (!tmp.renameTo(file(ctx))) {
                    file(ctx).writeText(json.encodeToString(items))
                    tmp.delete()
                }
            } catch (e: Exception) {
                android.util.Log.e("JecWeb", "history.save fail: ${e.message}")
            }
            Unit
        }
    }
}
