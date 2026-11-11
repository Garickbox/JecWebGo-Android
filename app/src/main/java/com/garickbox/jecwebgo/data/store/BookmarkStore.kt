package com.garickbox.jecwebgo.data.store

import android.content.Context
import com.garickbox.jecwebgo.domain.model.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Bookmark(val title: String, val magnet: String, val added: Long = 0L)

object BookmarkStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private fun file(ctx: Context) = File(ctx.filesDir, "bookmarks.json")

    suspend fun list(ctx: Context): List<Bookmark> = withContext(Dispatchers.IO) {
        try {
            val f = file(ctx)
            if (!f.exists()) emptyList() else json.decodeFromString<List<Bookmark>>(f.readText())
        } catch (e: Exception) { emptyList() }
    }

    suspend fun add(ctx: Context, item: SearchItem) = withContext(Dispatchers.IO) {
        val cur = list(ctx).filter { it.magnet != item.magnet }.toMutableList()
        cur.add(Bookmark(item.title, item.magnet, System.currentTimeMillis() / 1000))
        file(ctx).writeText(json.encodeToString<List<Bookmark>>(cur))
        Unit
    }

    suspend fun removeAt(ctx: Context, index: Int) = withContext(Dispatchers.IO) {
        val cur = list(ctx).toMutableList()
        if (index in cur.indices) {
            cur.removeAt(index)
            file(ctx).writeText(json.encodeToString<List<Bookmark>>(cur))
        }
        Unit
    }
}
