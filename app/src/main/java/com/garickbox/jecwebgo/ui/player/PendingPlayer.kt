package com.garickbox.jecwebgo.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerRequest(val url: String, val title: String)

object PendingPlayer {
    private val _data = MutableStateFlow<PlayerRequest?>(null)
    val data: StateFlow<PlayerRequest?> = _data.asStateFlow()
    fun open(url: String, title: String) { _data.value = PlayerRequest(url, title) }
    fun consume() { _data.value = null }
}
