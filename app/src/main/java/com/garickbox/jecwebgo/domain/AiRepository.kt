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
import java.util.concurrent.TimeUnit

object AiRepository {
    // Cloudflare Worker proxy; ключ DeepSeek хранится в Secret воркера, в APK его нет
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
        "Биографии", "Нуар", "Посткатастрофа"
    )

    fun randomGenres(n: Int): List<String> = GENRES.shuffled().take(n)

    private fun systemPrompt(): String {
        val year = 2025
        return """Ты — голосовой помощник JecWeb для поиска торрентов.

Отвечай ТОЛЬКО JSON-объектом:
{"action":"search","search_query":"...","search_type":"movie/serial/..."}
{"action":"recommend","movies":[{"title":"...","rating":"IMDb"}, ...]}
{"action":"chips","chips":["...","..."]}
{"action":"ask","question":"..."}
{"action":"describe","text":"..."}

ПРАВИЛА:
- search_query: чистые ключевые слова без года/качества.
- search_type: если известен (movie/serial/docuserial/anime).
- Если запрос общий (что посмотреть, популярное, новинки, случайное) — сразу recommend с 10 реальными фильмами, каждый с рейтингом IMDb (строка, "8.5").
- Для новинок — фильмы последних 1-2 лет (2024-2025).
- "ещё" — не повторяй уже названные фильмы.
- Если не можешь подобрать — chips с 6 разнообразными жанрами.
- Никогда не используй ask без крайней необходимости.
- Названия реальные, не выдумывай. На русском языке.
- Если пользователь не попросил "классику"/"ретро" — не предлагай раньше 2000 года.
- Разные жанры, годы, рейтинги в одной подборке.
- Если пользователь выбрал жанр — сразу 10 фильмов жанра, без уточнений.""".trimIndent()
    }

    suspend fun chat(userText: String, history: List<ChatMessage>): AiAction = withContext(Dispatchers.IO) {
        try {
            val messages = mutableListOf(ChatMessage("system", systemPrompt()))
            messages.addAll(history.takeLast(8))
            messages.add(ChatMessage("user", userText))
            val body = json.encodeToString(ChatRequest.serializer(), ChatRequest(messages = messages))
            val req = Request.Builder().url(BASE)
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
            action
        } catch (e: Exception) {
            AiAction(action = "chips", chips = randomGenres(6))
        }
    }

    suspend fun describe(title: String): String = withContext(Dispatchers.IO) {
        try {
            val messages = listOf(
                ChatMessage("system", "Ты — краткий киносправочник. Отвечай СТРОГО 1-2 предложениями, на русском. Укажи русское название и один интересный факт. Если не знаешь фильм, скажи: \"Информация не найдена.\""),
                ChatMessage("user", "Расскажи о фильме или сериале «$title».")
            )
            val body = json.encodeToString(ChatRequest.serializer(), ChatRequest(messages = messages, max_tokens = 300))
            val req = Request.Builder().url(BASE)
                .post(body.toRequestBody("application/json".toMediaType())).build()
            val text = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            val resp = json.decodeFromString(ChatResponse.serializer(), text)
            resp.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifBlank { "Информация не найдена." }
        } catch (e: Exception) { "Информация недоступна." }
    }
}
