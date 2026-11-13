package com.garickbox.jecwebgo.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

object DoubleOrEmptySerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("DoubleOrEmpty", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double {
        val input = decoder as? JsonDecoder ?: return 0.0
        val el = input.decodeJsonElement()
        return if (el is JsonPrimitive) (el.content.toDoubleOrNull() ?: 0.0) else 0.0
    }
    override fun serialize(encoder: Encoder, value: Double) { encoder.encodeDouble(value) }
}

@Serializable
data class TorrentItem(
    val title: String = "",
    val size: String = "",
    val seeds: Int = 0,
    val leeches: Int = 0,
    val magnet: String = "",
    val poster: String = "",
    @Serializable(with = DoubleOrEmptySerializer::class) val rating: Double = 0.0,
    val genre: String = "",
    val year: String = "",
    val description: String = "",
    val imdb_id: String = "",
    val kp_id: String = "",
    val category: String = "",
    val director: String = "",
    val actors: String = ""
)

@Serializable
data class MetaInfo(
    val generated_at: String = "",
    val count: Int = 0,
    val size_bytes: Long = 0
)

object DataRepository {
    private const val URL = "https://raw.githubusercontent.com/Garickbox/JecWebGo-Android/data/torrents.json"
    private const val FNAME = "torrents.json"
    private const val MAX_AGE_MS = 60L * 60 * 1000  // 1 час

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            val resp = chain.proceed(req)
            if (req.url.toString().contains("/data/posters/")) {
                resp.newBuilder()
                    .header("Cache-Control", "public, max-age=2592000")
                    .build()
            } else resp
        }
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun file(ctx: Context) = File(ctx.filesDir, FNAME)

    fun loadCached(ctx: Context): List<TorrentItem> {
        return try {
            val f = file(ctx)
            if (!f.exists()) emptyList()
            else json.decodeFromString<List<TorrentItem>>(f.readText())
        } catch (e: Exception) { emptyList() }
    }

    fun cacheAge(ctx: Context): Long {
        val f = file(ctx)
        return if (f.exists()) System.currentTimeMillis() - f.lastModified() else Long.MAX_VALUE
    }

    suspend fun loadMeta(ctx: Context): MetaInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://raw.githubusercontent.com/Garickbox/JecWebGo-Android/data/meta.json").build()
            val text = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body?.string().orEmpty()
            }
            if (text.length < 10) null else json.decodeFromString<MetaInfo>(text)
        } catch (e: Exception) { null }
    }

    suspend fun refresh(ctx: Context): List<TorrentItem> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(URL).build()
            val body = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext loadCached(ctx)
                r.body?.string().orEmpty()
            }
            if (body.length < 100) return@withContext loadCached(ctx)
            val items = json.decodeFromString<List<TorrentItem>>(body)
            file(ctx).writeText(body)
            items
        } catch (e: Exception) {
            loadCached(ctx)
        }
    }

    suspend fun load(ctx: Context): List<TorrentItem> = withContext(Dispatchers.IO) {
        val age = cacheAge(ctx)
        if (age > MAX_AGE_MS) refresh(ctx) else loadCached(ctx)
    }
}
