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
}
