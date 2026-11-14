package com.garickbox.jecwebgo.util
object Helpers {
    fun sizeToBytes(valD: Double, unit: String): Long = when (unit.uppercase().trim()) {
        "GB" -> (valD * 1024 * 1024 * 1024).toLong()
        "MB" -> (valD * 1024 * 1024).toLong()
        "KB" -> (valD * 1024).toLong()
        else -> valD.toLong()
    }
    fun parseSize(s: String): Long {
        if (s.isBlank()) return 0L
        val parts = s.replace('\u00A0', ' ').trim().split(" ")
        if (parts.size < 2) return 0L
        val v = parts[0].toDoubleOrNull() ?: return 0L
        return sizeToBytes(v, parts[1])
    }
    fun coalesce(vararg v: String?): String = v.firstOrNull { !it.isNullOrBlank() } ?: ""

    fun formatSize(b: Long): String = when {
        b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
        b >= 1L shl 20 -> "%.0f MB".format(b / 1048576.0)
        b >= 1024 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }

    fun formatSpeed(bps: Long): String = when {
        bps >= 1048576 -> "%.1f MB/s".format(bps / 1048576.0)
        bps >= 1024 -> "%.0f KB/s".format(bps / 1024.0)
        else -> "$bps B/s"
    }
}
