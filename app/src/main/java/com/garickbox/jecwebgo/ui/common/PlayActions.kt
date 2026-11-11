package com.garickbox.jecwebgo.ui.common

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.garickbox.jecwebgo.data.store.PlayLogStore
import com.garickbox.jecwebgo.data.store.PlayLogEntry
import com.garickbox.jecwebgo.domain.TorrentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PlayActions {
    fun playPhone(ctx: Context, scope: CoroutineScope, magnet: String, title: String) {
        scope.launch {
            val h = TorrentRepository.add(magnet)
            android.util.Log.d("JecWeb", "play_phone hash=$h title=$title")
            if (h == null) {
                Toast.makeText(ctx, "TorrServer недоступен", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(ctx, "Загрузка торрента…", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch { TorrentRepository.preload(h) }
            PlayLogStore.add(ctx, PlayLogEntry("phone", title, magnet, h, System.currentTimeMillis()/1000))
            val ready = TorrentRepository.waitReady(h, 15000L)
            android.util.Log.d("JecWeb", "play_phone ready=$ready")
            if (!ready) {
                Toast.makeText(ctx, "Торрент не готов за 15с", Toast.LENGTH_LONG).show()
                return@launch
            }
            val url = TorrentRepository.streamM3U(h)
            android.util.Log.d("JecWeb", "play_phone url=$url")
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
            val h = TorrentRepository.add(magnet)
            if (h == null) {
                Toast.makeText(ctx, "TorrServer недоступен", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("m3u", TorrentRepository.streamM3U(h)))
            Toast.makeText(ctx, "M3U скопирован для ТВ", Toast.LENGTH_SHORT).show()
            PlayLogStore.add(ctx, PlayLogEntry("tv", title, magnet, h, System.currentTimeMillis()/1000))
        }
    }
}
