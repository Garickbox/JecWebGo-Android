package com.garickbox.jecwebgo.data.store

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PlayLogEntry(
    val type: String,
    val title: String,
    val magnet: String = "",
    val hash: String = "",
    val time: Long = 0L,
    val position: Long = 0L,
    val duration: Long = 0L
)

object PlayLogStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private fun file(ctx: Context) = File(ctx.filesDir, "play_log.json")

    suspend fun list(ctx: Context): List<PlayLogEntry> = withContext(Dispatchers.IO) {
        try {
            val f = file(ctx)
            if (!f.exists()) emptyList() else json.decodeFromString<List<PlayLogEntry>>(f.readText())
        } catch (e: Exception) { emptyList() }
    }

    suspend fun add(ctx: Context, entry: PlayLogEntry, limit: Int = 100) = withContext(Dispatchers.IO) {
        // Дедуп как в Go: удалить старую запись с тем же magnet (или hash если magnet пуст)
        val cur = list(ctx).filter { old ->
            val sameMagnet = entry.magnet.isNotBlank() && old.magnet == entry.magnet
            val sameHash = entry.magnet.isBlank() && entry.hash.isNotBlank() && old.hash == entry.hash
            !sameMagnet && !sameHash
        }.toMutableList()
        cur.add(entry)
        if (cur.size > limit) cur.subList(0, cur.size - limit).clear()
        file(ctx).writeText(json.encodeToString<List<PlayLogEntry>>(cur))
        Unit
    }

    suspend fun removeAt(ctx: Context, index: Int) = withContext(Dispatchers.IO) {
        val cur = list(ctx).toMutableList()
        if (index in cur.indices) {
            cur.removeAt(index)
            file(ctx).writeText(json.encodeToString<List<PlayLogEntry>>(cur))
        }
        Unit
    }
}
