package com.garickbox.jecwebgo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.garickbox.jecwebgo.data.download.DownloadItem
import com.garickbox.jecwebgo.data.download.DownloadRepository
import com.garickbox.jecwebgo.data.download.MediaStoreWriter
import com.garickbox.jecwebgo.data.download.TorrentDownloader
import com.garickbox.jecwebgo.data.network.HttpClient
import com.garickbox.jecwebgo.domain.TorrentRepository
import kotlinx.coroutines.*
import okhttp3.Request

class DownloadService : Service() {
    companion object {
        private const val CH = "jecweb_dl"
        private const val NOTIF_ID = 8810
        fun start(ctx: Context) {
            val i = Intent(ctx, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var cancelled = false
    @Volatile private var running = false
    private var idleTicks = 0

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "Загрузки", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(NOTIF_ID, buildNotif("Ожидание...", 0L, 0L))
        scope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        cancelled = true
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            if (running) { delay(500); continue }
            val item = DownloadRepository.next()
            if (item == null) {
                idleTicks++
                if (idleTicks > 60) { stopSelf(); return }
                delay(1000)
                continue
            }
            idleTicks = 0
            running = true
            try { process(item) } finally { running = false }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun process(item: DownloadItem) {
        cancelled = false
        val safeName = sanitize(item.request.fileName)
        val uri = MediaStoreWriter.createFile(this, safeName)
        if (uri == null) { DownloadRepository.fail(item, "create fail"); return }
        val totalBytes = getFileSize(item.request.hash, item.request.fileIndex)
        if (totalBytes <= 0) {
            MediaStoreWriter.delete(this, uri)
            DownloadRepository.fail(item, "size 0")
            return
        }
        updateNotif(item, 0L, totalBytes)
        val ok = TorrentDownloader.download(
            ctx = this,
            hash = item.request.hash,
            fileIndex = item.request.fileIndex,
            targetUri = uri,
            totalBytes = totalBytes,
            onProgress = { dl, sp ->
                DownloadRepository.updateProgress(dl, sp)
                updateNotif(item, dl, totalBytes)
            },
            isCancelled = { cancelled }
        )
        if (ok) {
            DownloadRepository.complete(item, uri.toString(), totalBytes)
            runCatching { TorrentRepository.rem(item.request.hash) }
            updateNotif(item, totalBytes, totalBytes)
        } else {
            DownloadRepository.fail(item, "cancelled/failed")
        }
    }

    private fun getFileSize(hash: String, index: Int): Long {
        return try {
            val url = "http://127.0.0.1:8090/stream?link=$hash&index=$index&play"
            val req = Request.Builder().url(url).head().build()
            HttpClient.client.newCall(req).execute().use {
                it.header("Content-Length")?.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) { 0L }
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").take(180)
    }

    private fun buildNotif(text: String, progress: Long, total: Long): Notification {
        val b = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("JecWeb загрузка")
            .setContentText(text)
            .setOngoing(true)
        if (total > 0) b.setProgress(100, ((progress * 100) / total).toInt(), false)
        return b.build()
    }

    private fun updateNotif(item: DownloadItem, progress: Long, total: Long) {
        val pct = if (total > 0) (progress * 100 / total).toInt() else 0
        val spd = if (item.speedBps > 0) " · %.1f MB/s".format(item.speedBps / 1e6) else ""
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotif("$pct%$spd", progress, total))
    }
}
