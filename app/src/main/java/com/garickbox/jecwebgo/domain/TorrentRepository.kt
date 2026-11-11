package com.garickbox.jecwebgo.domain

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

    fun streamM3U(hash: String) = "$BASE/stream?link=$hash&m3u"
    fun streamPlay(hash: String) = "$BASE/stream?link=$hash&index=1&play"
}
