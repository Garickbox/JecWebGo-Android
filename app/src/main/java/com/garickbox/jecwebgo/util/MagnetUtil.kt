package com.garickbox.jecwebgo.util

object MagnetUtil {
    private val RE = Regex("""btih:([a-fA-F0-9]{40})""")

    private val TRACKERS = listOf(
    )

    fun hashOf(magnet: String): String {
        val m = RE.find(magnet) ?: return ""
        return m.groupValues[1].lowercase()
    }

    fun enrich(magnet: String): String {
        if (magnet.isBlank()) return magnet
        var out = magnet
        for (t in TRACKERS) {
            if (!out.contains(t)) {
                out += if (out.contains('?')) "&tr=\$t" else "?tr=\$t"
            }
        }
        return out
    }
}
