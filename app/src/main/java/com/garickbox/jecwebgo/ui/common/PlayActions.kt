package com.garickbox.jecwebgo.ui.common

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.garickbox.jecwebgo.data.store.PlayLogStore
import com.garickbox.jecwebgo.data.store.PlayLogEntry
import com.garickbox.jecwebgo.data.store.SettingsStore
import com.garickbox.jecwebgo.domain.TorrentRepository
import com.garickbox.jecwebgo.domain.SuperblitzParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PlayActions {
    private fun paramsFor(ctx: Context): SuperblitzParams {
        val s = SettingsStore.load(ctx)
        return if (s.auto_speed_detect) {
            TorrentRepository.paramsForLevel(s.superblitz_aggressiveness)
        } else {
            SuperblitzParams(
                preloadMB = s.preload_mb.coerceAtLeast(10),
                threads = s.preload_threads.coerceIn(1, 20)
            )
        }
    }

    fun playPhone(ctx: Context, scope: CoroutineScope, magnet: String, title: String) {
        scope.launch {
            val h = TorrentRepository.add(magnet)
            android.util.Log.d("JecWeb", "play_phone hash=$h title=$title")
            if (h == null) {
                Toast.makeText(ctx, "TorrServer недоступен", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val p = paramsFor(ctx)
            Toast.makeText(ctx, "Загрузка торрента… (${p.preloadMB}MB × ${p.threads})", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch { TorrentRepository.preload(h, p.preloadMB, p.threads) }
            PlayLogStore.add(ctx, PlayLogEntry("phone", title, magnet, h, System.currentTimeMillis()/1000))
            val ready = TorrentRepository.waitReady(h, 15000L)
            if (!ready) {
                Toast.makeText(ctx, "Торрент не готов за 15с", Toast.LENGTH_LONG).show()
                return@launch
            }
            val url = TorrentRepository.streamM3U(h)
            val vlc = Intent(Intent.ACTION_VIEW).apply {
                setPackage("org.videolan.vlc")
                setDataAndType(Uri.parse(url), "video/x-mpegurl")
            }
            val generic = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setType("video/*")
            val ok = runCatching { ctx.startActivity(vlc) }.isSuccess ||
                     runCatching { ctx.startActivity(generic) }.isSuccess
            if (!ok) Toast.makeText(ctx, "Нет видеоплеера", Toast.LENGTH_SHORT).show()
        }
    }

    fun playHash(ctx: Context, scope: CoroutineScope, hash: String, title: String) {
        scope.launch {
            val p = paramsFor(ctx)
            Toast.makeText(ctx, "Загрузка торрента… (${p.preloadMB}MB × ${p.threads})", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch { TorrentRepository.preload(hash, p.preloadMB, p.threads) }
            PlayLogStore.add(ctx, PlayLogEntry("phone", title, "", hash, System.currentTimeMillis()/1000))
            val ready = TorrentRepository.waitReady(hash, 15000L)
            if (!ready) {
                Toast.makeText(ctx, "Торрент не готов за 15с", Toast.LENGTH_LONG).show()
                return@launch
            }
            val url = TorrentRepository.streamM3U(hash)
            val vlc = Intent(Intent.ACTION_VIEW).apply {
                setPackage("org.videolan.vlc")
                setDataAndType(Uri.parse(url), "video/x-mpegurl")
            }
            val generic = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setType("video/*")
            val ok = runCatching { ctx.startActivity(vlc) }.isSuccess ||
                     runCatching { ctx.startActivity(generic) }.isSuccess
            if (!ok) Toast.makeText(ctx, "Нет видеоплеера", Toast.LENGTH_SHORT).show()
        }
    }

    fun playTV(ctx: Context, scope: CoroutineScope, magnet: String, title: String) {
        scope.launch {
            val s = SettingsStore.load(ctx)
            val tvIp = s.tv_ip.trim()
            if (tvIp.isBlank()) {
                Toast.makeText(ctx, "TV IP не задан (Настройки)", Toast.LENGTH_LONG).show()
                return@launch
            }
            if (!TorrentRepository.isAliveAt(tvIp)) {
                Toast.makeText(ctx, "TorrServer на $tvIp:8090 недоступен", Toast.LENGTH_LONG).show()
                return@launch
            }
            val h = TorrentRepository.add(magnet)
            if (h == null) {
                Toast.makeText(ctx, "TorrServer недоступен", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val m3u = TorrentRepository.streamM3U(h, tvIp)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("m3u", m3u))
            Toast.makeText(ctx, "M3U для ТВ: $tvIp", Toast.LENGTH_LONG).show()
            PlayLogStore.add(ctx, PlayLogEntry("tv", title, magnet, h, System.currentTimeMillis()/1000))
        }
    }
}
