package com.garickbox.jecwebgo.domain.model
import kotlinx.serialization.Serializable
@Serializable data class Torrent(
    val hash: String = "", val name: String = "", val title: String = "",
    val file_size: Long = 0L, val loaded_size: Long = 0L, val download_speed: Double = 0.0,
    val peers_active: Int = 0, val peers_total: Int = 0,
    val seeds: Int = 0, val stat: Int = 0
)
