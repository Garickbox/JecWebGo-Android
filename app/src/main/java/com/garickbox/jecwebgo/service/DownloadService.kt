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
    @Volatile private var running = false
    private var idleTicks = 0

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "Загрузки", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setShowBadge(true)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
        startForeground(NOTIF_ID, buildNotif("JecWeb", "Ожидание...", 0L, 0L))
        scope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        DownloadRepository.cancelled = true
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
        DownloadRepository.cancelled = false
        android.util.Log.d("JecWeb", "download start: ${item.request.fileName} idx=${item.request.fileIndex}")

        // 1. Ждём готовности стрима (правильный index)
        val ready = TorrentRepository.waitReady(item.request.hash, item.request.fileIndex, 30000L)
        android.util.Log.d("JecWeb", "download waitReady=${ready}")
        if (!ready) { DownloadRepository.fail(item, "torrent not ready"); return }

        // 1b. Прогрев TorrServer (50MB × 6 потоков, без &play)
        //     ускоряет sequential download — TorrServer качает с пиров параллельно
        CoroutineScope(Dispatchers.IO).launch {
            TorrentRepository.preload(item.request.hash, 50, 6)
            android.util.Log.d("JecWeb", "preload done: 50MB")
        }

        // 2. Размер: из request (getFiles) или fallback на HEAD
        var totalBytes = item.request.totalBytes
        if (totalBytes <= 0) {
            totalBytes = getFileSize(item.request.hash, item.request.fileIndex)
            android.util.Log.d("JecWeb", "download size fallback HEAD=$totalBytes")
        }
        if (totalBytes <= 0) { DownloadRepository.fail(item, "size 0"); return }
        DownloadRepository.setTotal(totalBytes)

        // 3. Файл: resume существующего или создать новый
        val targetUri: android.net.Uri
        val startOffset: Long
        val existingStr = item.existingUri?.takeIf { it.isNotBlank() }
        if (existingStr != null) {
            val exUri = android.net.Uri.parse(existingStr)
            if (MediaStoreWriter.exists(this, exUri)) {
                val size = MediaStoreWriter.getFileSize(this, exUri)
                if (size in 1..(totalBytes - 1)) {
                    targetUri = exUri
                    startOffset = size
                    android.util.Log.d("JecWeb", "RESUME from $startOffset/$totalBytes")
                } else {
                    // файл битый или полный — удалить и создать заново
                    MediaStoreWriter.delete(this, exUri)
                    val safeName = sanitize(item.request.fileName)
                    val safeRel = sanitizeRelPath(item.request.relPath.substringBeforeLast("/", ""))
                    targetUri = MediaStoreWriter.createFile(this, safeName, safeRel) ?: run {
                        DownloadRepository.fail(item, "create fail"); return
                    }
                    startOffset = 0L
                }
            } else {
                val safeName = sanitize(item.request.fileName)
                val safeRel = sanitizeRelPath(item.request.relPath.substringBeforeLast("/", ""))
                targetUri = MediaStoreWriter.createFile(this, safeName, safeRel) ?: run {
                    DownloadRepository.fail(item, "create fail"); return
                }
                startOffset = 0L
            }
        } else {
            val safeName = sanitize(item.request.fileName)
            val safeRel = sanitizeRelPath(item.request.relPath.substringBeforeLast("/", ""))
            targetUri = MediaStoreWriter.createFile(this, safeName, safeRel) ?: run {
                DownloadRepository.fail(item, "create fail"); return
            }
            startOffset = 0L
        }

        // Сохранить uri активной загрузки (для resume после kill)
        DownloadRepository.setActiveUri(targetUri.toString())
        DownloadRepository.setTotal(totalBytes)

        // 4. Скачать
        updateNotif(item, startOffset, totalBytes)
        val ok = TorrentDownloader.download(
            ctx = this,
            hash = item.request.hash,
            fileIndex = item.request.fileIndex,
            targetUri = targetUri,
            totalBytes = totalBytes,
            startOffset = startOffset,
            onProgress = { dl, total, sp ->
                DownloadRepository.updateProgress(dl, total, sp)
                updateNotif(item, dl, total)
            },
            isCancelled = { DownloadRepository.cancelled },
            isPaused = { DownloadRepository.paused }
        )
        if (ok) {
            DownloadRepository.complete(this, item, targetUri.toString(), totalBytes)
            runCatching { TorrentRepository.rem(item.request.hash) }
            updateNotif(item, totalBytes, totalBytes)
            android.util.Log.d("JecWeb", "download complete: ${item.request.fileName}")
        } else {
            DownloadRepository.fail(item, "cancelled/failed")
        }
    }

    private fun sanitizeRelPath(relPath: String): String {
        if (relPath.isBlank()) return ""
        return relPath.split("/")
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/") { seg ->
                seg.replace(Regex("""[\\:*?"<>|]"""), "_").take(120)
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

    private fun openAppIntent(): android.app.PendingIntent {
        val i = Intent(this, com.garickbox.jecwebgo.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "downloads")
        }
        return android.app.PendingIntent.getActivity(
            this, 0, i,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )
    }

    private fun buildNotif(title: String, text: String, progress: Long, total: Long): Notification {
        val b = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (total > 0) b.setProgress(100, ((progress * 100) / total).toInt(), false)
        return b.build()
    }

    private fun updateNotif(item: DownloadItem, progress: Long, total: Long) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) {
                android.util.Log.w("JecWeb", "notifications disabled by user - download runs silent")
                return
            }
            val pct = if (total > 0) (progress * 100 / total).toInt() else 0
            val mbDone = progress / 1048576
            val mbTotal = total / 1048576
            val spd = if (item.speedBps > 0) " · %.1f MB/s".format(item.speedBps / 1e6) else ""
            val title = item.request.title.ifBlank { "JecWeb загрузка" }
            val text = "$pct% · $mbDone / $mbTotal MB$spd"
            nm.notify(NOTIF_ID, buildNotif(title, text, progress, total))
        } catch (e: Exception) {
            android.util.Log.e("JecWeb", "updateNotif fail: ${e.message}")
        }
    }
}
