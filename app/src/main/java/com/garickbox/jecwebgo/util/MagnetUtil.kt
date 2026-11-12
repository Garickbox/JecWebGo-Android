package com.garickbox.jecwebgo.util

object MagnetUtil {
    private val RE = Regex("""btih:([a-fA-F0-9]{40})""")

    private val TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://exodus.desync.com:6969/announce",
        "http://tracker.qu.ax:6969/announce",
        "udp://tracker.ducks.party:1984/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://tracker.tryhackx.org:6969/announce",
        "udp://tracker.corpscorp.online:80/announce",
        "udp://tracker.nyaa.vc:6969/announce",
        "udp://tracker-udp.gbitt.info:80/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.gmi.gd:6969/announce",
        "http://tracker.dler.org:6969/announce",
        "udp://tracker2.dler.org:80/announce",
        "http://tracker.dler.com:6969/announce",
        "http://tracker.renfei.net:8080/announce",
        "udp://tracker.peerfect.org:6969/announce",
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
