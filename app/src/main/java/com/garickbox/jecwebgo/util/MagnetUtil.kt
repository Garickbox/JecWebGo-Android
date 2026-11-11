package com.garickbox.jecwebgo.util

object MagnetUtil {
    private val RE = Regex("""btih:([a-fA-F0-9]{40})""")
    fun hashOf(magnet: String): String {
        val m = RE.find(magnet) ?: return ""
        return m.groupValues[1].lowercase()
    }
}
