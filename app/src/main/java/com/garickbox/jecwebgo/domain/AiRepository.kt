package com.garickbox.jecwebgo.domain

import com.garickbox.jecwebgo.domain.model.AiAction
import com.garickbox.jecwebgo.domain.model.ChatMessage
import com.garickbox.jecwebgo.domain.model.ChatRequest
import com.garickbox.jecwebgo.domain.model.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

object AiRepository {
    private const val BASE = "https://ai-jecweb.garickwot.workers.dev"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val GENRES = listOf(
        "Боевики", "Комедии", "Фантастика", "Ужасы", "Триллеры", "Драмы",
        "Мелодрамы", "Приключения", "Детективы", "Исторические", "Военные",
        "Спорт", "Документальные", "Семейные", "Фэнтези", "Мистика",
        "Криминал", "Вестерны", "Аниме", "Мультфильмы", "Музыкальные",
        "Биографии", "Нуар", "Постапокалипсис"
    )

    fun randomGenres(n: Int): List<String> = GENRES.shuffled().take(n)

    private fun systemPrompt(): String {
        val now = LocalDate.now()
        val year = now.year
        val monthYear = now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("ru")))
        return """Ты — голосовой помощник JecWeb для поиска торрентов (фильмы, сериалы, аниме, документалки).
СЕГОДНЯ: ${now} (${monthYear}). Текущий год: $year.

ОТВЕЧАЙ ТОЛЬКО валидным JSON-объектом, БЕЗ markdown, БЕЗ текста:

ДЛЯ ОБЩИХ ЗАПРОСОВ (что посмотреть, новинки, популярное, жанр) — ВЕРНИ ТОЛЬКО:
{"action":"tmdb_discover","year":2026,"genre":"comedy"}
{"action":"tmdb_discover","year":2026,"genre":"action"}
{"action":"tmdb_discover","year":2025,"genre":""}

НЕ ГЕНЕРИРУЙ НАЗВАНИЯ ФИЛЬМОВ. Клиент сам получит реальные фильмы из TMDB.

Для конкретного поиска пользователя:
{"action":"search","search_query":"...","search_type":"movie|serial|docuserial|anime"}

Для уточняющего вопроса (редко):
{"action":"ask","question":"..."}

ПРАВИЛА:
1. Общие запросы → tmdb_discover с жанром и годом. Жанры: comedy/action/horror/thriller/drama/fantasy/sci-fi/animation/documentary/family/romance/mystery/crime/war/western/history/music/adventure. Если жанр не важен — genre:"".
2. Новинки → year=${year} или ${year-1}.
3. Если пользователь явно назвал фильм/сериал ("найди Матрицу") → search с чистым названием.
4. search_query — БЕЗ года, качества, "смотреть", "скачать".
5. search_type — фильм→movie, сериал→serial, документалка→docuserial, аниме→anime. Если неясно — "".
6. "ещё"/"другое" → tmdb_discover с другим случайным жанром.
7. НЕ пиши извинений. Только JSON.""".trimIndent()
    }

    private fun describePrompt(): String {
        val year = LocalDate.now().year
        return """Ты — краткий киносправочник. Отвечай СТРОГО 1-2 предложениями на русском.
Формат: русское название + год + один интересный факт (режиссёр, актёр, награда, факт о съёмках).
Без воды, без "к сожалению", без "я не могу".
Если фильм/сериал не существует или ты не уверен — верни ровно: "Информация не найдена."
Не выдумывай факты. Сейчас $year год.""".trimIndent()
    }

    suspend fun chat(userText: String, history: List<ChatMessage>): AiAction = withContext(Dispatchers.IO) {
        try {
            val messages = mutableListOf(ChatMessage("system", systemPrompt()))
            messages.addAll(history.takeLast(8))
            messages.add(ChatMessage("user", userText))
            val body = json.encodeToString(
                ChatRequest.serializer(),
                ChatRequest(messages = messages, temperature = 0.6)
            )
            val req = Request.Builder().url("$BASE/ai")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            val text = client.newCall(req).execute().use { r ->
                val s = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}: ${s.take(200)}")
                s
            }
            val resp = json.decodeFromString(ChatResponse.serializer(), text)
            val content = resp.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            val clean = if (content.startsWith("```")) {
                content.removePrefix("```json").removePrefix("```")
                    .let { if (it.contains("```")) it.substringBeforeLast("```") else it }
                    .trim()
            } else content
            val start = clean.indexOf('{')
            val end = clean.lastIndexOf('}')
            val jsonStr = if (start >= 0 && end > start) clean.substring(start, end + 1) else clean
            val action = json.decodeFromString(AiAction.serializer(), jsonStr)
            when (action.action) {
                "recommend" -> if (action.movies.isEmpty()) AiAction("chips", chips = randomGenres(6)) else action
                "chips" -> action.copy(chips = if (action.chips.isEmpty()) randomGenres(6) else action.chips)
                "search" -> if (action.search_query.isBlank()) AiAction("chips", chips = randomGenres(6)) else action
                "ask", "describe" -> action
                else -> AiAction("chips", chips = randomGenres(6))
            }
        } catch (e: Exception) {
            AiAction(action = "chips", chips = randomGenres(6))
        }
    }

    /**
     * Прямой запрос в TMDB /discover/movie через CF Worker.
     * Возвращает РЕАЛЬНЫЕ фильмы (id, title, rating), без AI.
     */
    suspend fun discoverMovies(year: Int, page: Int, genreId: Int? = null): List<AiMovie> = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append(BASE).append("/tmdb/discover/movie?")
                append("primary_release_year=").append(year)
                append("&sort_by=popularity.desc")
                append("&vote_count.gte=10")
                append("&language=ru-RU")
                append("&page=").append(page.coerceIn(1, 500))
                if (genreId != null && genreId > 0) append("&with_genres=").append(genreId)
            }
            val req = Request.Builder().url(url).build()
            val text = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                r.body?.string().orEmpty()
            }
            val obj = json.parseToJsonElement(text).jsonObject
            val results = obj["results"]?.jsonArray ?: return@withContext emptyList()
            results.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val title = o["title"]?.jsonPrimitive?.contentOrNull
                    ?: o["original_title"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapNotNull null
                if (title.isBlank()) return@mapNotNull null
                val rating = o["vote_average"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                AiMovie(
                    title = title,
                    rating = if (rating > 0.0) "%.1f".format(rating) else "",
                    id = id
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "discoverMovies err: ${e.message}")
            emptyList()
        }
    }

    suspend fun describe(title: String): String = withContext(Dispatchers.IO) {
        try {
            val messages = listOf(
                ChatMessage("system", describePrompt()),
                ChatMessage("user", "Расскажи о фильме или сериале «$title».")
            )
            val body = json.encodeToString(
                ChatRequest.serializer(),
                ChatRequest(messages = messages, max_tokens = 300, temperature = 0.3)
            )
            val req = Request.Builder().url("$BASE/ai")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            val text = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            val resp = json.decodeFromString(ChatResponse.serializer(), text)
            val out = resp.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            if (out.isBlank() || out.length < 10) "Информация не найдена." else out
        } catch (e: Exception) { "Информация недоступна." }
    }
}
