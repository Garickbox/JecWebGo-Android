package com.garickbox.jecwebgo.ui.ai

import android.content.Context
import java.time.LocalDate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garickbox.jecwebgo.domain.AiRepository
import com.garickbox.jecwebgo.domain.model.AiMovie
import com.garickbox.jecwebgo.domain.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiMessage(val role: String, val text: String)

data class AiState(
    val messages: List<UiMessage> = emptyList(),
    val chips: List<String> = emptyList(),
    val movies: List<AiMovie> = emptyList(),
    val descTitle: String = "",
    val descText: String = "",
    val loading: Boolean = false,
    val history: List<ChatMessage> = emptyList()
)

class AiViewModel : ViewModel() {
    private val _state = MutableStateFlow(AiState())
    val state: StateFlow<AiState> = _state.asStateFlow()

    fun send(userText: String, ctx: Context? = null) {
        val text = userText.trim()
        if (text.isEmpty()) return
        _state.value = _state.value.copy(
            messages = _state.value.messages + UiMessage("user", text),
            loading = true
        )
        viewModelScope.launch {
            val action = AiRepository.chat(text, _state.value.history)
            when (action.action) {
                "tmdb_discover" -> {
                    _state.value = _state.value.copy(loading = false)
                    if (ctx != null) {
                        recommendFresh(ctx, action.year, tmdbGenreId(action.genre ?: ""))
                    }
                }
                "recommend" -> {
                    // AI вернул рекомендованные названия — но мы их игнорируем и запрашиваем TMDB
                    _state.value = _state.value.copy(loading = false)
                    if (ctx != null) {
                        recommendFresh(ctx, action.year, null)
                    } else {
                        _state.value = _state.value.copy(movies = action.movies)
                    }
                }
                "chips" -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        chips = action.chips.ifEmpty { AiRepository.randomGenres(6) },
                        messages = _state.value.messages + UiMessage("assistant", "Выбери жанр:"),
                        history = _state.value.history + ChatMessage("user", text)
                    )
                }
                "ask" -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        messages = _state.value.messages + UiMessage("assistant", action.question),
                        history = _state.value.history + ChatMessage("user", text) + ChatMessage("assistant", action.question)
                    )
                }
                "search" -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        messages = _state.value.messages + UiMessage("assistant", "Ищу: ${action.search_query}"),
                        history = _state.value.history + ChatMessage("user", text) + ChatMessage("assistant", action.search_query)
                    )
                }
                else -> _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun quickAction(kind: String, ctx: Context) {
        when (kind) {
            "random" -> recommendFresh(ctx, null, null)
            "popular" -> recommendFresh(ctx, LocalDate.now().year, null)
            "new" -> recommendFresh(ctx, LocalDate.now().year, null)
            "genre" -> recommendFresh(ctx, null, null)
            else -> send(kind, ctx)
        }
    }

    /**
     * Рекомендация из TMDB с dedup.
     * random page + исключить уже показанные → если пул исчерпан → clear seen.
     */
    fun recommendFresh(ctx: Context, year: Int?, genreId: Int?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, movies = emptyList(), chips = emptyList())
            try {
                val seen: Set<Int> = loadSeenIds(ctx)
                val accumulated: MutableList<AiMovie> = mutableListOf()
                var page: Int = (1..3).random()
                for (attempt: Int in 0 until 3) {
                    val currentYear: Int = LocalDate.now().year
                    val targetYear: Int = year ?: currentYear
                    val batch: List<AiMovie> = AiRepository.discoverMovies(targetYear, page, genreId)
                        .filter { movie: AiMovie ->
                            movie.id !in seen && accumulated.none { a: AiMovie -> a.id == movie.id }
                        }
                    accumulated.addAll(batch)
                    if (accumulated.size >= 10) break
                    page += 1
                }
                val final: List<AiMovie> = accumulated.take(10)
                if (final.isEmpty()) {
                    clearSeenIds(ctx)
                    val currentYear: Int = LocalDate.now().year
                    val targetYear: Int = year ?: currentYear
                    val fresh: List<AiMovie> = AiRepository.discoverMovies(targetYear, 1, genreId).take(10)
                    _state.value = _state.value.copy(loading = false, movies = fresh)
                    if (fresh.isNotEmpty()) {
                        val ids: List<Int> = fresh.map { movie: AiMovie -> movie.id }
                        saveSeenIds(ctx, ids)
                    }
                } else {
                    val ids: List<Int> = final.map { movie: AiMovie -> movie.id }
                    saveSeenIds(ctx, ids)
                    _state.value = _state.value.copy(loading = false, movies = final)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    private fun loadSeenIds(ctx: Context): Set<Int> {
        val prefs = ctx.getSharedPreferences("ai_seen", Context.MODE_PRIVATE)
        return (prefs.getStringSet("ids", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
    }

    private fun saveSeenIds(ctx: Context, ids: List<Int>) {
        val prefs = ctx.getSharedPreferences("ai_seen", Context.MODE_PRIVATE)
        val current = loadSeenIds(ctx)
        val merged = (current + ids).take(200)
        prefs.edit().putStringSet("ids", merged.map { it.toString() }.toSet()).apply()
    }

    private fun clearSeenIds(ctx: Context) {
        ctx.getSharedPreferences("ai_seen", Context.MODE_PRIVATE).edit().clear().apply()
    }

    companion object {
        fun tmdbGenreId(name: String): Int? = when (name.lowercase().trim()) {
            "comedy", "комедия", "комедии" -> 35
            "action", "боевик", "боевики" -> 28
            "horror", "ужасы" -> 27
            "thriller", "триллер", "триллеры" -> 53
            "drama", "драма", "драмы" -> 18
            "fantasy", "фэнтези" -> 14
            "sci-fi", "фантастика" -> 878
            "animation", "мультфильм", "мультфильмы", "аниме" -> 16
            "documentary", "документальный", "документальные" -> 99
            "family", "семейный", "семейные" -> 10751
            "romance", "мелодрама", "мелодрамы" -> 10749
            "mystery", "детектив", "детективы", "мистика" -> 9648
            "crime", "криминал" -> 80
            "war", "военный", "военные" -> 10752
            "western", "вестерн", "вестерны" -> 37
            "history", "исторический", "исторические" -> 36
            "music", "музыкальный", "музыкальные" -> 10402
            "adventure", "приключения" -> 12
            else -> null
        }
    }

    fun describe(title: String) {
        _state.value = _state.value.copy(descTitle = title, descText = "Загрузка...", loading = true)
        viewModelScope.launch {
            val text = AiRepository.describe(title)
            _state.value = _state.value.copy(descText = text, loading = false)
        }
    }

    fun clearDesc() {
        _state.value = _state.value.copy(descTitle = "", descText = "")
    }

    fun clear() { _state.value = AiState() }
}
