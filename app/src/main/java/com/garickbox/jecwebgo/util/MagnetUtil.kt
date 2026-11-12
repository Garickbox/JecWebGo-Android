package com.garickbox.jecwebgo.util

object MagnetUtil {
    private val RE = Regex("""btih:([a-fA-F0-9]{40})""")

    private val TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker.gmi.gd:6969/announce",
        "udp://tracker.auctor.tv:6969/announce",
        "udp://tracker.1h.is:1337/announce",
        "udp://tracker-udp.gbitt.info:80/announce",
        "udp://evan.im:6969/announce",
        "udp://leet-tracker.moe:1337/announce",
        "udp://ipv4announce.sktorrent.eu:6969/announce",
        "udp://open.demonoid.ch:6969/announce",
        "udp://tracker.qu.ax:6969/announce",
        "udp://tracker.srv00.com:6969/announce",
        "udp://tracker.fnix.net:6969/announce",
        "udp://tracker.ducks.party:1984/announce",
        "udp://uabits.today:6990/announce",
        "udp://torrentclub.online:54123/announce",
        "udp://t.overflow.biz:6969/announce",
        "udp://tracker.ddunlimited.net:6969/announce",
        "udp://tracker.corpscorp.online:80/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://martin-gebhardt.eu:25/announce",
        "udp://tracker.plx.im:6969/announce",
        "udp://tracker.opentorrent.top:6969/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.internetwarriors.net:1337/announce",
        "udp://opentor.org:2710/announce"
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
                out += if (out.contains('?')) "&tr=$t" else "?tr=$t"
            }
        }
        return out
    }
}
