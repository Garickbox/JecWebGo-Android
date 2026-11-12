package com.garickbox.jecwebgo.data.network
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
object HttpClient {
    val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    fun postJson(url: String, json: String): String {
        val media = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder().url(url).post(json.toRequestBody(media)).build()
        return client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    fun get(url: String, ua: String = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"): String {
        val req = Request.Builder().url(url).header("User-Agent", ua).header("Accept", "application/json").header("Accept-Language", "ru-RU,ru;q=0.9").build()
        return client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }
}
