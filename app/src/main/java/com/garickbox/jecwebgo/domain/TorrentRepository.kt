package com.garickbox.jecwebgo.domain

import com.garickbox.jecwebgo.data.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class TorrentFile(val id: Int, val path: String, val length: Long)

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
            val enriched = com.garickbox.jecwebgo.util.MagnetUtil.enrich(magnet)
            val b = buildJsonObject { put("action","add"); put("link",enriched); put("save_to_db", true) }.toString()
            val resp = post(b)
            android.util.Log.d("JecWeb", "add resp: ${resp.take(300)}")
            json.parseToJsonElement(resp).jsonObject["hash"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "add fail: ${e.message}")
            null
        }
    }

    suspend fun addTorrentBase64(b64: String): String? = withContext(Dispatchers.IO) {
        try {
            val b = buildJsonObject {
                put("action", "add"); put("torrent", b64); put("save_to_db", true)
            }.toString()
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

    suspend fun getFiles(hash: String): List<TorrentFile> = withContext(Dispatchers.IO) {
        try {
            val obj = get(hash) ?: return@withContext emptyList()
            // ФОРМАТ 1 (YouROK): obj["file_stats"] = [{id,path,length}]
            obj["file_stats"]?.jsonArray?.let { stats ->
                val list = stats.mapNotNull { el ->
                    val o = el.jsonObject
                    val id = o["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    val path = o["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val len = o["length"]?.jsonPrimitive?.longOrNull ?: 0L
                    if (len > 0) TorrentFile(id, path, len) else null
                }
                if (list.isNotEmpty()) return@withContext list
            }
            // ФОРМАТ 2 (MatriX.150): obj["data"] = "{\"TorrServer\":{\"Files\":[...]}}" (JSON-строка!)
            obj["data"]?.jsonPrimitive?.contentOrNull?.let { dataStr ->
                if (dataStr.isNotBlank()) {
                    val inner = json.parseToJsonElement(dataStr).jsonObject
                    val files = inner["TorrServer"]?.jsonObject?.get("Files")?.jsonArray ?: return@withContext emptyList()
                    return@withContext files.mapNotNull { el ->
                        val o = el.jsonObject
                        val id = o["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                        val path = o["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val len = o["length"]?.jsonPrimitive?.longOrNull ?: 0L
                        if (len > 0) TorrentFile(id, path, len) else null
                    }
                }
            }
            emptyList()
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "getFiles err: ${e.message}")
            emptyList()
        }
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

    suspend fun firstHash(): String? = withContext(Dispatchers.IO) {
        try {
            val arr = list()
            for (el in arr) {
                val h = el.jsonObject["hash"]?.jsonPrimitive?.contentOrNull
                if (h != null) return@withContext h
            }
            null
        } catch (_: Exception) { null }
    }

    /**
     * Прогрев торрента БЕЗ &play (низкий приоритет) — раскручивает пиры,
     * заполняет кэш TorrServer для последующего sequential download.
     * Параллельные Range-запросы заставляют TorrServer качать с разных пиров.
     */
    suspend fun preload(hash: String, totalMB: Int = 50, threads: Int = 6) = withContext(Dispatchers.IO) {
        val url = "$BASE/stream?link=$hash&index=1"
        val chunkBytes = (totalMB.coerceIn(10, 200) * 1024L * 1024L) / threads
        val jobs = (0 until threads).map { i ->
            async(Dispatchers.IO) {
                try {
                    val s = i.toLong() * chunkBytes
                    val e = s + chunkBytes - 1
                    val r = Request.Builder().url(url).header("Range", "bytes=$s-$e").build()
                    HttpClient.downloadClient.newCall(r).execute().use { it.body?.bytes() }
                } catch (_: Exception) {}
            }
        }
        jobs.forEach { it.await() }
    }

    suspend fun waitReady(hash: String, index: Int = 1, timeoutMs: Long = 120000L): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val url = "$BASE/stream?link=$hash&index=$index&play"
        while (System.currentTimeMillis() < deadline) {
            try {
                // GET Range 0-1 — считается чтением, TorrServer НЕ закрывает торрент по DisconnectTimeout
                val req = Request.Builder().url(url).header("Range", "bytes=0-1").get().build()
                val (code, len) = HttpClient.client.newCall(req).execute().use {
                    Pair(it.code, it.header("Content-Range")?.substringAfter("/")?.toLongOrNull()
                        ?: it.header("Content-Length")?.toLongOrNull() ?: 0L)
                }
                if (code in 200..299) return@withContext true
            } catch (_: Exception) {}
            Thread.sleep(2000)
        }
        false
    }

    fun streamM3U(hash: String, ip: String = "127.0.0.1") = "http://$ip:8090/stream?link=$hash&m3u"
    fun streamPlay(hash: String, ip: String = "127.0.0.1") = "http://$ip:8090/stream?link=$hash&index=1&play"
}
