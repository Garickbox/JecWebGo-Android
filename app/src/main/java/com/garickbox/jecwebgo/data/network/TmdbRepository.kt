package com.garickbox.jecwebgo.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class TmdbGenre(val id: Int, val name: String, val emoji: String = "")

@Serializable
data class TmdbMovie(
    val id: Int = 0,
    val title: String = "",
    val original_title: String = "",
    val year: String = "",
    val rating: Double = 0.0,
    val vote_count: Int = 0,
    val overview: String = "",
    val genre_ids: List<Int> = emptyList(),
    val poster_file: String = "",
    val runtime: Int = 0,
    val director: String = "",
    val cast: List<String> = emptyList(),
    val genres: List<String> = emptyList()
)

@Serializable
data class TmdbData(
    val generated_at: String = "",
    val genres: List<TmdbGenre> = emptyList(),
    val movies: List<TmdbMovie> = emptyList()
) {
    fun byGenre(genreId: Int, limit: Int = 50): List<TmdbMovie> =
        movies.filter { genreId in it.genre_ids }
            .sortedByDescending { it.vote_count }
            .take(limit)

    fun genresOf(ids: List<Int>): List<TmdbGenre> =
        ids.mapNotNull { gid -> genres.firstOrNull { it.id == gid } }
}

object TmdbRepository {
    private const val URL = "https://raw.githubusercontent.com/Garickbox/JecWebGo-Android/data/tmdb_movies.json"
    private const val POSTER_BASE = "https://raw.githubusercontent.com/Garickbox/JecWebGo-Android/data/posters_tmdb/"
    private const val FNAME = "tmdb_movies.json"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun file(ctx: Context) = File(ctx.filesDir, FNAME)

    fun loadCached(ctx: Context): TmdbData? = try {
        val f = file(ctx)
        if (!f.exists()) null else json.decodeFromString<TmdbData>(f.readText())
    } catch (e: Exception) { null }

    fun cacheAge(ctx: Context): Long {
        val f = file(ctx)
        return if (f.exists()) System.currentTimeMillis() - f.lastModified() else Long.MAX_VALUE
    }

    suspend fun refresh(ctx: Context): TmdbData? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(URL).build()
            val body = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext loadCached(ctx)
                r.body?.string().orEmpty()
            }
            if (body.length < 100) return@withContext loadCached(ctx)
            val data = json.decodeFromString<TmdbData>(body)
            file(ctx).writeText(body)
            data
        } catch (e: Exception) { loadCached(ctx) }
    }

    suspend fun load(ctx: Context): TmdbData? = withContext(Dispatchers.IO) {
        if (cacheAge(ctx) > MAX_AGE_MS) refresh(ctx) else loadCached(ctx)
    }

    fun posterUrl(movie: TmdbMovie): String =
        if (movie.poster_file.isBlank()) "" else POSTER_BASE + movie.poster_file
}
