package com.garickbox.jecwebgo.data.network
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
object HttpClient {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    fun get(url: String, ua: String = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"): String {
        val req = Request.Builder().url(url).header("User-Agent", ua).header("Accept", "application/json").header("Accept-Language", "ru-RU,ru;q=0.9").build()
        return client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }
}
