package com.garickbox.jecwebgo.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.garickbox.jecwebgo.data.store.PlayLogStore
import com.garickbox.jecwebgo.data.store.PlayLogEntry
import com.garickbox.jecwebgo.domain.TorrentRepository
import kotlinx.coroutines.CoroutineScope
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
            val ready = TorrentRepository.waitReady(h, 1, 30000L)
            if (!ready) {
                Toast.makeText(ctx, "Торрент не готов за 30с", Toast.LENGTH_LONG).show()
                return@launch
            }
            openVlc(ctx, h, title, magnet)
        }
    }

    fun playHash(ctx: Context, scope: CoroutineScope, hash: String, title: String) {
        scope.launch {
            Toast.makeText(ctx, "Загрузка торрента…", Toast.LENGTH_SHORT).show()
            val ready = TorrentRepository.waitReady(hash, 1, 30000L)
            if (!ready) {
                Toast.makeText(ctx, "Торрент не готов за 30с", Toast.LENGTH_LONG).show()
                return@launch
            }
            openVlc(ctx, hash, title, "")
        }
    }

    private suspend fun openVlc(ctx: Context, hash: String, title: String, magnet: String) {
        PlayLogStore.add(ctx, PlayLogEntry("phone", title, magnet, hash, System.currentTimeMillis()/1000))
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
