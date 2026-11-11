package com.garickbox.jecwebgo.domain.model
import kotlinx.serialization.Serializable
@Serializable data class SearchItem(
    val title: String = "", val relased: String = "", val quality: Int = 0,
    val videotype: String = "", val size: Long = 0L, val sizeName: String = "",
    val sid: Int = 0, val pir: Int = 0, val magnet: String = ""
)
