package com.garickbox.jecwebgo.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.garickbox.jecwebgo.data.store.PlayLogStore
import com.garickbox.jecwebgo.data.store.PlayLogEntry
import com.garickbox.jecwebgo.domain.TorrentFile
import com.garickbox.jecwebgo.domain.TorrentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object PlayActions {
    private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "flv", "m4v", "ts", "m2ts")

    fun playPhone(ctx: Context, scope: CoroutineScope, magnet: String, title: String, poster: String = "", description: String = "") {
        scope.launch {
            com.garickbox.jecwebgo.ui.player.PendingPlayer.openLoading(title, poster, description)
            com.garickbox.jecwebgo.ui.player.PendingPlayer.updateStatus("Подключение к TorrServer...")
            val h = TorrentRepository.add(magnet)
            android.util.Log.d("JecWeb", "play_phone hash=$h title=$title")
            if (h == null) {
                com.garickbox.jecwebgo.ui.player.PendingPlayer.error("TorrServer недоступен")
                return@launch
            }
            com.garickbox.jecwebgo.ui.player.PendingPlayer.updateStatus("Получаем метаданные торрента...")
            val video = findVideoFile(h)
            if (video == null) {
                com.garickbox.jecwebgo.ui.player.PendingPlayer.error("Видеофайл не найден в торренте")
                return@launch
            }
            com.garickbox.jecwebgo.ui.player.PendingPlayer.updateStatus("Получаем пиров...")
            val ready = TorrentRepository.waitReadyReal(h, video.id, 90000L) { status ->
                com.garickbox.jecwebgo.ui.player.PendingPlayer.updateStatus(status)
            }
            if (!ready) {
                com.garickbox.jecwebgo.ui.player.PendingPlayer.error("Нет пиров (или мало сидов). Попробуйте другой фильм или скачайте")
                return@launch
            }
            openVlc(ctx, h, video.id, title, magnet)
        }
    }

    fun playHash(ctx: Context, scope: CoroutineScope, hash: String, title: String) {
        scope.launch {
            Toast.makeText(ctx, "Загрузка торрента…", Toast.LENGTH_SHORT).show()
            val video = findVideoFile(hash)
            if (video == null) {
                Toast.makeText(ctx, "Видеофайл не найден", Toast.LENGTH_LONG).show()
                return@launch
            }
            val ready = TorrentRepository.waitReady(hash, video.id, 30000L)
            if (!ready) {
                Toast.makeText(ctx, "Торрент не готов за 30с", Toast.LENGTH_LONG).show()
                return@launch
            }
            openVlc(ctx, hash, video.id, title, "")
        }
    }

    /**
     * Ищет первый видеофайл в торренте (не .srt/.sub/.ass/.nfo/.txt).
     * Ждёт до 20 сек пока TorrServer получит file_stats.
     */
    private suspend fun findVideoFile(hash: String): TorrentFile? {
        val deadline = System.currentTimeMillis() + 20000L
        while (System.currentTimeMillis() < deadline) {
            val files = TorrentRepository.getFiles(hash)
            if (files.isNotEmpty()) {
                val video = files.firstOrNull { f ->
                    val ext = f.path.substringAfterLast(".", "").lowercase()
                    ext in VIDEO_EXT
                }
                if (video != null) return video
                // fallback: самый большой файл (обычно это видео)
                return files.maxByOrNull { it.length }
            }
            kotlinx.coroutines.delay(1000)
        }
        return null
    }

    private suspend fun openVlc(ctx: Context, hash: String, index: Int, title: String, magnet: String) {
        PlayLogStore.add(ctx, PlayLogEntry("phone", title, magnet, hash, System.currentTimeMillis()/1000))
        val base = "http://127.0.0.1:8090/stream?link=$hash&index=$index&play"
        val url = "$base&t=${System.currentTimeMillis()}"
        val cleanT = com.garickbox.jecwebgo.util.TitleUtil.cleanTitle(title)
        com.garickbox.jecwebgo.ui.player.PendingPlayer.openReady(url, cleanT, hash, index)
    }
}
