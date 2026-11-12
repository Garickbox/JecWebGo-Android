package com.garickbox.jecwebgo.util

import java.security.MessageDigest

/**
 * Минимальный парсер .torrent (bencode) — извлекает info_hash (SHA1) и name.
 * .torrent → magnet:?xt=urn:btih:<hash>&dn=<name>
 */
object BencodeTorrent {
    data class Info(val infoHash: String, val name: String, val magnet: String)

    fun toMagnet(bytes: ByteArray): Info? {
        return try {
            val infoStart = findInfoStart(bytes) ?: return null
            val infoEnd = skipValue(bytes, infoStart) ?: return null
            val infoBytes = bytes.copyOfRange(infoStart, infoEnd)
            val hash = sha1Hex(infoBytes)
            val name = findName(bytes, infoStart, infoEnd) ?: "torrent"
            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
            Info(hash, name, "magnet:?xt=urn:btih:$hash&dn=$encodedName")
        } catch (e: Exception) { null }
    }

    private fun findInfoStart(b: ByteArray): Int? {
        val needle = "4:info".toByteArray()
        for (i in 0..b.size - needle.size) {
            var match = true
            for (j in needle.indices) {
                if (b[i + j] != needle[j]) { match = false; break }
            }
            if (match) return i + needle.size
        }
        return null
    }

    /** Возвращает позицию ПОСЛЕ значения, начиная с start. */
    private fun skipValue(b: ByteArray, start: Int): Int? {
        if (start >= b.size) return null
        return when (b[start].toInt().toChar()) {
            'i' -> {
                var i = start + 1
                while (i < b.size && b[i] != 'e'.code.toByte()) i++
                if (i >= b.size) null else i + 1
            }
            'l' -> {
                var i = start + 1
                while (i < b.size && b[i] != 'e'.code.toByte()) {
                    i = skipValue(b, i) ?: return null
                }
                if (i >= b.size) null else i + 1
            }
            'd' -> {
                var i = start + 1
                while (i < b.size && b[i] != 'e'.code.toByte()) {
                    i = skipValue(b, i) ?: return null
                    i = skipValue(b, i) ?: return null
                }
                if (i >= b.size) null else i + 1
            }
            else -> {
                // строка: <len>:<bytes>
                var i = start
                var len = 0
                while (i < b.size && b[i] != ':'.code.toByte()) {
                    val c = b[i].toInt().toChar()
                    if (!c.isDigit()) return null
                    len = len * 10 + (c - '0')
                    i++
                }
                if (i >= b.size) null else i + 1 + len
            }
        }
    }

    private fun findName(b: ByteArray, start: Int, end: Int): String? {
        val needle = "4:name".toByteArray()
        var i = start
        while (i < end - needle.size) {
            var match = true
            for (j in needle.indices) {
                if (b[i + j] != needle[j]) { match = false; break }
            }
            if (match) {
                var k = i + needle.size
                var len = 0
                while (k < end && b[k] != ':'.code.toByte()) {
                    val c = b[k].toInt().toChar()
                    if (!c.isDigit()) return null
                    len = len * 10 + (c - '0')
                    k++
                }
                return String(b, k + 1, minOf(len, end - k - 1))
            }
            i++
        }
        return null
    }

    private fun sha1Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }
}
