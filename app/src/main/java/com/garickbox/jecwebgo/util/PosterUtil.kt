package com.garickbox.jecwebgo.util

object PosterUtil {
    private const val BASE = "https://raw.githubusercontent.com/Garickbox/JecWebGo-Android/data/"
    fun posterUrl(poster: String): String {
        if (poster.isBlank()) return ""
        if (poster.startsWith("posters/")) return BASE + poster
        if (poster.startsWith("http")) return poster
        return ""
    }
}
