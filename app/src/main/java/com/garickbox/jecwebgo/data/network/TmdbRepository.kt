package com.garickbox.jecwebgo.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class TmdbGenre(val id: Int, val name: String, val emoji: String = "")

@Serializable
data class TmdbCast(
    val id: Int = 0,
    val name: String = "",
    val profile_path: String = "",
    val character: String = "",
    val order: Int = 99,
    val profile_file: String = ""
)

@Serializable
data class TmdbCrew(
    val id: Int = 0,
    val name: String = "",
    val job: String = "",
    val department: String = ""
)

@Serializable
data class TmdbCompany(
    val id: Int = 0,
    val name: String = "",
    val logo_path: String = ""
)

@Serializable
data class TmdbCollection(
    val id: Int = 0,
    val name: String = "",
    val poster_path: String = ""
)

@Serializable
data class TmdbActor(
    val id: Int = 0,
    val name: String = "",
    val profile_file: String = "",
    val movie_count: Int = 0,
    val popularity: Double = 0.0,
    val avg_rating: Double = 0.0
)

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
    val cast_ids: List<Int> = emptyList(),
    val cast_details: List<TmdbCast> = emptyList(),
    val crew: List<TmdbCrew> = emptyList(),
    val genres: List<String> = emptyList(),
    val production_companies: List<TmdbCompany> = emptyList(),
    val belongs_to_collection: TmdbCollection? = null,
    val budget: Long = 0L,
    val revenue: Long = 0L,
    val tagline: String = "",
    val original_language: String = "",
    val spoken_languages: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val imdb_id: String = ""
)

@Serializable
data class TmdbData(
    val generated_at: String = "",
    val genres: List<TmdbGenre> = emptyList(),
    val top_actors: List<TmdbActor> = emptyList(),
    val movies: List<TmdbMovie> = emptyList()
) {
    fun byGenre(genreId: Int, limit: Int = 100): List<TmdbMovie> =
        movies.filter { genreId in it.genre_ids }
            .sortedByDescending { it.vote_count }
            .take(limit)

    fun genresOf(ids: List<Int>): List<TmdbGenre> =
        ids.mapNotNull { gid -> genres.firstOrNull { it.id == gid } }

    fun byCategory(key: String, limit: Int = 100): List<TmdbMovie> = when (key) {
        "new" -> movies.filter { (it.year.toIntOrNull() ?: 0) >= 2025 }
            .sortedByDescending { it.vote_count }
        "top" -> movies.filter { it.rating >= 7.5 && it.vote_count >= 500 }
            .sortedByDescending { it.rating }
        "blockbuster" -> movies.filter { it.vote_count >= 2000 }
            .sortedByDescending { it.vote_count }
        "eight" -> movies.filter { it.rating >= 8.0 }
            .sortedByDescending { it.rating }
        "epic" -> movies.filter { it.runtime >= 150 }
            .sortedByDescending { it.rating }
        else -> movies.sortedByDescending { it.vote_count }
    }.take(limit)

    fun byActorId(personId: Int, limit: Int = 100): List<TmdbMovie> =
        movies.filter { m ->
            personId in m.cast_ids || m.cast_details.any { it.id == personId }
        }
            .sortedByDescending { it.rating }
            .take(limit)

    fun byCollection(collectionId: Int, limit: Int = 100): List<TmdbMovie> =
        movies.filter { it.belongs_to_collection?.id == collectionId }
            .sortedByDescending { it.vote_count }
            .take(limit)

    fun byStudio(name: String, limit: Int = 100): List<TmdbMovie> =
        movies.filter { m -> m.production_companies.any { it.name == name } }
            .sortedByDescending { it.vote_count }
            .take(limit)
}

data class TmdbCategory(val key: String, val name: String, val emoji: String)

val TMDB_CATEGORIES = listOf(
    TmdbCategory("new", "Новинки", "🆕"),
    TmdbCategory("top", "Топ", "🔥"),
    TmdbCategory("blockbuster", "Блокбастеры", "💰"),
    TmdbCategory("eight", "Рейтинг 8+", "⭐"),
    TmdbCategory("epic", "Эпики", "⏱️"),
    TmdbCategory("all", "Все", "🎬")
)

