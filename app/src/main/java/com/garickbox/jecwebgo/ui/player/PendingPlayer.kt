package com.garickbox.jecwebgo.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerRequest(
    val url: String = "",
    val title: String = "",
    val description: String = "",
    val hash: String = "",
    val currentIndex: Int = 1,
    val magnet: String = "",
    val poster: String = "",
    val loading: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val fileName: String = "",
    val localKey: String = "",
    val loadingNonce: Long = 0L,
    val isPlaying: Boolean = false
)

object PendingPlayer {
    private val _data = MutableStateFlow<PlayerRequest?>(null)
    val data: StateFlow<PlayerRequest?> = _data.asStateFlow()

    private val _loadingNonce = java.util.concurrent.atomic.AtomicLong(0L)
    fun openLoading(title: String, poster: String, description: String = "") {
        _data.value = PlayerRequest(
            title = title, poster = poster, description = description,
            loading = true, status = "Подключение...",
            loadingNonce = _loadingNonce.incrementAndGet()
        )
    }
    fun updateStatus(msg: String) {
        _data.update { it?.copy(status = msg) }
    }
    fun updatePoster(url: String) {
        _data.update { it?.copy(poster = url) }
    }
    fun updateDescription(text: String) {
        if (text.isBlank()) return
        _data.update { it?.copy(description = text) }
    }
    fun openReady(url: String, title: String, hash: String, currentIndex: Int, fileName: String = "", localKey: String = "") {
        _data.value = _data.value?.copy(
            url = url, title = title, hash = hash,
            currentIndex = currentIndex, fileName = fileName, localKey = localKey,
            loading = false, error = null, isPlaying = false
        ) ?: PlayerRequest(url = url, title = title, hash = hash, currentIndex = currentIndex, fileName = fileName, localKey = localKey, isPlaying = false)
    }
    fun error(msg: String) {
        _data.update { it?.copy(loading = false, error = msg, isPlaying = false) }
    }
    fun setPlaying(playing: Boolean) {
        _data.update { if (it?.isPlaying != playing) it.copy(isPlaying = playing) else it }
    }
    fun consume() { _data.value = null }
}
