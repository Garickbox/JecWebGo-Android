package com.garickbox.jecwebgo.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerRequest(
    val url: String,
    val title: String,
    val hash: String = "",
    val currentIndex: Int = 1,
    val magnet: String = ""
)

object PendingPlayer {
    private val _data = MutableStateFlow<PlayerRequest?>(null)
    val data: StateFlow<PlayerRequest?> = _data.asStateFlow()
    fun open(url: String, title: String, hash: String = "", currentIndex: Int = 1, magnet: String = "") {
        _data.value = PlayerRequest(url, title, hash, currentIndex, magnet)
    }
    fun consume() { _data.value = null }
}
