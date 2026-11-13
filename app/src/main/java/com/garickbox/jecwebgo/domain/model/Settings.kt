package com.garickbox.jecwebgo.domain.model
import kotlinx.serialization.Serializable
@Serializable data class Settings(
    val search_provider: String = "jacred",
    val enable_play_log: Boolean = true, val enable_bookmarks: Boolean = true,
    val tv_ip: String = "192.168.1.128",
    val phone_action: String = "web_player", val tv_action: String = "m3u",
    val superblitz_first_mb: Int = 20, val superblitz_first_threads: Int = 20,
    val preload_mb: Int = 50, val preload_threads: Int = 6,
    val superblitz_aggressiveness: Int = 3, val auto_speed_detect: Boolean = false,
    val theme: String = "dark", val history_limit: Int = 50, val play_log_limit: Int = 100,
    val show_torrent_panel: Boolean = true, val voice_search: Boolean = false,
    val voice_assistant: Boolean = true, val password: String = "",
    val ai_api_key: String = "",
    val hw_mode: String = "full"
)
