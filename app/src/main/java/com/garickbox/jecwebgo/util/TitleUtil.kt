package com.garickbox.jecwebgo.util

object TitleUtil {
    fun cleanTitle(raw: String): String {
        var t = raw.trim()
        if (t.contains(" / ")) t = t.substringBefore(" / ").trim()
        val p = t.indexOf(" (")
        if (p > 0) t = t.substring(0, p).trim()
        val b = t.indexOf(" [")
        if (b > 0) t = t.substring(0, b).trim()
        return t.ifBlank { raw }
    }

    fun mediaType(category: String): String = when {
        category.contains("сериал", true) -> "serial"
        category.contains("Аниме", true) -> "anime"
        category.contains("Мультипликация", true) -> "cartoon"
        category.contains("Научно", true) || category.contains("Телевизор", true) -> "docu"
        else -> "movie"
    }
}
