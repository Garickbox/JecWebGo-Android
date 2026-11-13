package com.garickbox.jecwebgo.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerRequest(
    val url: String = "",
    val title: String = "",
    val hash: String = "",
    val currentIndex: Int = 1,
    val magnet: String = "",
    val poster: String = "",
    val loading: Boolean = false,
    val status: String = "",
    val error: String? = null
)

object PendingPlayer {
    private val _data = MutableStateFlow<PlayerRequest?>(null)
    val data: StateFlow<PlayerRequest?> = _data.asStateFlow()

    fun openLoading(title: String, poster: String) {
        _data.value = PlayerRequest(title = title, poster = poster, loading = true, status = "Подключение...")
    }
    fun updateStatus(msg: String) {
        _data.value = _data.value?.copy(status = msg)
    }
    fun openReady(url: String, title: String, hash: String, currentIndex: Int) {
        _data.value = PlayerRequest(url = url, title = title, hash = hash, currentIndex = currentIndex, loading = false)
    }
    fun error(msg: String) {
        _data.value = _data.value?.copy(loading = false, error = msg)
    }
    fun consume() { _data.value = null }
}
