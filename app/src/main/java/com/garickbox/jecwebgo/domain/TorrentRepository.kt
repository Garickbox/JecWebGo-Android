package com.garickbox.jecwebgo.domain

import com.garickbox.jecwebgo.data.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    data class AddResult(
        val hash: String,
        val firstVideoIndex: Int,
        val firstVideoName: String
    )

    private val VIDEO_EXT_SET = setOf("mp4","mkv","avi","mov","wmv","webm","flv","m4v","ts","m2ts")

    /**
     * Добавляет торрент и СРАЗУ возвращает hash + первый video (из data.TorrServer.Files).
     * Не ждёт findVideoFile/getFiles — данные уже в add resp.
     */
    suspend fun addWithFile(magnet: String): AddResult? = withContext(Dispatchers.IO) {
        try {
            val enriched = com.garickbox.jecwebgo.util.MagnetUtil.enrich(magnet)
            val b = buildJsonObject { put("action","add"); put("link",enriched); put("save_to_db", true) }.toString()
            val resp = post(b)
            android.util.Log.d("JecWeb", "addWithFile resp: ${resp.take(400)}")
            val root = json.parseToJsonElement(resp).jsonObject
            val hash = root["hash"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
            // data — вложенная JSON-строка
            val dataStr = root["data"]?.jsonPrimitive?.contentOrNull
            var firstIdx = 1
            var firstName = ""
            var found = false
            if (!dataStr.isNullOrBlank()) {
                try {
                    val inner = json.parseToJsonElement(dataStr).jsonObject
                    val files = inner["TorrServer"]?.jsonObject?.get("Files")?.jsonArray
                    if (files != null) {
                        for (el in files) {
                            val o = el.jsonObject
                            val id = o["id"]?.jsonPrimitive?.intOrNull ?: continue
                            val path = o["path"]?.jsonPrimitive?.contentOrNull ?: continue
                            val len = o["length"]?.jsonPrimitive?.longOrNull ?: 0L
                            val ext = path.substringAfterLast(".", "").lowercase()
                            if (ext in VIDEO_EXT_SET && len > 0) {
                                firstIdx = id
                                firstName = path.substringAfterLast("/")
                                found = true
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JecWeb", "addWithFile data parse: ${e.message}")
                }
            }
            if (!found) {
                android.util.Log.w("JecWeb", "addWithFile: no video in add resp, polling up to 40s")
                val video = waitForVideo(hash, 40000L)
                if (video != null) {
                    firstIdx = video.id
                    firstName = video.path.substringAfterLast("/")
                    found = true
                } else {
                    android.util.Log.e("JecWeb", "addWithFile: no video after 40s, abort")
                    return@withContext null
                }
            }
            AddResult(hash, firstIdx, firstName)
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "addWithFile fail: ${e.message}")
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

    /** Ждёт появления видеофайла в metadata (до timeoutMs), опрашивая getFiles каждую 1с. */
    suspend fun waitForVideo(hash: String, timeoutMs: Long = 90000L): TorrentFile? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var polls = 0
        var lastStat = -1
        while (System.currentTimeMillis() < deadline) {
            polls++
            val stat = try {
                val g = get(hash)
                g?.get("stat")?.jsonPrimitive?.intOrNull ?: -1
            } catch (e: Exception) { -1 }
            val files = getFiles(hash)
            if (polls % 5 == 0 || stat != lastStat) {
                android.util.Log.d("JecWeb", "waitForVideo poll=$polls stat=$stat files=${files.size}")
                lastStat = stat
            }
            if (files.isNotEmpty()) {
                val video = files.firstOrNull { f ->
                    val ext = f.path.substringAfterLast(".", "").lowercase()
                    ext in VIDEO_EXT_SET && f.length > 0
                } ?: files.firstOrNull { f ->
                    val ext = f.path.substringAfterLast(".", "").lowercase()
                    ext in VIDEO_EXT_SET
                }
                if (video != null) {
                    android.util.Log.d("JecWeb", "waitForVideo: found ${video.path} len=${video.length} stat=$stat after ${polls}s")
                    return@withContext video
                }
            }
            Thread.sleep(1000)
        }
        android.util.Log.w("JecWeb", "waitForVideo: timeout ${timeoutMs}ms, no video")
        null
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

    private val probeClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
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

    suspend fun waitReady(hash: String, index: Int = 1, timeoutMs: Long = 30000L): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val url = "$BASE/stream?link=$hash&index=$index&play"
        var lastStat = -1
        while (System.currentTimeMillis() < deadline) {
            try {
                val stat = try {
                    val g = get(hash)
                    g?.get("stat")?.jsonPrimitive?.intOrNull ?: -1
                } catch (e: Exception) { -1 }
                if (stat != lastStat) {
                    android.util.Log.d("JecWeb", "waitReady: stat=$stat")
                    lastStat = stat
                }
                if (stat in 0..2) {
                    Thread.sleep(1000)
                    continue
                }
                val req = Request.Builder().url(url).header("Range", "bytes=0-1").get().build()
                val (code, bytesRead) = probeClient.newCall(req).execute().use {
                    val body = it.body
                    val n = if (body != null) {
                        try {
                            val buf = ByteArray(2)
                            val stream = body.byteStream()
                            val got = stream.read(buf)
                            runCatching { stream.close() }
                            if (got > 0) got else 0
                        } catch (e: Exception) { 0 }
                    } else 0
                    Pair(it.code, n)
                }
                if (code in 200..299 && bytesRead >= 2) {
                    android.util.Log.d("JecWeb", "waitReady: OK stat=$stat")
                    return@withContext true
                }
            } catch (_: Exception) {}
            Thread.sleep(2000)
        }
        false
    }

    /**
     * Проверка готовности: Range 0-1 → 200 OK (1-2 сек).
     * НЕ качает реальные данные (это перегружает TorrServer).
     * VLC сам буферизует после открытия (cache=60000).
     * БЕЗ preload — preload дропает пиров.
     */
    suspend fun waitReadyReal(
        hash: String,
        index: Int,
        timeoutMs: Long = 30000L,
        onProgress: (String) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val url = "$BASE/stream?link=$hash&index=$index&play"
        var attempts = 0
        while (System.currentTimeMillis() < deadline) {
            attempts++
            try {
                val req = Request.Builder().url(url).header("Range", "bytes=0-1").get().build()
                val code = HttpClient.downloadClient.newCall(req).execute().use { it.code }
                if (code in 200..299) {
                    onProgress("Готов")
                    return@withContext true
                }
                onProgress("HTTP $code (попытка $attempts)")
            } catch (e: Exception) {
                onProgress("Ожидание TorrServer... ($attempts)")
            }
            Thread.sleep(2000)
        }
        false
    }

    fun streamM3U(hash: String, ip: String = "127.0.0.1") = "http://$ip:8090/stream?link=$hash&m3u"
    fun streamPlay(hash: String, ip: String = "127.0.0.1") = "http://$ip:8090/stream?link=$hash&index=1&play"
}
