package com.garickbox.jecwebgo.service
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.garickbox.jecwebgo.data.network.HttpClient
import com.garickbox.jecwebgo.torrent.TorrServerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class TorrServerService : Service() {
    companion object {
        private const val CH = "jecweb"
        private const val ID = 8809
        private const val TAG = "TorrServer"
        fun start(ctx: Context) {
            val i = Intent(ctx, TorrServerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CH, "TorrServer", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(ID, NotificationCompat.Builder(this, CH)
            .setContentTitle("JecWeb Go")
            .setContentText("TorrServer running")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true).build())
        TorrServerLauncher.start(this)
        val prefs = getSharedPreferences("torrsettings", android.content.Context.MODE_PRIVATE)
        val alreadyApplied = prefs.getBoolean("settings_v1_done", false)
        if (!alreadyApplied) {
            scope.launch {
                applySettings()
                prefs.edit().putBoolean("settings_v1_done", true).apply()
            }
        } else {
            android.util.Log.i(TAG, "settings already applied, skip (torrents preserved)")
        }
    }

    /** Retry POST /settings до 15 сек — пока TorrServer не поднимется */
    private suspend fun applySettings() {
        val cacheDir = filesDir.absolutePath + "/torrents_cache"
        val cachePath = "${filesDir.parentFile?.absolutePath}/files/torrents_cache"
        // dataDir user path: /data/user/0/com.garickbox.jecwebgo/files/torrents_cache
        val realPath = "/data/user/0/package/files/torrents_cache".replace("package", packageName)

        val body = JSONObject().apply {
            put("action", "set")
            put("sets", JSONObject().apply {
                put("CacheSize", 2147483648L)   // 2 GB RAM-кэш
                put("PreloadCache", 100)
                put("ReaderReadAHead", 95)
                put("UseDisk", true)
                put("TorrentsSavePath", realPath)
                put("RemoveCacheOnDrop", true)
                put("ConnectionsLimit", 50)
                put("TorrentDisconnectTimeout", 300)
            })
        }.toString()

        val url = "http://127.0.0.1:8090/settings"
        for (attempt in 1..10) {
            try {
                val resp = HttpClient.postJson(url, body)
                android.util.Log.i(TAG, "settings applied (attempt $attempt): ${resp.take(80)}")
                return
            } catch (e: Exception) {
                android.util.Log.w(TAG, "settings attempt $attempt fail: ${e.message}")
                delay(1500)
            }
        }
        android.util.Log.e(TAG, "settings FAILED after 10 attempts")
    }

    override fun onDestroy() {
        scope.cancel()
        TorrServerLauncher.stop()
        super.onDestroy()
    }
    override fun onBind(i: Intent?): IBinder? = null
}
