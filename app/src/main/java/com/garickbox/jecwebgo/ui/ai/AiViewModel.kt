package com.garickbox.jecwebgo.ui.ai

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

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isEmpty()) return
        _state.value = _state.value.copy(
            messages = _state.value.messages + UiMessage("user", text),
            loading = true
        )
        viewModelScope.launch {
            val action = AiRepository.chat(text, _state.value.history)
            when (action.action) {
                "recommend" -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        movies = action.movies,
                        chips = emptyList(),
                        messages = _state.value.messages + UiMessage("assistant", "Вот что может понравиться:"),
                        history = _state.value.history + ChatMessage("user", text)
                    )
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

    fun quickAction(kind: String) {
        val query = when (kind) {
            "random" -> "предложи 10 случайных фильмов с высоким рейтингом"
            "popular" -> "популярные фильмы прямо сейчас"
            "new" -> "новые фильмы 2025 года"
            "genre" -> "предложи 10 фильмов из разных жанров"
            else -> kind
        }
        send(query)
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
