package com.garickbox.jecwebgo.data.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class HistoryEntry(val query: String, val time: Long = 0L)

object HistoryStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private fun file(ctx: Context) = File(ctx.filesDir, "history.json")

    suspend fun list(ctx: Context): List<HistoryEntry> = withContext(Dispatchers.IO) {
        try {
            val f = file(ctx)
            if (!f.exists()) emptyList() else json.decodeFromString<List<HistoryEntry>>(f.readText())
        } catch (e: Exception) { emptyList() }
    }

    suspend fun add(ctx: Context, query: String, limit: Int = 50) = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext
        val cur = list(ctx).filter { it.query != q }.toMutableList()
        cur.add(0, HistoryEntry(q, System.currentTimeMillis() / 1000))
        if (cur.size > limit) cur.subList(limit, cur.size).clear()
        file(ctx).writeText(json.encodeToString<List<HistoryEntry>>(cur))
        Unit
    }

    suspend fun removeAt(ctx: Context, index: Int) = withContext(Dispatchers.IO) {
        val cur = list(ctx).toMutableList()
        if (index in cur.indices) {
            cur.removeAt(index)
            file(ctx).writeText(json.encodeToString<List<HistoryEntry>>(cur))
        }
        Unit
    }
}