object TmdbRepository {
    private const val URL = "https://raw.githubusercontent.com/Garickbox/JecWebGo-Android/data/tmdb_movies.json"
    private const val POSTER_BASE = "https://cdn.jsdelivr.net/gh/Garickbox/JecWebGo-Android@data/posters_tmdb/"
    private const val ACTOR_BASE = "https://cdn.jsdelivr.net/gh/Garickbox/JecWebGo-Android@data/posters_actors/"
    private const val FNAME = "tmdb_movies.json"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val MIN_MOVIES = 2000

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
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
            android.util.Log.d("JecWeb", "Tmdb refresh: ${data.movies.size} movies, ${data.top_actors.size} actors")
            data
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "Tmdb refresh fail: ${e.message}")
            loadCached(ctx)
        }
    }

    suspend fun load(ctx: Context): TmdbData? = withContext(Dispatchers.IO) {
        val cached = loadCached(ctx)
        val isOldSchema = cached == null || cached.top_actors.isEmpty() || cached.movies.size < MIN_MOVIES
        val isStale = cacheAge(ctx) > MAX_AGE_MS
        if (isStale || isOldSchema) refresh(ctx) else cached
    }

    private const val WORKER_BASE = "https://ai-jecweb.garickwot.workers.dev"

    suspend fun searchPosterByTitle(title: String, year: String): String? = withContext(Dispatchers.IO) {
        try {
            val clean = com.garickbox.jecwebgo.util.TitleUtil.cleanTitle(title).trim()
            if (clean.length < 2) return@withContext null
            val q = java.net.URLEncoder.encode(clean, "UTF-8")
            val y = year.take(4)
            val url = "$WORKER_BASE/tmdb/search/movie?query=$q&year=$y&language=ru-RU"
            val req = Request.Builder().url(url).build()
            val text = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body?.string().orEmpty()
            }
            val obj = json.parseToJsonElement(text).jsonObject
            val results = obj["results"]?.jsonArray ?: return@withContext null
            val first = results.firstOrNull()?.jsonObject ?: return@withContext null
            val posterPath = first["poster_path"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
            if (posterPath.isBlank()) return@withContext null
            val tmdbUrl = "https://image.tmdb.org/t/p/w342$posterPath"
            "$WORKER_BASE/img?url=" + java.net.URLEncoder.encode(tmdbUrl, "UTF-8")
        } catch (e: Exception) { null }
    }

    data class TmdbSearchInfo(val posterUrl: String, val overview: String)

    suspend fun searchMovieInfo(title: String, year: String): TmdbSearchInfo? = withContext(Dispatchers.IO) {
        try {
            val clean = com.garickbox.jecwebgo.util.TitleUtil.cleanTitle(title).trim()
            if (clean.length < 2) return@withContext null
            val q = java.net.URLEncoder.encode(clean, "UTF-8")
            val y = year.take(4)
            val url = "$WORKER_BASE/tmdb/search/movie?query=$q&year=$y&language=ru-RU"
            val req = Request.Builder().url(url).build()
            val text = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body?.string().orEmpty()
            }
            val obj = json.parseToJsonElement(text).jsonObject
            val results = obj["results"]?.jsonArray ?: return@withContext null
            val first = results.firstOrNull()?.jsonObject ?: return@withContext null
            val posterPath = first["poster_path"]?.jsonPrimitive?.contentOrNull ?: ""
            val overview = first["overview"]?.jsonPrimitive?.contentOrNull ?: ""
            val tmdbUrl = if (posterPath.isBlank()) "" else "https://image.tmdb.org/t/p/w342$posterPath"
            val posterWorkerUrl = if (tmdbUrl.isBlank()) "" else "$WORKER_BASE/img?url=" + java.net.URLEncoder.encode(tmdbUrl, "UTF-8")
            TmdbSearchInfo(posterWorkerUrl, overview)
        } catch (e: Exception) { null }
    }

    fun posterUrl(movie: TmdbMovie): String =
        if (movie.poster_file.isBlank()) "" else POSTER_BASE + movie.poster_file

    fun actorPhotoUrl(profileFile: String): String =
        if (profileFile.isBlank()) "" else ACTOR_BASE + profileFile

    fun collectionPosterUrl(posterPath: String): String {
        if (posterPath.isBlank()) return ""
        return "$WORKER_BASE/img?url=" + java.net.URLEncoder.encode("https://image.tmdb.org/t/p/w185$posterPath", "UTF-8")
    }
}
