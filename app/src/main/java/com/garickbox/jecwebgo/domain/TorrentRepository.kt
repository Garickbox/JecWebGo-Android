package com.garickbox.jecwebgo.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import com.garickbox.jecwebgo.data.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object TorrentRepository {
    private const val BASE = "http://127.0.0.1:8090"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val media = "application/json; charset=utf-8".toMediaType()

    private fun post(body: String): String {
        val req = Request.Builder().url("$BASE/torrents").post(body.toRequestBody(media)).build()
        return HttpClient.client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    suspend fun add(magnet: String): String? = withContext(Dispatchers.IO) {
        try {
            val b = buildJsonObject { put("action","add"); put("link",magnet); put("save_to_db", true) }.toString()
            json.parseToJsonElement(post(b)).jsonObject["hash"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) { null }
    }

    suspend fun list(): JsonArray = withContext(Dispatchers.IO) {
        try {
            val b = buildJsonObject { put("action","list") }.toString()
            json.parseToJsonElement(post(b)).jsonArray
        } catch (e: Exception) { JsonArray(emptyList()) }
    }

    suspend fun get(hash: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val b = buildJsonObject { put("action","get"); put("hash",hash) }.toString()
            json.parseToJsonElement(post(b)).jsonObject
        } catch (e: Exception) { null }
    }

    suspend fun stop(hash: String): Unit = withContext(Dispatchers.IO) {
        try { post(buildJsonObject { put("action","stop"); put("link",hash) }.toString()); Unit } catch (_: Exception) {}
    }

    suspend fun rem(hash: String): Unit = withContext(Dispatchers.IO) {
        try { post(buildJsonObject { put("action","rem"); put("hash",hash) }.toString()); Unit } catch (_: Exception) {}
    }

    suspend fun waitReady(hash: String, timeoutMs: Long = 15000L): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val url = "$BASE/stream?link=$hash&index=1&play"
        val req0 = Request.Builder().url(url).head().build()
        while (System.currentTimeMillis() < deadline) {
            try {
                val code = HttpClient.client.newCall(req0).execute().use { it.code }
                if (code in 200..299) return@withContext true
            } catch (_: Exception) {}
            Thread.sleep(2000)
        }
        false
    }

    suspend fun preload(hash: String, totalMB: Int = 30, threads: Int = 4) = withContext(Dispatchers.IO) {
        val url = "$BASE/stream?link=$hash&index=1&play"
        val chunk = (totalMB * 1024 * 1024) / threads
        val jobs = (0 until threads).map { i ->
            async(Dispatchers.IO) {
                try {
                    val s = i.toLong() * chunk
                    val e = s + chunk - 1
                    val r = Request.Builder().url(url)
                        .header("Range", "bytes=$s-$e").build()
                    HttpClient.client.newCall(r).execute().use { it.body?.bytes() }
                } catch (_: Exception) {}
            }
        }
        jobs.awaitAll()
    }

    fun isAlive(): Boolean = try {
        val req = Request.Builder().url("$BASE/").head().build()
        HttpClient.client.newCall(req).execute().use { it.code in 200..499 }
    } catch (_: Exception) { false }

    fun streamM3U(hash: String) = "$BASE/stream?link=$hash&m3u"
    fun streamPlay(hash: String) = "$BASE/stream?link=$hash&index=1&play"
}
