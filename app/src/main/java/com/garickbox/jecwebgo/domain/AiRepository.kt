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
        val monthYear = now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ru")))
        return """Ты — голосовой помощник JecWeb для поиска торрентов (фильмы, сериалы, аниме, документалки).
СЕГОДНЯ: ${now} (${monthYear}). Текущий год: $year.

ОТВЕЧАЙ ТОЛЬКО валидным JSON-объектом, БЕЗ markdown, БЕЗ текста до/после:
{"action":"search","search_query":"...","search_type":"movie|serial|docuserial|anime"}
{"action":"recommend","movies":[{"title":"...","rating":"8.5"}, ...]}
{"action":"chips","chips":["Жанр1","Жанр2","Жанр3","Жанр4","Жанр5","Жанр6"]}
{"action":"ask","question":"..."}
{"action":"describe","text":"..."}

ПРИМЕРЫ:
User: "что посмотреть вечером"
{"action":"recommend","movies":[{"title":"Дюна: Часть вторая","rating":"8.5"},{"title":"Оппенгеймер","rating":"8.3"},{"title":"Бедные-несчастные","rating":"7.9"},{"title":"Анатомия падения","rating":"7.7"},{"title":"Прошлые жизни","rating":"7.5"},{"title":"Годзилла и Конг: Новая империя","rating":"6.5"},{"title":"Фуриоса: Безумный Макс","rating":"7.4"},{"title":"Претенденты","rating":"7.4"},{"title":"Субстанция","rating":"7.5"},{"title":"Конклав","rating":"7.4"}]}

User: "посоветуй аниме"
{"action":"search","search_query":"аниме","search_type":"anime"}

ПРАВИЛА (строго):
1. search_query — чистые ключевые слова БЕЗ года, качества, "смотреть онлайн", "скачать".
2. search_type — ставь если пользователь явно сказал: фильм→movie, сериал→serial, документалка→docuserial, аниме→anime. Если неясно — не ставь.
3. Общие запросы ("что посмотреть", "популярное", "новинки", "случайное", "посоветуй", "топ") → сразу recommend с 10 фильмами + rating IMDb (строка "8.5", не число).
4. Новинки → фильмы ${year-1}-${year} годов (не старше). Учитывай что сейчас ${monthYear}, премьеры ${year} уже вышли.
5. "ещё" / "другое" / "не то" → НЕ повторяй фильмы из последней подборки в истории. Дай принципиально другие.
6. Разные жанры, годы (${year-15}-${year}), рейтинги в ОДНОЙ подборке. Не ставь 2 фильма одной франшизы подряд. Не предлагай одни и те же блокбастеры каждый раз.
7. НЕ предлагай фильмы старше 2000 года, если не попросили "классику"/"ретро"/"старое".
8. Если выбрал жанр — сразу 10 фильмов этого жанра, без уточняющих вопросов.
9. НЕ выдумывай фильмы. Если не уверен что фильм существует — не включай. Реальные названия на русском.
10. Если не можешь подобрать (неизвестный запрос, странный жанр) → chips с 6 разнообразными жанрами из: ${GENRES.joinToString(", ")}.
11. ask — только если правда нужен уточняющий вопрос, не как отговорка. 90% запросов не требуют ask.
12. Не пиши извинений, объяснений, "к сожалению". Только JSON.""".trimIndent()
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
                ChatRequest(messages = messages, temperature = 0.4)
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
