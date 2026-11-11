package com.garickbox.jecwebgo.domain
import com.garickbox.jecwebgo.data.network.HttpClient
import com.garickbox.jecwebgo.domain.model.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
object SearchRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val last = arrayOf(0L)
    private val MIN_INTERVAL = 3000L

    suspend fun jacred(query: String, type: String = ""): List<SearchItem> = withContext(Dispatchers.IO) {
        val elapsed = System.currentTimeMillis() - last[0]
        if (elapsed < MIN_INTERVAL) kotlinx.coroutines.delay(MIN_INTERVAL - elapsed)
        last[0] = System.currentTimeMillis()
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://jac.red/api/v1.0/torrents?search=$q" + if (type.isNotBlank() && type != "-1") "&type=$type" else ""
        val body = try { HttpClient.get(url) } catch (e: Exception) { return@withContext emptyList() }
        val arr = try { json.parseToJsonElement(body).jsonArray } catch (e: Exception) { return@withContext emptyList() }
        arr.mapNotNull { el ->
            val o = el.jsonObject
            val magnet = o["magnet"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val sid = o["sid"]?.jsonPrimitive?.intOrNull ?: 0
            if (sid <= 0) return@mapNotNull null
            SearchItem(
                title = o["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                relased = o["relased"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                quality = o["quality"]?.jsonPrimitive?.intOrNull ?: 0,
                videotype = o["videotype"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                size = o["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                sizeName = o["sizeName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                sid = sid,
                pir = o["pir"]?.jsonPrimitive?.intOrNull ?: 0,
                magnet = magnet
            )
        }.sortedByDescending { it.sid }
    }
}
