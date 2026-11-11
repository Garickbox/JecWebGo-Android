package com.garickbox.jecwebgo.domain

import com.garickbox.jecwebgo.data.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class SuperblitzParams(val preloadMB: Int, val threads: Int)

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

    suspend fun firstHash(): String? = withContext(Dispatchers.IO) {
        try {
            val arr = list()
            for (el in arr) {
                val obj = el.jsonObject
                val h = obj["hash"]?.jsonPrimitive?.contentOrNull
                if (h != null) return@withContext h
            }
            null
        } catch (_: Exception) { null }
    }

    suspend fun get(hash: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val b = buildJsonObject { put("action","get"); put("hash",hash) }.toString()
            json.parseToJsonElement(post(b)).jsonObject
        } catch (e: Exception) { null }
    }

    suspend fun getFiles(hash: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val obj = get(hash) ?: return@withContext emptyList()
            val stats = obj["file_stats"]?.jsonArray ?: return@withContext emptyList()
            stats.mapNotNull { it.jsonObject["path"]?.jsonPrimitive?.contentOrNull }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun stop(hash: String): Unit = withContext(Dispatchers.IO) {
        try { post(buildJsonObject { put("action","stop"); put("link",hash) }.toString()); Unit } catch (_: Exception) {}
    }

    suspend fun rem(hash: String): Unit = withContext(Dispatchers.IO) {
        try { post(buildJsonObject { put("action","rem"); put("hash",hash) }.toString()); Unit } catch (_: Exception) {}
    }

    fun isAlive(): Boolean = try {
        val req = Request.Builder().url("$BASE/").head().build()
        HttpClient.client.newCall(req).execute().use { it.code in 200..499 }
    } catch (_: Exception) { false }

    fun isAliveAt(ip: String, port: Int = 8090): Boolean = try {
        val req = Request.Builder().url("http://$ip:$port/").head().build()
        HttpClient.client.newCall(req).execute().use { it.code in 200..499 }
    } catch (_: Exception) { false }

    fun paramsForLevel(level: Int): SuperblitzParams = when (level) {
        1 -> SuperblitzParams(30, 3)
        2 -> SuperblitzParams(40, 4)
        3 -> SuperblitzParams(50, 6)
        4 -> SuperblitzParams(80, 8)
        5 -> SuperblitzParams(150, 10)
        else -> SuperblitzParams(50, 6)
    }

    suspend fun measureSpeed(hash: String): Int = withContext(Dispatchers.IO) {
        try {
            val totalMB = 100
            val threads = 20
            val totalBytes = totalMB.toLong() * 1024 * 1024
            val url = "$BASE/stream?link=$hash&index=1&play"
            val headReq = Request.Builder().url(url).head().build()
            val fileSize = HttpClient.client.newCall(headReq).execute().use { r ->
                if (!r.isSuccessful) return@withContext 0
                r.header("Content-Length")?.toLongOrNull() ?: 0L
            }
            if (fileSize < totalBytes) return@withContext 0
            val chunk = totalBytes / threads
            val t0 = System.currentTimeMillis()
            (0 until threads).map { i ->
                async(Dispatchers.IO) {
                    try {
                        val s = i.toLong() * chunk
                        val e = s + chunk - 1
                        val r = Request.Builder().url(url).header("Range", "bytes=$s-$e").build()
                        HttpClient.client.newCall(r).execute().use { it.body?.bytes() }
                    } catch (_: Exception) {}
                }
            }.awaitAll()
            val elapsed = (System.currentTimeMillis() - t0) / 1000.0
            if (elapsed <= 0.1) return@withContext 1
            val mbps = totalBytes * 8 / elapsed / 1e6
            when {
                mbps >= 80 -> 5
                mbps >= 40 -> 4
                mbps >= 15 -> 3
                mbps >= 5 -> 2
                else -> 1
            }
        } catch (_: Exception) { 0 }
    }

    suspend fun preload(hash: String, totalMB: Int = 50, threads: Int = 6) = withContext(Dispatchers.IO) {
        val url = "$BASE/stream?link=$hash&index=1&play"
        val chunk = (totalMB * 1024 * 1024) / threads
        val jobs = (0 until threads).map { i ->
            async(Dispatchers.IO) {
                try {
                    val s = i.toLong() * chunk
                    val e = s + chunk - 1
                    val r = Request.Builder().url(url).header("Range", "bytes=$s-$e").build()
                    HttpClient.client.newCall(r).execute().use { it.body?.bytes() }
                } catch (_: Exception) {}
            }
        }
        jobs.awaitAll()
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

    fun streamM3U(hash: String, ip: String = "127.0.0.1") = "http://$ip:8090/stream?link=$hash&m3u"
    fun streamPlay(hash: String, ip: String = "127.0.0.1") = "http://$ip:8090/stream?link=$hash&index=1&play"
}
